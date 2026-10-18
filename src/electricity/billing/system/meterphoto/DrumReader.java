package electricity.billing.system.meterphoto;

import electricity.billing.system.meterphoto.Raster.Box;
import electricity.billing.system.meterphoto.SerialMatcher.DigitGuess;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Reads the number wheels of an electromechanical (rotating disc) meter: a row
 * of dark drums showing light digits, as on older analogue kWh meters.
 *
 * The counter is the row of at least four clearly read digits, evenly spaced,
 * on a dark band; all the ways it reads at different contrasts must agree. The
 * last wheel turns continuously, so it is often caught between two digits.
 * It is then matched against digits rolling over, and if more than one fits,
 * the lowest is taken, the meter readers' rule: a reading is never raised by
 * a guess, and any shortfall is billed with next month's reading. The result
 * then carries a note for the admin. A last wheel in a red window counts
 * tenths and is read after a decimal point.
 */
final class DrumReader {

    /** Below this brightness a pixel counts as part of the dark drums. */
    private static final int DARK = 95;
    /** A rolling digit that scores within this of the best one is just as possible. */
    private static final double ROLLING_TIE = 0.03;

    /** What was read ({@code note} explains a doubtful last wheel, or is null), or why nothing could be. */
    record Result(String text, Box counter, String note, String problem) {
        boolean ok() {
            return problem == null;
        }

        static Result fail(String problem) {
            return new Result(null, null, null, problem);
        }
    }

    /** One way the counter was read: its clear digits, where each sits, and the wheel after them if it isn't clear. */
    private record Reading(List<Box> boxes, String digits, Box next, Box area) {
    }

    private DrumReader() {
    }

    static Result read(Raster photo) {
        // Light digits on dark drums become dark print on light, which the character matcher reads.
        Raster inverted = photo.inverted();
        SerialMatcher.Analysis printed = SerialMatcher.analyze(inverted, null);
        List<Reading> readings = new ArrayList<>();
        for (List<DigitGuess> line : SerialMatcher.digitLines(printed)) {
            for (int start = 0; start < line.size(); start++) {
                int end = runEnd(line, start);
                if (end - start >= 4 && onDarkBand(photo, line.subList(start, end))) {
                    readings.add(reading(line, start, end));
                    start = end - 1;
                }
            }
        }
        if (readings.isEmpty()) {
            return Result.fail("Couldn't find the meter's reading: no lit display or number wheels could be read. Take the "
                    + "photo straight on, close enough that the digits are sharp, without glare.");
        }
        // The same counter is usually found at several contrast levels: they must agree wherever they overlap.
        Reading best = readings.get(0);
        for (Reading r : readings) {
            if (r.digits.length() > best.digits.length()) {
                best = r;
            }
        }
        for (Reading r : readings) {
            if (!overlaps(r.area, best.area)) {
                return Result.fail("The photo shows more than one row of number wheels. Retake it so only the meter's "
                        + "kWh counter is in view.");
            }
            if (!agrees(r, best)) {
                return Result.fail("The number wheels could be read more than one way. Retake the photo closer, in focus "
                        + "and without glare.");
            }
        }
        String digits = best.digits;
        Box last = best.boxes.get(best.boxes.size() - 1);
        double pitch = (last.centerX() - best.boxes.get(0).centerX()) / (best.boxes.size() - 1);
        // An unclear wheel after the clear ones, as seen at any contrast level.
        Box next = null;
        for (Reading r : readings) {
            if (r.next != null && Math.abs(r.next.centerX() - (last.centerX() + pitch)) < 0.3 * pitch) {
                next = r.next;
                break;
            }
        }
        if (next == null && wheelThere(photo, window(best.boxes, last.centerX() + pitch))) {
            return Result.fail("The meter's number wheels were found, but the last wheel couldn't be read. Retake the "
                    + "photo closer and in focus.");
        }
        String note = null;
        if (next != null) {
            // The next wheel is there but not clearly one digit: it's part-way between two.
            Box window = window(best.boxes, next.centerX());
            double[] scores = SerialMatcher.rollingScores(inverted, window);
            double top = Arrays.stream(scores).max().orElse(0);
            if (LcdReader.trace != null) {
                LcdReader.trace.accept("rolling", Arrays.toString(scores));
            }
            if (top < 0.6) {
                return Result.fail("The meter's number wheels were found, but the last wheel couldn't be read. Retake the "
                        + "photo closer and in focus.");
            }
            List<Integer> possible = IntStream.range(0, 10).filter(d -> scores[d] >= top - ROLLING_TIE).boxed().toList();
            int digit = possible.get(0);
            digits += digit;
            last = window;
            List<String> others = possible.stream().skip(1).map(String::valueOf).toList();
            String alternatives = others.size() <= 1 ? String.join("", others)
                    : String.join(", ", others.subList(0, others.size() - 1)) + " or " + others.get(others.size() - 1);
            note = "The meter's last number wheel was turning between two numbers. It was read as " + digit
                    + (others.isEmpty() ? "" : " (it could also be " + alternatives + "; the lowest is taken)")
                    + ". Check it against the meter; any shortfall is billed with next month's reading.";
        }
        boolean tenths = redWindow(photo, last);
        String text = tenths ? digits.substring(0, digits.length() - 1) + "." + digits.substring(digits.length() - 1) : digits;
        return new Result(text, span(List.of(best.area, last)), tenths ? null : note, null);
    }

