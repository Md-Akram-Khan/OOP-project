package electricity.billing.system.meterphoto;

import electricity.billing.system.meterphoto.Raster.Blob;
import electricity.billing.system.meterphoto.Raster.Box;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the kWh figure from a meter's seven-segment LCD.
 *
 * <ol>
 *   <li>Find the display: the largest backlit (green) area of the photo.</li>
 *   <li>Mark the dark "ink" of the digits against the display's own brightness.</li>
 *   <li>Undo the italic slant of the digits, then split them by vertical gaps.</li>
 *   <li>For each digit, check which of the seven segments are dark and look the pattern up.</li>
 *   <li>A small dot near the baseline is the decimal point.</li>
 * </ol>
 */
final class LcdReader {

    /** Segments a-g as bits: a=top, b=top right, c=bottom right, d=bottom, e=bottom left, f=top left, g=middle. */
    private static final int A = 1, B = 2, C = 4, D = 8, E = 16, F = 32, G = 64;
    private static final int[][] DIGITS = {
        {A | B | C | D | E | F},
        {B | C},
        {A | B | D | E | G},
        {A | B | C | D | G},
        {B | C | F | G},
        {A | C | D | F | G},
        {A | C | D | E | F | G, C | D | E | F | G},
        {A | B | C, A | B | C | F},
        {A | B | C | D | E | F | G},
        {A | B | C | D | F | G, A | B | C | F | G},
    };
    private static final int TARGET_HEIGHT = 220;
    /** Ink is darker than this share of the surrounding brightness. */
    private static final double[] THRESHOLDS = {0.70, 0.78, 0.62};

    /** Test hook: receives intermediate steps while tuning. Null in normal use. */
    static java.util.function.BiConsumer<String, Object> trace;

    private static void trace(String step, Object value) {
        if (trace != null) {
            trace.accept(step, value);
        }
    }

    /** What was read, or why nothing could be. {@code certainty} is 0..1. */
    record Result(String text, double certainty, Box display, String problem) {
        boolean ok() {
            return problem == null;
        }

        static Result fail(String problem, Box display) {
            return new Result(null, 0, display, problem);
        }
    }

    private LcdReader() {
    }

    static Result read(Raster photo) {
        Blob found = findDisplay(photo);
        if (found == null) {
            return Result.fail("Couldn't find the meter's display. Take the photo straight on, with the lit display clearly visible.", null);
        }
        // Level a tilted display using the orientation of its outline.
        int w = photo.width;
        double mx = 0;
        double my = 0;
        for (int p : found.pixels()) {
            mx += p % w;
            my += p / w;
        }
        mx /= found.area();
        my /= found.area();
        double sxx = 0;
        double syy = 0;
        double sxy = 0;
        for (int p : found.pixels()) {
            double dx = p % w - mx;
            double dy = p / w - my;
            sxx += dx * dx;
            syy += dy * dy;
            sxy += dx * dy;
        }
        double angle = 0.5 * Math.atan2(2 * sxy, sxx - syy);
        if (Math.abs(angle) > Math.toRadians(15)) {
            return Result.fail("The photo is too tilted. Hold the phone level with the meter.", found.box());
        }
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double halfW = 0;
        double halfH = 0;
        for (int p : found.pixels()) {
            double dx = p % w - mx;
            double dy = p / w - my;
            halfW = Math.max(halfW, Math.abs(dx * cos + dy * sin));
            halfH = Math.max(halfH, Math.abs(-dx * sin + dy * cos));
        }
        int scale = Math.max(1, Math.min(8, (int) Math.ceil(TARGET_HEIGHT / (2 * halfH))));
        Raster lcd = photo.cropRotated(mx, my, halfW + 1, halfH + 1, angle, scale);
        trace("angle", Math.toDegrees(angle));
        // Read at a few ink thresholds. Faint photos need a looser one. Every
        // threshold that reads the display must agree on the digits, and the ones
        // that see the decimal point must agree on where it is; otherwise the
        // photo is ambiguous and is refused rather than guessed.
        Result first = null;
        String digits = null;
        int point = -1;
        for (double threshold : THRESHOLDS) {
            Result r = readDisplay(lcd, found.box(), threshold);
            trace("threshold " + threshold, r.ok() ? r.text() : "refused");
            if (first == null) {
                first = r;
            }
            if (!r.ok()) {
                continue;
            }
            String plain = r.text().replace(".", "");
            int dot = r.text().indexOf('.');
            if ((digits != null && !digits.equals(plain)) || (dot >= 0 && point >= 0 && dot != point)) {
                return Result.fail("The display could be read more than one way. Retake the photo closer, in focus and without glare.",
                        found.box());
            }
            digits = plain;
            point = dot >= 0 ? dot : point;
        }
        if (digits == null) {
            return first;
        }
        String agreed = point > 0 ? digits.substring(0, point) + "." + digits.substring(point) : digits;
        return new Result(agreed, 1.0, found.box(), null);
    }

