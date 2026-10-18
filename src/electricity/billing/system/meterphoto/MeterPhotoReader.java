package electricity.billing.system.meterphoto;

import electricity.billing.system.common.AppException;
import electricity.billing.system.model.PhotoReading;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Reads a meter photo on the server, so the reading on a bill always comes
 * from the meter itself and never from what someone typed.
 *
 * Runs entirely offline, with no external service: {@link LcdReader} reads the
 * seven-segment display of a digital meter, {@link DrumReader} the number
 * wheels of an older electromechanical one, and {@link SerialMatcher} finds which customer's
 * meter number is printed on the meter. When either can't be done with confidence the photo is
 * refused with advice on retaking it; a doubtful photo is never guessed at.
 */
public final class MeterPhotoReader {

    /** Largest photo accepted, in bytes. */
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    /** Largest photo accepted, in pixels: guards against "decompression bomb" images. */
    private static final long MAX_PIXELS = 40_000_000L;
    /** Photos are analysed at this size; larger ones are scaled down first. */
    private static final int WORKING_SIZE = 1600;
    /** Photos smaller than this are enlarged before the number wheels are read. */
    private static final int SMALL_PHOTO_SIZE = 900;

    private MeterPhotoReader() {
    }

    /**
     * Reads the display and works out which meter the photo shows: the printed
     * meter number is checked against every meter on file ({@code numbersByMeter}:
     * customer meter number to the number printed on that meter). Checking known
     * numbers is far more reliable than reading arbitrary text, so exactly one
     * must match.
     *
     * @throws AppException with a message for the admin if the photo can't be used
     */
    public static PhotoReading identify(byte[] photo, Map<String, String> numbersByMeter) {
        BufferedImage image = decode(photo);
        Raster raster = Raster.of(image, WORKING_SIZE);
        LcdReader.Result display = LcdReader.read(raster);
        String text;
        String note = null;
        Raster.Box readingArea;
        if (display.ok()) {
            text = display.text();
            readingArea = display.display();
        } else {
            // No readable lit display: an electromechanical meter's number wheels, perhaps. Small photos are
            // enlarged first so the wheels' digits and the stamped number are big enough to compare.
            raster = Raster.atLeast(image, SMALL_PHOTO_SIZE, WORKING_SIZE);
            DrumReader.Result wheels = DrumReader.read(raster);
            if (!wheels.ok()) {
                throw new AppException(display.display() != null ? display.problem() : wheels.problem());
            }
            text = wheels.text();
            note = wheels.note();
            readingArea = wheels.counter();
        }
        SerialMatcher.Analysis printed = SerialMatcher.analyze(raster, readingArea);
        Map<String, SerialMatcher.Result> matches = new LinkedHashMap<>();
        for (Map.Entry<String, String> meter : numbersByMeter.entrySet()) {
            if (meter.getValue() == null || normalizeSerial(meter.getValue()).length() < 3) {
                continue;
            }
            SerialMatcher.Result r = SerialMatcher.score(printed, meter.getValue());
            if (r.matched()) {
                matches.put(meter.getKey(), r);
            }
        }
        // A number found inside a longer matching number (e.g. 401987 inside 4401987) is the longer one.
        List<String> found = new ArrayList<>(matches.keySet());
        found.removeIf(a -> found.stream().anyMatch(b -> !a.equals(b)
                && normalizeSerial(numbersByMeter.get(b)).contains(normalizeSerial(numbersByMeter.get(a)))
                && normalizeSerial(numbersByMeter.get(b)).length() > normalizeSerial(numbersByMeter.get(a)).length()));
        if (found.isEmpty()) {
            throw new AppException("The meter number in the photo doesn't match any customer's meter. Make sure the meter's "
                    + "number is in the photo and in focus. If the customer is new, add them first; if they were added before "
                    + "meter numbers were recorded, set their meter number on the Customers page.");
        }
        if (found.size() > 1) {
            throw new AppException("The photo matches more than one meter (" + String.join(", ", found) + "). "
                    + "Retake it closer, so only this meter's number is in view.");
        }
        String meterNo = found.get(0);
        int reading = new BigDecimal(text).setScale(0, RoundingMode.FLOOR).intValueExact();
        return new PhotoReading(meterNo, text, reading, matches.get(meterNo).seen(), note);
    }

    /** Letters and digits only, upper case: how serials are stored and compared. */
    public static String normalizeSerial(String serial) {
        return serial == null ? "" : SerialMatcher.normalize(serial);
    }

    /** Opens an uploaded photo safely: size limits, and a check that it really is an image. */
    static BufferedImage decode(byte[] photo) {
        if (photo == null || photo.length == 0) {
            throw new AppException("Upload a photo of the meter.");
        }
        if (photo.length > MAX_BYTES) {
            throw new AppException("The photo is too large (over 8 MB). Use a smaller photo.");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(photo))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new AppException("That file isn't a photo the server can read. Use a JPEG or PNG.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new AppException("The photo is too large. Use a photo under 40 megapixels.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new AppException("The photo couldn't be opened. Use a JPEG or PNG.");
        }
    }
}