    /** End (exclusive) of the run of confident, evenly spaced, same-sized digits starting at {@code start}. */
    private static int runEnd(List<DigitGuess> line, int start) {
        if (!line.get(start).confident()) {
            return start;
        }
        int end = start + 1;
        while (end < line.size() && line.get(end).confident() && fits(line.get(end - 1).box(), line.get(end).box(),
                end - start >= 2 ? pitch(line, start, end) : 0)) {
            end++;
        }
        return end;
    }

    /** True when {@code next} could be the next wheel after {@code prev}: level, the same size, and (once known) at the same spacing. */
    private static boolean fits(Box prev, Box next, double pitch) {
        int lo = Math.min(prev.height(), next.height());
        int hi = Math.max(prev.height(), next.height());
        double step = next.centerX() - prev.centerX();
        boolean level = Math.abs(next.centerY() - prev.centerY()) < 0.3 * lo && hi <= 1.3 * lo;
        boolean near = step > 0.4 * hi && step < 1.6 * hi;
        return level && near && (pitch == 0 || (step > 0.75 * pitch && step < 1.3 * pitch));
    }

    private static double pitch(List<DigitGuess> line, int start, int end) {
        double[] steps = new double[end - start - 1];
        for (int i = start + 1; i < end; i++) {
            steps[i - start - 1] = line.get(i).box().centerX() - line.get(i - 1).box().centerX();
        }
        Arrays.sort(steps);
        return steps[steps.length / 2];
    }

    /** The drums are dark: most of the counter's area, between and around the digits, is dark in the photo. */
    private static boolean onDarkBand(Raster photo, List<DigitGuess> run) {
        Box area = span(run.stream().map(DigitGuess::box).toList());
        int dark = 0;
        for (int y = area.y0(); y < area.y1(); y++) {
            for (int x = area.x0(); x < area.x1(); x++) {
                dark += photo.lum[y * photo.width + x] < DARK ? 1 : 0;
            }
        }
        return dark >= 0.4 * area.width() * area.height();
    }

    /** True when {@code window} looks like another wheel: on the dark band, with light digit strokes in it. */
    private static boolean wheelThere(Raster photo, Box window) {
        if (window.x0() < 0 || window.x1() > photo.width) {
            return false;
        }
        int dark = 0;
        int light = 0;
        for (int y = window.y0(); y < window.y1(); y++) {
            for (int x = window.x0(); x < window.x1(); x++) {
                int v = photo.lum[y * photo.width + x];
                dark += v < DARK ? 1 : 0;
                light += v > 170 ? 1 : 0;
            }
        }
        int area = window.width() * window.height();
        return dark >= 0.4 * area && light >= 0.08 * area;
    }

    /** A run of clearly read digits, noting the wheel right after it if one is there but unclear. */
    private static Reading reading(List<DigitGuess> line, int start, int end) {
        List<DigitGuess> run = line.subList(start, end);
        List<Box> boxes = run.stream().map(DigitGuess::box).toList();
        String digits = run.stream().map(g -> String.valueOf(g.digit())).collect(Collectors.joining());
        Box next = end < line.size() && fits(line.get(end - 1).box(), line.get(end).box(), pitch(line, start, end))
                ? line.get(end).box() : null;
        return new Reading(boxes, digits, next, span(boxes));
    }

    /** Where a whole digit would sit on the wheel centred at {@code centerX}, judged from the clearly read wheels. */
    private static Box window(List<Box> wheels, double centerX) {
        int[] widths = wheels.stream().mapToInt(Box::width).sorted().toArray();
        int[] tops = wheels.stream().mapToInt(Box::y0).sorted().toArray();
        int[] bottoms = wheels.stream().mapToInt(Box::y1).sorted().toArray();
        int w = widths[widths.length / 2];
        int x0 = (int) Math.round(centerX - w / 2.0);
        return new Box(x0, tops[tops.length / 2], x0 + w, bottoms[bottoms.length / 2]);
    }

    /** A wheel framed in red counts tenths of a kWh. */
    private static boolean redWindow(Raster photo, Box digit) {
        int padX = Math.max(2, digit.width() / 3);
        int padY = Math.max(2, digit.height() / 4);
        int red = 0;
        int all = 0;
        for (int y = Math.max(0, digit.y0() - padY); y < Math.min(photo.height, digit.y1() + padY); y++) {
            for (int x = Math.max(0, digit.x0() - padX); x < Math.min(photo.width, digit.x1() + padX); x++) {
                int i = y * photo.width + x;
                all++;
                red += photo.r[i] > 110 && photo.r[i] > photo.g[i] + 45 && photo.r[i] > photo.b[i] + 45 ? 1 : 0;
            }
        }
        return red >= 0.15 * all;
    }

    /** Two readings of one counter agree where their wheels line up. */
    private static boolean agrees(Reading a, Reading b) {
        for (int i = 0; i < a.boxes.size(); i++) {
            for (int j = 0; j < b.boxes.size(); j++) {
                Box x = a.boxes.get(i);
                Box y = b.boxes.get(j);
                if (Math.abs(x.centerX() - y.centerX()) < 0.4 * Math.max(x.width(), y.width())
                        && a.digits.charAt(i) != b.digits.charAt(j)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean overlaps(Box a, Box b) {
        return a.x0() < b.x1() && b.x0() < a.x1() && a.y0() < b.y1() && b.y0() < a.y1();
    }

    private static Box span(List<Box> boxes) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        for (Box b : boxes) {
            x0 = Math.min(x0, b.x0());
            y0 = Math.min(y0, b.y0());
            x1 = Math.max(x1, b.x1());
            y1 = Math.max(y1, b.y1());
        }
        return new Box(x0, y0, x1, y1);
    }
}