    /** The largest green, display-shaped area. */
    static Blob findDisplay(Raster p) {
        boolean[] green = new boolean[p.width * p.height];
        for (int i = 0; i < green.length; i++) {
            green[i] = p.g[i] >= 70 && p.g[i] > p.r[i] + 15 && p.g[i] > p.b[i] + 28;
        }
        int minArea = Math.max(50, p.width * p.height / 400);
        Blob best = null;
        for (Blob blob : Raster.blobs(green, p.width, p.height, minArea)) {
            double aspect = (double) blob.box().width() / blob.box().height();
            if (aspect >= 1.4 && aspect <= 9 && (best == null || blob.area() > best.area())) {
                best = blob;
            }
        }
        return best;
    }

    private static Result readDisplay(Raster lcd, Box display, double threshold) {
        int w = lcd.width;
        int h = lcd.height;
        int[] mean = lcd.localMean(Math.max(8, h / 3));
        boolean[] ink = new boolean[w * h];
        for (int i = 0; i < ink.length; i++) {
            ink[i] = lcd.lum[i] < mean[i] * threshold;
        }
        trace("lcd", lcd);
        trace("ink", ink);
        List<Blob> blobs = new ArrayList<>();
        for (Blob blob : Raster.blobs(ink, w, h, Math.max(4, h * h / 2500))) {
            Box bx = blob.box();
            boolean touchesEdge = bx.x0() <= 1 || bx.y0() <= 1 || bx.x1() >= w - 1 || bx.y1() >= h - 1;
            if (!touchesEdge) {
                blobs.add(blob);
            }
        }
        // Vertical segments are the tall strokes; they set the band the digits occupy.
        List<Blob> tall = blobs.stream().filter(bl -> bl.box().height() >= 0.15 * h && bl.box().width() <= 0.35 * h).toList();
        if (tall.size() < 2) {
            return Result.fail("Couldn't make out the digits on the display. Make sure it's in focus and not covered by glare.", display);
        }
        int[] band0 = digitBand(tall, w, h);
        int top = band0[0];
        int bottom = band0[1];
        int band = bottom - top;
        if (band < 0.2 * h) {
            return Result.fail("Couldn't make out the digits on the display. Make sure it's in focus and not covered by glare.", display);
        }
        int slack = (int) Math.round(band * 0.06);

        List<Blob> strokes = new ArrayList<>();
        List<Blob> dots = new ArrayList<>();
        for (Blob bl : blobs) {
            Box bx = bl.box();
            if (bx.centerY() < top - slack || bx.centerY() > bottom + slack) {
                continue;
            }
            boolean small = bx.height() < 0.2 * band && bx.width() < 0.2 * band;
            boolean low = bx.centerY() > top + 0.78 * band;
            boolean square = bx.width() <= bx.height() * 2.2 && bx.height() <= bx.width() * 2.2;
            if (small && low && square) {
                dots.add(bl);
            } else if (bx.height() >= 0.05 * band || bx.width() >= 0.12 * band) {
                strokes.add(bl);
            }
        }

        double slant = bestSlant(strokes, w, (top + bottom) / 2.0);
        trace("band", top + ".." + bottom + " slant " + slant + " strokes " + strokes.size() + " dots " + dots.size());
        double cy = (top + bottom) / 2.0;
        int offset = (int) Math.ceil(Math.abs(slant) * h) + 2;
        int span = w + offset * 2;
        int[] columns = new int[span];
        for (Blob bl : strokes) {
            for (int p : bl.pixels()) {
                int x = p % w;
                int y = p / w;
                int sx = (int) Math.round(x + slant * (y - cy)) + offset;
                if (sx >= 0 && sx < span) {
                    columns[sx]++;
                }
            }
        }
        List<int[]> spans = spans(columns, Math.max(1, band / 60), Math.max(2, band / 25));
        if (spans.isEmpty()) {
            return Result.fail("Couldn't make out the digits on the display.", display);
        }
        double digitWidth = median(spans.stream().filter(s -> s[1] - s[0] > 0.25 * band).mapToInt(s -> s[1] - s[0]).toArray());
        if (digitWidth <= 0) {
            digitWidth = band * 0.55;
        }
        spans = splitWide(spans, digitWidth);
        trace("spans", spans.stream().map(sp -> sp[0] + "-" + sp[1]).toList() + " digitWidth " + digitWidth);

        // Decode each digit by sampling its seven segment regions.
        boolean[] strokeMask = new boolean[w * h];
        for (Blob bl : strokes) {
            for (int p : bl.pixels()) {
                strokeMask[p] = true;
            }
        }
        StringBuilder text = new StringBuilder();
        List<Double> centers = new ArrayList<>();
        int exact = 0;
        for (int[] s : spans) {
            if (rowCoverage(strokeMask, w, h, slant, cy, offset, s, top, band) < 0.55) {
                continue; // not a digit: a speck, glare or shading that doesn't run the digit's full height
            }
            int[] decoded = decode(strokeMask, w, h, slant, cy, offset, s, top, band, digitWidth);
            if (decoded[0] < 0) {
                return Result.fail("One of the digits on the display couldn't be read. Retake the photo closer and in focus.", display);
            }
            text.append((char) ('0' + decoded[0]));
            exact += decoded[1] == 0 ? 1 : 0;
            centers.add((s[0] + s[1]) / 2.0);
        }
        if (text.length() < 3 || text.length() > 9) {
            return Result.fail("Found " + text.length() + " digits on the display, which doesn't look like a meter reading. "
                    + "Retake the photo with the whole display in view.", display);
        }

        // Decimal point: a dot between two digits, near the baseline.
        int point = -1;
        for (Blob dot : dots) {
            double dx = dot.box().centerX() + slant * (dot.box().centerY() - cy) + offset;
            int before = 0;
            for (double c : centers) {
                if (c < dx) {
                    before++;
                }
            }
            if (before > 0 && before < centers.size()) {
                point = before;
            }
        }
        if (point > 0) {
            text.insert(point, '.');
        }
        double certainty = (double) exact / centers.size();
        return new Result(text.toString(), certainty, display, null);
    }

