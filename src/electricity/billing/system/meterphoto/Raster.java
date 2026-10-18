package electricity.billing.system.meterphoto;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** A photo as plain arrays of brightness and colour, plus the few image operations the readers need. */
final class Raster {

    final int width;
    final int height;
    final int[] r;
    final int[] g;
    final int[] b;
    /** Perceived brightness, 0 (black) to 255 (white). */
    final int[] lum;

    private Raster(int width, int height) {
        this.width = width;
        this.height = height;
        int n = width * height;
        r = new int[n];
        g = new int[n];
        b = new int[n];
        lum = new int[n];
    }

    /** Copies the image, scaling it so its longer side is at most {@code maxSide} pixels. */
    static Raster of(BufferedImage source, int maxSide) {
        BufferedImage image = source;
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest > maxSide) {
            double scale = (double) maxSide / longest;
            image = scaled(source, (int) Math.round(source.getWidth() * scale), (int) Math.round(source.getHeight() * scale));
        }
        Raster raster = new Raster(image.getWidth(), image.getHeight());
        int[] argb = image.getRGB(0, 0, raster.width, raster.height, null, 0, raster.width);
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            raster.r[i] = (p >> 16) & 0xFF;
            raster.g[i] = (p >> 8) & 0xFF;
            raster.b[i] = p & 0xFF;
            raster.lum[i] = (299 * raster.r[i] + 587 * raster.g[i] + 114 * raster.b[i]) / 1000;
        }
        return raster;
    }

    static BufferedImage scaled(BufferedImage source, int w, int h) {
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(source, 0, 0, out.getWidth(), out.getHeight(), null);
        g2.dispose();
        return out;
    }

    /** The whole image turned by {@code angle} radians about its centre (corners filled from the nearest edge). */
    Raster rotated(double angle) {
        return cropRotated(width / 2.0, height / 2.0, width / 2.0, height / 2.0, angle, 1);
    }

    /**
     * The angle (radians) that makes printed text lines run level: the turn that
     * gives the sharpest pattern of dark rows and light gaps. Checked from -8° to 8°.
     */
    double textAngle() {
        return textAngle(8);
    }

    /** Like {@link #textAngle()}, checking from -{@code maxDegrees} to {@code maxDegrees}. */
    double textAngle(double maxDegrees) {
        int step = Math.max(1, Math.max(width, height) / 500);
        int[] mean = localMean(Math.max(6, Math.min(width, height) / 30));
        List<int[]> ink = new ArrayList<>();
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int i = y * width + x;
                if (lum[i] < mean[i] - Math.max(14, mean[i] * 0.12)) {
                    ink.add(new int[] {x, y});
                }
            }
        }
        double best = 0;
        double bestScore = -1;
        for (double deg = -maxDegrees; deg <= maxDegrees + 0.001; deg += 0.5) {
            double a = Math.toRadians(deg);
            double sin = Math.sin(a);
            double cos = Math.cos(a);
            int[] rows = new int[height * 2 + 2];
            for (int[] p : ink) {
                int row = (int) Math.round((p[1] - height / 2.0) * cos - (p[0] - width / 2.0) * sin + height);
                if (row >= 0 && row < rows.length) {
                    rows[row]++;
                }
            }
            double score = 0;
            for (int r : rows) {
                score += (double) r * r;
            }
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    /** Enlarged so the longer side is at least {@code minSide}: small photos give characters too few pixels. */
    static Raster atLeast(java.awt.image.BufferedImage source, int minSide, int maxSide) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest >= minSide) {
            return of(source, maxSide);
        }
        double scale = (double) minSide / longest;
        return of(scaled(source, (int) Math.round(source.getWidth() * scale), (int) Math.round(source.getHeight() * scale)), maxSide);
    }

    /** The same picture with light and dark swapped, so light print on a dark background reads like dark print. */
    Raster inverted() {
        Raster out = new Raster(width, height);
        for (int i = 0; i < lum.length; i++) {
            out.r[i] = 255 - r[i];
            out.g[i] = 255 - g[i];
            out.b[i] = 255 - b[i];
            out.lum[i] = 255 - lum[i];
        }
        return out;
    }

    /** A cut-out of this raster, enlarged by {@code scale} so thin strokes survive sampling. */
    Raster crop(Box box, int scale) {
        Raster out = new Raster(box.width() * scale, box.height() * scale);
        for (int y = 0; y < out.height; y++) {
            for (int x = 0; x < out.width; x++) {
                double sx = box.x0() + (x + 0.5) / scale - 0.5;
                double sy = box.y0() + (y + 0.5) / scale - 0.5;
                int o = y * out.width + x;
                out.r[o] = bilinear(r, sx, sy);
                out.g[o] = bilinear(g, sx, sy);
                out.b[o] = bilinear(b, sx, sy);
                out.lum[o] = bilinear(lum, sx, sy);
            }
        }
        return out;
    }

    /**
     * A cut-out centred on (cx, cy), turned by {@code angle} radians so a tilted
     * display comes out level, and enlarged by {@code scale}.
     */
    Raster cropRotated(double cx, double cy, double halfW, double halfH, double angle, int scale) {
        Raster out = new Raster((int) Math.ceil(halfW * 2 * scale), (int) Math.ceil(halfH * 2 * scale));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        for (int y = 0; y < out.height; y++) {
            for (int x = 0; x < out.width; x++) {
                double lx = (x + 0.5) / scale - halfW;
                double ly = (y + 0.5) / scale - halfH;
                double sx = cx + lx * cos - ly * sin;
                double sy = cy + lx * sin + ly * cos;
                int o = y * out.width + x;
                out.r[o] = bilinear(r, sx, sy);
                out.g[o] = bilinear(g, sx, sy);
                out.b[o] = bilinear(b, sx, sy);
                out.lum[o] = bilinear(lum, sx, sy);
            }
        }
        return out;
    }

    private int bilinear(int[] channel, double x, double y) {
        int x0 = clamp((int) Math.floor(x), width - 1);
        int y0 = clamp((int) Math.floor(y), height - 1);
        int x1 = clamp(x0 + 1, width - 1);
        int y1 = clamp(y0 + 1, height - 1);
        double fx = Math.max(0, Math.min(1, x - x0));
        double fy = Math.max(0, Math.min(1, y - y0));
        double top = channel[y0 * width + x0] * (1 - fx) + channel[y0 * width + x1] * fx;
        double bottom = channel[y1 * width + x0] * (1 - fx) + channel[y1 * width + x1] * fx;
        return (int) Math.round(top * (1 - fy) + bottom * fy);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    /** Mean brightness around each pixel, over a (2r+1)² square: used for adaptive thresholds. */
    int[] localMean(int radius) {
        long[] integral = new long[(width + 1) * (height + 1)];
        for (int y = 0; y < height; y++) {
            long row = 0;
            for (int x = 0; x < width; x++) {
                row += lum[y * width + x];
                integral[(y + 1) * (width + 1) + x + 1] = integral[y * (width + 1) + x + 1] + row;
            }
        }
        int[] mean = new int[width * height];
        for (int y = 0; y < height; y++) {
            int ya = Math.max(0, y - radius);
            int yb = Math.min(height, y + radius + 1);
            for (int x = 0; x < width; x++) {
                int xa = Math.max(0, x - radius);
                int xb = Math.min(width, x + radius + 1);
                long sum = integral[yb * (width + 1) + xb] - integral[ya * (width + 1) + xb]
                        - integral[yb * (width + 1) + xa] + integral[ya * (width + 1) + xa];
                mean[y * width + x] = (int) (sum / ((long) (xb - xa) * (yb - ya)));
            }
        }
        return mean;
    }

    /** An axis-aligned rectangle, inclusive of x0/y0 and exclusive of x1/y1. */
    record Box(int x0, int y0, int x1, int y1) {
        int width() {
            return x1 - x0;
        }

        int height() {
            return y1 - y0;
        }

        double centerX() {
            return (x0 + x1) / 2.0;
        }

        double centerY() {
            return (y0 + y1) / 2.0;
        }
    }

    /** One connected group of "on" pixels in a mask. */
    record Blob(Box box, int area, int[] pixels) {
    }

    /** Connected groups of true pixels (8-neighbour), skipping groups smaller than {@code minArea}. */
    static List<Blob> blobs(boolean[] mask, int width, int height, int minArea) {
        int[] label = new int[mask.length];
        int[] stack = new int[mask.length];
        List<Blob> found = new ArrayList<>();
        int next = 1;
        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || label[start] != 0) {
                continue;
            }
            int top = 0;
            stack[top++] = start;
            label[start] = next;
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1, area = 0;
            List<Integer> pixels = new ArrayList<>();
            while (top > 0) {
                int p = stack[--top];
                int px = p % width;
                int py = p / width;
                area++;
                pixels.add(p);
                x0 = Math.min(x0, px);
                y0 = Math.min(y0, py);
                x1 = Math.max(x1, px);
                y1 = Math.max(y1, py);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = px + dx;
                        int ny = py + dy;
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                            continue;
                        }
                        int q = ny * width + nx;
                        if (mask[q] && label[q] == 0) {
                            label[q] = next;
                            stack[top++] = q;
                        }
                    }
                }
            }
            next++;
            if (area >= minArea) {
                found.add(new Blob(new Box(x0, y0, x1 + 1, y1 + 1), area, pixels.stream().mapToInt(Integer::intValue).toArray()));
            }
        }
        return found;
    }
}
