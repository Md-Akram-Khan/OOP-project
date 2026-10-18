package electricity.billing.system.ui.components;

import electricity.billing.system.common.AppException;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;
import javax.swing.Icon;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

/** Loading photos for upload and showing them. */
public final class Photos {

    /** The server reads meter photos at this size, so larger ones are shrunk before upload. */
    private static final int UPLOAD_SIZE = 1600;
    private static final long MAX_FILE_BYTES = 30L * 1024 * 1024;
    /** The largest photo the server accepts, in bytes and in pixels. */
    private static final int SERVER_MAX_BYTES = 8 * 1024 * 1024;
    private static final long SERVER_MAX_PIXELS = 40_000_000L;

    private Photos() {
    }

    /** A photo ready to upload: the original, or a high-quality JPEG no larger than the server reads. */
    public record Upload(BufferedImage image, byte[] bytes) {
    }

    /** A meter photo, shrunk to the size the server reads and saved as a high-quality JPEG (photos are kept with bills). */
    public static Upload load(File file) {
        BufferedImage image = read(file);
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest > UPLOAD_SIZE) {
            double scale = (double) UPLOAD_SIZE / longest;
            image = scaled(image, (int) Math.round(image.getWidth() * scale), (int) Math.round(image.getHeight() * scale));
        } else {
            image = scaled(image, image.getWidth(), image.getHeight());
        }
        return new Upload(image, jpeg(image, 0.92f));
    }

    /**
     * A photo to upload exactly as it is, byte for byte, so nothing is lost
     * (NID cards). Only a photo too large for the server is re-saved: at full
     * size as a near-lossless JPEG, and shrunk only if even that is too large.
     */
    public static Upload loadOriginal(File file) {
        BufferedImage image = read(file);
        byte[] original;
        try {
            original = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new AppException("Couldn't open that photo: " + e.getMessage());
        }
        long pixels = (long) image.getWidth() * image.getHeight();
        if (original.length <= SERVER_MAX_BYTES && pixels <= SERVER_MAX_PIXELS) {
            return new Upload(image, original);
        }
        BufferedImage fitted = rgb(image);
        if (pixels > SERVER_MAX_PIXELS) {
            double scale = Math.sqrt((double) SERVER_MAX_PIXELS / pixels) * 0.98;
            fitted = smooth(fitted, (int) (image.getWidth() * scale), (int) (image.getHeight() * scale));
        }
        byte[] bytes = jpeg(fitted, 0.95f);
        while (bytes.length > SERVER_MAX_BYTES) {
            fitted = smooth(fitted, fitted.getWidth() * 9 / 10, fitted.getHeight() * 9 / 10);
            bytes = jpeg(fitted, 0.95f);
        }
        return new Upload(image, bytes);
    }

    private static BufferedImage read(File file) {
        if (file.length() > MAX_FILE_BYTES) {
            throw new AppException("That photo is too large. Use a photo under 30 MB.");
        }
        BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (IOException e) {
            throw new AppException("Couldn't open that photo: " + e.getMessage());
        }
        if (image == null) {
            throw new AppException("That file isn't a photo. Use a JPEG or PNG.");
        }
        return image;
    }

    /**
     * The image as an icon fitting inside w x h, drawn sharp: it is scaled for
     * the screen's actual pixel density (125% or 150% Windows scaling) when
     * painted, instead of being drawn small and then stretched.
     */
    public static Icon icon(BufferedImage image, int w, int h) {
        double fit = Math.min((double) w / image.getWidth(), (double) h / image.getHeight());
        int width = Math.max(1, (int) (image.getWidth() * fit));
        int height = Math.max(1, (int) (image.getHeight() * fit));
        return new Icon() {
            private BufferedImage cache;

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                AffineTransform t = g2.getTransform();
                int pw = Math.max(1, (int) Math.round(width * t.getScaleX()));
                int ph = Math.max(1, (int) Math.round(height * t.getScaleY()));
                if (cache == null || cache.getWidth() != pw || cache.getHeight() != ph) {
                    cache = smooth(image, pw, ph);
                }
                // Draw in device pixels: one image pixel per screen pixel.
                Point2D at = t.transform(new Point2D.Double(x, y), null);
                g2.setTransform(AffineTransform.getTranslateInstance(Math.round(at.getX()), Math.round(at.getY())));
                g2.drawImage(cache, 0, 0, null);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return width;
            }

            @Override
            public int getIconHeight() {
                return height;
            }
        };
    }

    /** Scales in halving steps, then once more to size: shrinking a lot in one step loses detail and looks jagged. */
    private static BufferedImage smooth(BufferedImage source, int w, int h) {
        BufferedImage current = source;
        int cw = source.getWidth();
        int ch = source.getHeight();
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            current = scaled(current, cw, ch);
        }
        return cw == w && ch == h && current != source ? current : scaled(current, w, h);
    }

    private static BufferedImage rgb(BufferedImage image) {
        return image.getType() == BufferedImage.TYPE_INT_RGB ? image : scaled(image, image.getWidth(), image.getHeight());
    }

    public static BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new AppException("The photo couldn't be shown.");
            }
            return image;
        } catch (IOException e) {
            throw new AppException("The photo couldn't be shown.");
        }
    }

    /** Fits the image inside w×h, keeping its shape. */
    public static BufferedImage fit(BufferedImage image, int w, int h) {
        double scale = Math.min((double) w / image.getWidth(), (double) h / image.getHeight());
        return scaled(image, Math.max(1, (int) (image.getWidth() * scale)), Math.max(1, (int) (image.getHeight() * scale)));
    }

    private static BufferedImage scaled(BufferedImage source, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(source, 0, 0, w, h, null);
        g2.dispose();
        return out;
    }

    private static byte[] jpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new AppException("This computer can't save JPEG images.");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new AppException("Couldn't prepare the photo: " + e.getMessage());
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