    /**
     * Top and bottom of the digits: the rows where stroke ink is dense. Using
     * density rather than the outermost stroke keeps stray specks, glare and
     * the small "kWh" label from stretching the band.
     */
    private static int[] digitBand(List<Blob> tall, int w, int h) {
        int[] rows = new int[h];
        for (Blob bl : tall) {
            for (int p : bl.pixels()) {
                rows[p / w]++;
            }
        }
        int max = 0;
        for (int v : rows) {
            max = Math.max(max, v);
        }
        int threshold = Math.max(1, max / 4);
        int top = 0;
        while (top < h - 1 && rows[top] < threshold) {
            top++;
        }
        int bottom = h - 1;
        while (bottom > top && rows[bottom] < threshold) {
            bottom--;
        }
        // The horizontal top and bottom segments are thin and sit just outside the dense rows.
        int pad = (int) Math.round((bottom - top) * 0.06);
        return new int[] {Math.max(0, top - pad), Math.min(h, bottom + 1 + pad)};
    }

    /** Share of the band's rows that have ink inside this column span. */
    private static double rowCoverage(boolean[] mask, int w, int h, double slant, double cy, int offset, int[] span,
            int top, int band) {
        int covered = 0;
        for (int y = top; y < top + band && y < h; y++) {
            for (int x = 0; x < w; x++) {
                double sx = x + slant * (y - cy) + offset;
                if (sx >= span[0] && sx < span[1] && mask[y * w + x]) {
                    covered++;
                    break;
                }
            }
        }
        return (double) covered / band;
    }

    /** The shear that makes the digits' strokes most vertical (sharpest column histogram). */
    private static double bestSlant(List<Blob> strokes, int w, double cy) {
        double best = 0;
        long bestScore = -1;
        for (int step = -24; step <= 24; step++) {
            double s = step * 0.02;
            int offset = (int) Math.ceil(Math.abs(s) * 4 * w) + 2;
            int[] hist = new int[w + offset * 2];
            for (Blob bl : strokes) {
                for (int p : bl.pixels()) {
                    int x = p % w;
                    int y = p / w;
                    int sx = (int) Math.round(x + s * (y - cy)) + offset;
                    if (sx >= 0 && sx < hist.length) {
                        hist[sx]++;
                    }
                }
            }
            long score = 0;
            for (int v : hist) {
                score += (long) v * v;
            }
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    /** Runs of columns with ink, joining runs separated by gaps narrower than {@code joinGap}. */
    private static List<int[]> spans(int[] columns, int minCount, int joinGap) {
        List<int[]> raw = new ArrayList<>();
        int start = -1;
        for (int x = 0; x <= columns.length; x++) {
            boolean on = x < columns.length && columns[x] >= minCount;
            if (on && start < 0) {
                start = x;
            } else if (!on && start >= 0) {
                raw.add(new int[] {start, x});
                start = -1;
            }
        }
        List<int[]> joined = new ArrayList<>();
        for (int[] s : raw) {
            if (!joined.isEmpty() && s[0] - joined.get(joined.size() - 1)[1] < joinGap) {
                joined.get(joined.size() - 1)[1] = s[1];
            } else {
                joined.add(s);
            }
        }
        return joined;
    }

    /** Splits runs that are clearly two or more digits touching. */
    private static List<int[]> splitWide(List<int[]> spans, double digitWidth) {
        List<int[]> out = new ArrayList<>();
        for (int[] s : spans) {
            int width = s[1] - s[0];
            int parts = (int) Math.round(width / digitWidth);
            if (width > digitWidth * 1.6 && parts >= 2) {
                for (int i = 0; i < parts; i++) {
                    out.add(new int[] {s[0] + width * i / parts, s[0] + width * (i + 1) / parts});
                }
            } else {
                out.add(s);
            }
        }
        return out;
    }

    /** {digit, hamming distance}; digit -1 if nothing fits or a segment is unclear. */
    private static int[] decode(boolean[] mask, int w, int h, double slant, double cy, int offset, int[] span,
            int top, int band, double digitWidth) {
        int width = span[1] - span[0];
        if (width < digitWidth * 0.5) {
            return new int[] {1, 0}; // only the right-hand segments: a one
        }
        Sampler at = (u, v) -> {
            double y = top + v * band;
            double x = span[0] + u * width - offset - slant * (y - cy);
            int ix = (int) Math.round(x);
            int iy = (int) Math.round(y);
            return ix >= 0 && iy >= 0 && ix < w && iy < h && mask[iy * w + ix];
        };
        int minRun = Math.max(2, band / 50);
        int pattern = 0;
        boolean unclear = false;
        int[] votes = new int[7];
        for (int k = 0; k < 7; k++) {
            int hits = 0;
            for (int line = 0; line < 5; line++) {
                if (scanHits(at, k, line, minRun, band)) {
                    hits++;
                }
            }
            votes[k] = hits;
            if (hits >= 3) {
                pattern |= 1 << k;
            } else if (hits == 2) {
                unclear = true; // neither clearly lit nor clearly dark: don't guess
            }
        }
        trace("digit", java.util.Arrays.toString(votes) + " pattern " + Integer.toBinaryString(pattern));
        int bestDigit = -1;
        int bestDistance = 99;
        boolean tie = false;
        for (int d = 0; d < DIGITS.length; d++) {
            int distance = 99;
            for (int variant : DIGITS[d]) {
                distance = Math.min(distance, Integer.bitCount(variant ^ pattern));
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                bestDigit = d;
                tie = false;
            } else if (distance == bestDistance) {
                tie = true;
            }
        }
        if (bestDistance > 0 || tie || unclear) {
            return new int[] {-1, bestDistance};
        }
        return new int[] {bestDigit, bestDistance};
    }

    /** Ink at (u, v) in a digit's box, u from left and v from top, both 0..1. */
    private interface Sampler {
        boolean ink(double u, double v);
    }

    /**
     * Whether scan line {@code line} (0-4) of segment {@code k} crosses a solid
     * stroke. Horizontal segments (a, g, d) are crossed by short vertical lines,
     * the vertical ones (b, c, e, f) by short horizontal lines.
     */
    private static boolean scanHits(Sampler at, int k, int line, int minRun, int band) {
        double[] across = {0.36, 0.43, 0.50, 0.57, 0.64};
        int steps = Math.max(12, band / 2);
        double from;
        double to;
        boolean vertical;
        double fixed;
        switch (k) {
            case 0 -> { from = 0.00; to = 0.27; vertical = true; fixed = across[line]; }          // a
            case 6 -> { from = 0.37; to = 0.63; vertical = true; fixed = across[line]; }          // g
            case 3 -> { from = 0.73; to = 1.00; vertical = true; fixed = across[line]; }          // d
            case 5 -> { from = -0.05; to = 0.48; vertical = false; fixed = 0.21 + line * 0.04; }  // f
            case 1 -> { from = 0.52; to = 1.05; vertical = false; fixed = 0.21 + line * 0.04; }   // b
            case 4 -> { from = -0.05; to = 0.48; vertical = false; fixed = 0.63 + line * 0.04; }  // e
            default -> { from = 0.52; to = 1.05; vertical = false; fixed = 0.63 + line * 0.04; }  // c
        }
        int run = 0;
        for (int i = 0; i <= steps; i++) {
            double t = from + (to - from) * i / steps;
            boolean ink = vertical ? at.ink(fixed, t) : at.ink(t, fixed);
            run = ink ? run + 1 : 0;
            if (run >= Math.max(2, minRun * steps / Math.max(1, band))) {
                return true;
            }
        }
        return false;
    }

    private static double median(int[] values) {
        if (values.length == 0) {
            return 0;
        }
        int[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

}
