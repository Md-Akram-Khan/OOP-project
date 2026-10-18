package electricity.billing.system.meterphoto;

import electricity.billing.system.meterphoto.Raster.Blob;
import electricity.billing.system.meterphoto.Raster.Box;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks that the serial number printed on the meter (e.g. "No: E-756216")
 * is the one on file for the customer.
 *
 * Reading arbitrary printed text offline is unreliable, but checking for a
 * known serial is not: the printed text is split into lines and characters,
 * and each character is compared with font templates of every letter and
 * digit. The serial matches only if some line holds the expected characters
 * in order, each one looking more like the expected character than any other.
 */
final class SerialMatcher {

    private static final int CELL = 32;
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String[] FONTS = {"Arial", "SansSerif", "Verdana", "Tahoma"};
    /**
     * Narrow DIN-style faces, as stamped on older electromechanical meters, used
     * when the server has them; condensed Arial stands in everywhere else.
     */
    private static final String[] NARROW_FONTS = {"Bahnschrift", "DIN", "Arial Narrow", "DejaVu Sans Condensed"};
    /** Mean similarity a matching line needs. */
    private static final double MIN_SCORE = 0.55;
    /** How far the expected character may trail the best other character and still count. */
    private static final double MARGIN = 0.04;
    /** Printed text is darker than its surroundings by at least this share; a few levels are tried. */
    private static final double[] CONTRASTS = {0.12, 0.08, 0.17};
    private static Map<Character, List<float[]>> templates;
    private static Map<Character, List<float[]>> lowerTemplates;
    private static Map<Character, List<float[]>> narrowTemplates;

    record Result(boolean matched, double score, String seen, String problem) {
    }

    private SerialMatcher() {
    }

    /** Letters and digits only, upper case: "No: e-756 216" → "E756216". */
    static String normalize(String serial) {
        StringBuilder out = new StringBuilder();
        for (char ch : serial.toUpperCase(Locale.ROOT).toCharArray()) {
            if (ALPHABET.indexOf(ch) >= 0) {
                out.append(ch);
            }
        }
        return out.toString();
    }

    /** The printed characters found in a photo, ready to be checked against serials. */
    static final class Analysis {
        private final List<List<Glyph>> lines;

        private Analysis(List<List<Glyph>> lines) {
            this.lines = lines;
        }

        /** True when no printed characters were found at all. */
        boolean isEmpty() {
            return lines.stream().allMatch(List::isEmpty);
        }
    }

    /** Finds and classifies the printed characters once; checking a serial against them is then cheap. */
    static Analysis analyze(Raster photo, Box display) {
        return analyze(photo, display, false);
    }

    /**
     * Like {@link #analyze(Raster, Box)}; with {@code mixedCase} lower-case
     * letters are recognised too and compared without regard to case, for
     * printed names such as "Tajul Islam".
     */
    static Analysis analyze(Raster photo, Box display, boolean mixedCase) {
        int[] mean = photo.localMean(Math.max(6, Math.min(photo.width, photo.height) / 30));
        List<List<Glyph>> lines = new ArrayList<>();
        for (double contrast : CONTRASTS) {
            List<List<Glyph>> level = new ArrayList<>();
            for (List<Glyph> all : textLines(photo, mean, display, contrast, mixedCase)) {
                // Marks that look like no letter or digit (dashes, colons, smudges) are skipped.
                level.add(all.stream().filter(g -> g.bestScore >= 0.5).toList());
            }
            lines.addAll(level);
            lines.addAll(joinedRows(level));
        }
        return new Analysis(lines);
    }

    /**
     * Lines on the same printed row joined left to right, however far apart
     * they are: a meter number is often stamped in two groups ("S81C   67062").
     */
    private static List<List<Glyph>> joinedRows(List<List<Glyph>> lines) {
        List<List<Glyph>> sorted = new ArrayList<>(lines.stream().filter(l -> !l.isEmpty()).toList());
        sorted.sort(Comparator.comparingInt(l -> l.get(0).box.x0()));
        boolean[] used = new boolean[sorted.size()];
        List<List<Glyph>> joined = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (used[i]) {
                continue;
            }
            List<Glyph> row = new ArrayList<>(sorted.get(i));
            int parts = 1;
            for (int j = i + 1; j < sorted.size(); j++) {
                if (!used[j] && sameRow(row.get(row.size() - 1), sorted.get(j).get(0))) {
                    row.addAll(sorted.get(j));
                    used[j] = true;
                    parts++;
                }
            }
            if (parts > 1) {
                joined.add(row);
            }
        }
        return joined;
    }

    /** True when {@code next} carries on the row that ends with {@code last}: further right, level with it, the same size. */
    private static boolean sameRow(Glyph last, Glyph next) {
        int lo = Math.min(last.box.height(), next.box.height());
        int hi = Math.max(last.box.height(), next.box.height());
        return next.box.x0() >= last.box.x1() && Math.abs(next.box.centerY() - last.box.centerY()) < 0.35 * lo && hi <= 1.35 * lo;
    }

    /** One character position on a printed line, read as the digit it most resembles. */
    record DigitGuess(Box box, char digit, double score, double runnerUp) {
        /** A good match with no other digit close behind. */
        boolean confident() {
            return score >= 0.72 && score - runnerUp >= 0.04;
        }
    }

    /** Every line's characters as digits, for reading counters rather than checking known numbers. */
    static List<List<DigitGuess>> digitLines(Analysis analysis) {
        List<List<DigitGuess>> out = new ArrayList<>();
        for (List<Glyph> line : analysis.lines) {
            List<DigitGuess> guesses = new ArrayList<>();
            for (Glyph g : line) {
                char best = '?';
                double first = -1;
                double second = -1;
                for (char d = '0'; d <= '9'; d++) {
                    double v = g.scores.getOrDefault(d, -1.0);
                    if (v > first) {
                        second = first;
                        first = v;
                        best = d;
                    } else if (v > second) {
                        second = v;
                    }
                }
                guesses.add(new DigitGuess(g.box, best, first, second));
            }
            out.add(guesses);
        }
        return out;
    }

    /**
     * Scores, for each digit 0-9, how well a number wheel stopped part-way
     * between that digit and the next matches, so the window shows the bottom of
     * one and the top of the other. {@code window} is where a whole digit would
     * sit, in a photo where the digits are dark.
     */
    static double[] rollingScores(Raster p, Box window) {
        int[] mean = p.localMean(Math.max(6, Math.min(p.width, p.height) / 30));
        double[] best = new double[10];
        java.util.Arrays.fill(best, -1);
        // The window is only estimated from the neighbouring wheels, so it is nudged a little each way.
        int stepX = Math.max(1, window.width() / 8);
        int stepY = Math.max(1, window.height() / 10);
        for (int dy = -stepY; dy <= stepY; dy += stepY) {
            for (int dx = -stepX; dx <= stepX; dx += stepX) {
                Box shifted = new Box(Math.max(0, window.x0() + dx), Math.max(0, window.y0() + dy),
                        Math.min(p.width, window.x1() + dx), Math.min(p.height, window.y1() + dy));
                float[] cell = cell(p, mean, shifted);
                for (RollingTemplate t : rollingTemplates()) {
                    best[t.digit] = Math.max(best[t.digit], correlation(cell, t.cell));
                }
            }
        }
        return best;
    }

    static Result verify(Raster photo, String expectedSerial, Box display) {
        return score(analyze(photo, display), expectedSerial);
    }

    /** How well the photo's printed characters match one serial. */
    static Result score(Analysis analysis, String expectedSerial) {
        String expected = normalize(expectedSerial);
        if (expected.length() < 3) {
            return new Result(false, 0, null, "The meter number on file is too short to check.");
        }
        double bestScore = 0;
        String bestSeen = null;
        for (Hit hit : hits(analysis, expected)) {
            if (hit.score > bestScore) {
                bestScore = hit.score;
                bestSeen = hit.seen;
            }
        }
        boolean matched = bestScore >= MIN_SCORE;
        trace("serial", "expected " + expected + " best " + bestScore + " seen " + bestSeen);
        return new Result(matched, bestScore, bestSeen, matched ? null
                : "The meter number in the photo doesn't match " + expectedSerial + ".");
    }

    /**
     * True when the words of {@code text} are all printed on one row, left to
     * right, each matching as {@link #score} would. Each word may be read at a
     * different contrast level: faint or coloured print, like the red date of
     * birth on an NID card, seldom reads whole at any single level. Words
     * shorter than 3 characters are joined to the next ("1 Dec 1990" is checked
     * as "1DEC", "1990").
     */
    static boolean wordsInRow(Analysis analysis, String text) {
        List<String> words = new ArrayList<>();
        String pending = "";
        for (String part : text.trim().split("\\s+")) {
            pending += normalize(part);
            if (pending.length() >= 3) {
                words.add(pending);
                pending = "";
            }
        }
        if (!pending.isEmpty()) {
            if (words.isEmpty()) {
                return false;
            }
            words.set(words.size() - 1, words.get(words.size() - 1) + pending);
        }
        List<List<Hit>> found = new ArrayList<>();
        for (String word : words) {
            found.add(hits(analysis, word).stream().filter(h -> h.score >= MIN_SCORE).toList());
        }
        return found.get(0).stream().anyMatch(first -> continuesRow(found, 1, first));
    }

    /** One printed line read character by character: its text (a space where the gap is wide) and how clearly it read. */
    record ReadLine(String text, double score, Box area) {
    }

    /**
     * Every line as plain text, each character the one it most resembles. This
     * is a best guess for showing people what was seen; checks against known
     * values use {@link #score} and {@link #wordsInRow}, which are far stricter.
     */
    static List<ReadLine> readLines(Analysis analysis) {
        List<ReadLine> out = new ArrayList<>();
        for (List<Glyph> line : analysis.lines) {
            if (line.isEmpty()) {
                continue;
            }
            int[] heights = line.stream().mapToInt(g -> g.box.height()).sorted().toArray();
            int tall = heights[Math.min(heights.length - 1, heights.length * 4 / 5)];
            StringBuilder text = new StringBuilder();
            Glyph previous = null;
            for (Glyph g : line) {
                if (previous != null && g.box.x0() - previous.box.x1() > 0.35 * tall) {
                    text.append(' ');
                }
                text.append(g.best);
                previous = g;
            }
            double score = line.stream().mapToDouble(g -> g.bestScore).average().orElse(0);
            Box first = line.get(0).box;
            Box last = line.get(line.size() - 1).box;
            int top = line.stream().mapToInt(g -> g.box.y0()).min().orElse(first.y0());
            int bottom = line.stream().mapToInt(g -> g.box.y1()).max().orElse(first.y1());
            out.add(new ReadLine(text.toString(), score, new Box(first.x0(), top, last.x1(), bottom)));
        }
        return out;
    }

    private static boolean continuesRow(List<List<Hit>> found, int index, Hit previous) {
        if (index == found.size()) {
            return true;
        }
        for (Hit next : found.get(index)) {
            int h = Math.max(previous.height, next.height);
            boolean sameRow = Math.abs(next.centerY - previous.centerY) < 0.5 * h;
            int gap = next.x0 - previous.x1;
            if (sameRow && gap > -0.3 * h && gap < 2.5 * h && continuesRow(found, index + 1, next)) {
                return true;
            }
        }
        return false;
    }

    /** One place a line's characters match an expected string: how well, what was read, and where. */
    private record Hit(double score, String seen, int x0, int x1, double centerY, int height) {
    }

    /** Every run of glyphs in which each one looks enough like the expected character. */
    private static List<Hit> hits(Analysis analysis, String expected) {
        List<Hit> hits = new ArrayList<>();
        for (List<Glyph> line : analysis.lines) {
            for (int start = 0; start + expected.length() <= line.size(); start++) {
                double total = 0;
                boolean every = true;
                for (int i = 0; i < expected.length() && every; i++) {
                    Glyph g = line.get(start + i);
                    double want = g.scores.getOrDefault(expected.charAt(i), 0.0);
                    every = want >= g.bestScore - MARGIN;
                    total += want;
                }
                if (every) {
                    List<Glyph> run = line.subList(start, start + expected.length());
                    String seen = run.stream().map(g -> String.valueOf(g.best)).reduce("", String::concat);
                    double centerY = run.stream().mapToDouble(g -> g.box.centerY()).average().orElse(0);
                    int height = run.stream().mapToInt(g -> g.box.height()).max().orElse(1);
                    hits.add(new Hit(total / expected.length(), seen, run.get(0).box.x0(),
                            run.get(run.size() - 1).box.x1(), centerY, height));
                }
            }
        }
        return hits;
    }

    // ---- Finding printed characters ------------------------------------------

    private static final class Glyph {
        final Box box;
        final float[] cell;
        final Map<Character, Double> scores = new HashMap<>();
        char best = '?';
        double bestScore = -1;

        Glyph(Box box, float[] cell) {
            this.box = box;
            this.cell = cell;
        }
    }

    private static List<List<Glyph>> textLines(Raster p, int[] mean, Box display, double contrast, boolean mixedCase) {
        int w = p.width;
        int h = p.height;
        boolean[] raw = new boolean[w * h];
        for (int i = 0; i < raw.length; i++) {
            int x = i % w;
            int y = i / w;
            boolean inDisplay = display != null && x >= display.x0() && x < display.x1() && y >= display.y0() && y < display.y1();
            raw[i] = !inDisplay && p.lum[i] < mean[i] - Math.max(10, mean[i] * contrast);
        }
        // Majority of the 3x3 neighbourhood: removes speckle noise, keeps strokes.
        boolean[] ink = new boolean[w * h];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        n += raw[(y + dy) * w + x + dx] ? 1 : 0;
                    }
                }
                ink[y * w + x] = n >= 5 || (raw[y * w + x] && n >= 3);
            }
        }
        int minH = Math.max(6, h / 120);
        int maxH = Math.max(minH + 1, h / 6);
        List<Box> boxes = new ArrayList<>();
        List<Box> fragments = new ArrayList<>();
        for (Blob b : Raster.blobs(ink, w, h, 3)) {
            Box bx = b.box();
            boolean sized = bx.height() >= minH && bx.height() <= maxH && bx.width() <= bx.height() * 4;
            if (sized) {
                boxes.add(bx);
            } else if (bx.height() < minH && bx.width() < maxH) {
                fragments.add(bx);
            }
        }
        boxes.sort(Comparator.comparingInt(Box::x0));
        // Chain boxes into lines: similar height, overlapping vertically, close horizontally.
        List<List<Box>> lines = new ArrayList<>();
        for (Box bx : boxes) {
            List<Box> home = null;
            for (List<Box> line : lines) {
                Box last = line.get(line.size() - 1);
                double overlap = Math.min(last.y1(), bx.y1()) - Math.max(last.y0(), bx.y0());
                double ratio = (double) Math.max(last.height(), bx.height()) / Math.min(last.height(), bx.height());
                int gap = bx.x0() - last.x1();
                if (overlap >= 0.5 * Math.min(last.height(), bx.height()) && ratio <= 1.7 && gap <= 1.6 * last.height() && gap > -last.width()) {
                    home = line;
                    break;
                }
            }
            if (home == null) {
                home = new ArrayList<>();
                lines.add(home);
            }
            home.add(bx);
        }
        List<List<Glyph>> out = new ArrayList<>();
        for (List<Box> line : lines) {
            if (line.size() < 2) {
                continue;
            }
            int[] heights = line.stream().mapToInt(Box::height).sorted().toArray();
            int lineHeight = heights[heights.length / 2];
            int capHeight = heights[Math.min(heights.length - 1, heights.length * 4 / 5)];
            line = attachFragments(line, fragments, lineHeight);
            List<Glyph> glyphs = new ArrayList<>();
            for (Box bx : line) {
                if (bx.height() < 0.6 * lineHeight) {
                    continue; // punctuation: '-', ':', '.'
                }
                // Characters printed close together can touch; split boxes that are too wide for one. Judge width
                // against the tall letters: in mixed case the typical height is the short lower-case one.
                glyphs.addAll(glyphsIn(p, mean, bx, capHeight, mixedCase));
            }
            if (glyphs.size() >= 3) {
                out.add(glyphs);
                if (LcdReader.trace != null) {
                    trace("line", glyphs.get(0).box + " .. " + glyphs.size() + " glyphs: "
                            + glyphs.stream().map(g -> g.scores.entrySet().stream().sorted((x, y) -> Double.compare(y.getValue(), x.getValue()))
                                    .limit(4).map(e -> e.getKey() + String.format("%.2f", e.getValue())).reduce("", (x, y) -> x + y + " ")).toList());
                }
            }
        }
        return out;
    }

    /**
     * One box from a text line as one or more characters. A box much wider than
     * a character is split; in mixed case, letters like m and W are naturally
     * wide, so a wide box is read both whole and split, and whichever matches
     * the templates better wins (touching digits like "58" split, an "m" stays whole).
     */
    private static List<Glyph> glyphsIn(Raster p, int[] mean, Box bx, int capHeight, boolean mixedCase) {
        int parts = (int) Math.round(bx.width() / (capHeight * 0.62));
        boolean wide = bx.width() > capHeight * 1.05 && parts >= 2;
        if (!wide) {
            Glyph whole = glyph(p, mean, bx, mixedCase);
            // Two narrow letters touching ("la", "ri") look like one poor match: try splitting where they meet.
            if (mixedCase && whole.bestScore < 0.6 && bx.width() > capHeight * 0.5) {
                int cut = faintestColumn(p, mean, bx);
                if (cut > 0) {
                    Glyph left = glyph(p, mean, inkOf(p, mean, new Box(bx.x0(), bx.y0(), cut, bx.y1())), true);
                    Glyph right = glyph(p, mean, inkOf(p, mean, new Box(cut, bx.y0(), bx.x1(), bx.y1())), true);
                    if (Math.min(left.bestScore, right.bestScore) > whole.bestScore + 0.08) {
                        return List.of(left, right);
                    }
                }
            }
            return List.of(whole);
        }
        List<Glyph> split = evenSplit(p, mean, bx, parts, mixedCase);
        if (!mixedCase) {
            return split;
        }
        // Letters differ in width ("LA" overlapping in capitals is a narrow L and a wide A), so an even cut can land
        // mid-letter: one letter fewer or more, and a cut where the ink is faintest, are tried too; the best read wins.
        List<List<Glyph>> options = new ArrayList<>(List.of(split, evenSplit(p, mean, bx, parts + 1, true)));
        if (parts > 2) {
            options.add(evenSplit(p, mean, bx, parts - 1, true));
        }
        int cut = faintestColumn(p, mean, bx);
        if (cut > 0) {
            options.add(List.of(glyph(p, mean, inkOf(p, mean, new Box(bx.x0(), bx.y0(), cut, bx.y1())), true),
                    glyph(p, mean, inkOf(p, mean, new Box(cut, bx.y0(), bx.x1(), bx.y1())), true)));
        }
        List<Glyph> best = split;
        for (List<Glyph> option : options) {
            if (averageScore(option) > averageScore(best)) {
                best = option;
            }
        }
        if (bx.width() > capHeight * 1.9) {
            return best;
        }
        Glyph whole = glyph(p, mean, bx, mixedCase);
        return averageScore(best) > whole.bestScore + 0.03 ? best : List.of(whole);
    }

    private static List<Glyph> evenSplit(Raster p, int[] mean, Box bx, int parts, boolean mixedCase) {
        List<Glyph> split = new ArrayList<>();
        for (int i = 0; i < parts; i++) {
            split.add(glyph(p, mean, new Box(bx.x0() + bx.width() * i / parts, bx.y0(), bx.x0() + bx.width() * (i + 1) / parts,
                    bx.y1()), mixedCase));
        }
        return split;
    }

    private static double averageScore(List<Glyph> glyphs) {
        return glyphs.stream().mapToDouble(g -> g.bestScore).average().orElse(0);
    }

    /** The box shrunk to the ink inside it, so a short letter cut from a tall one ("a" from "la") is framed on its own. */
    private static Box inkOf(Raster p, int[] mean, Box bx) {
        int x0 = bx.x1();
        int y0 = bx.y1();
        int x1 = bx.x0();
        int y1 = bx.y0();
        for (int y = bx.y0(); y < bx.y1(); y++) {
            for (int x = bx.x0(); x < bx.x1(); x++) {
                int i = y * p.width + x;
                if (p.lum[i] < mean[i] - Math.max(10, mean[i] * CONTRASTS[0])) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x + 1);
                    y1 = Math.max(y1, y + 1);
                }
            }
        }
        return x1 - x0 >= 2 && y1 - y0 >= 4 ? new Box(x0, y0, x1, y1) : bx;
    }

    /** The column with the least ink in the middle half of the box, where two touching letters most likely meet; -1 if none. */
    private static int faintestColumn(Raster p, int[] mean, Box bx) {
        int from = bx.x0() + bx.width() / 4;
        int to = bx.x1() - bx.width() / 4;
        int best = -1;
        long bestInk = Long.MAX_VALUE;
        for (int x = from; x < to; x++) {
            long ink = 0;
            for (int y = bx.y0(); y < bx.y1(); y++) {
                int i = y * p.width + x;
                ink += Math.max(0, mean[i] - p.lum[i]);
            }
            if (ink < bestInk) {
                bestInk = ink;
                best = x;
            }
        }
        return best > bx.x0() + 2 && best < bx.x1() - 2 ? best : -1;
    }

    private static Glyph glyph(Raster p, int[] mean, Box box, boolean mixedCase) {
        Glyph g = new Glyph(box, cell(p, mean, box));
        classify(g, mixedCase);
        return g;
    }

    /**
     * Thin strokes often break off in a photo: the flag of a 5, the base of a 2,
     * the bar of a 7. Pieces lying within a character's column, inside the line's
     * height, are joined back onto that character.
     */
    private static List<Box> attachFragments(List<Box> line, List<Box> fragments, int lineHeight) {
        int top = line.stream().mapToInt(Box::y0).min().orElse(0);
        int bottom = line.stream().mapToInt(Box::y1).max().orElse(0);
        List<Box> merged = new ArrayList<>(line);
        List<Box> pieces = new ArrayList<>(fragments);
        pieces.addAll(line.stream().filter(b -> b.height() < 0.6 * lineHeight).toList());
        for (Box f : pieces) {
            if (f.centerY() < top - 0.15 * lineHeight || f.centerY() > bottom + 0.15 * lineHeight) {
                continue;
            }
            for (int i = 0; i < merged.size(); i++) {
                Box g = merged.get(i);
                if (g == f || g.height() < 0.6 * lineHeight) {
                    continue;
                }
                int overlap = Math.min(g.x1(), f.x1()) - Math.max(g.x0(), f.x0());
                boolean inside = overlap >= 0.7 * Math.max(1, f.width());
                boolean tall = f.y1() <= g.y0() + 0.5 * g.height() || f.y0() >= g.y0() + 0.5 * g.height();
                if (inside && tall) {
                    merged.set(i, new Box(Math.min(g.x0(), f.x0()), Math.min(g.y0(), f.y0()),
                            Math.max(g.x1(), f.x1()), Math.max(g.y1(), f.y1())));
                    merged.remove(f);
                    break;
                }
            }
        }
        merged.sort(Comparator.comparingInt(Box::x0));
        return merged;
    }

    /** The glyph as darkness values in a square cell, scaled by height and centred, so shapes compare fairly. */
    private static float[] cell(Raster p, int[] mean, Box box) {
        float[] cell = new float[CELL * CELL];
        double scale = (double) CELL / box.height();
        double offsetX = (CELL - box.width() * scale) / 2;
        int lo = 255;
        int hi = 0;
        for (int y = box.y0(); y < box.y1(); y++) {
            for (int x = box.x0(); x < box.x1(); x++) {
                int v = p.lum[y * p.width + x];
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
            }
        }
        int background = Math.max(hi, mean[(int) box.centerY() * p.width + (int) box.centerX()]);
        double range = Math.max(1, background - lo);
        for (int cy = 0; cy < CELL; cy++) {
            for (int cx = 0; cx < CELL; cx++) {
                double sx = box.x0() + (cx - offsetX + 0.5) / scale;
                double sy = box.y0() + (cy + 0.5) / scale;
                int ix = (int) sx;
                int iy = (int) sy;
                if (ix < box.x0() || ix >= box.x1() || iy < box.y0() || iy >= box.y1()) {
                    continue;
                }
                double dark = (background - p.lum[iy * p.width + ix]) / range;
                cell[cy * CELL + cx] = (float) Math.max(0, Math.min(1, dark));
            }
        }
        return cell;
    }

    /**
     * Scores the glyph against every character. In mixed case (printed names),
     * a letter's score is the better of its two cases; otherwise (meters) the
     * narrow faces stamped on meters count too.
     */
    private static void classify(Glyph g, boolean mixedCase) {
        for (Map.Entry<Character, List<float[]>> e : templates().entrySet()) {
            double best = -1;
            for (float[] t : e.getValue()) {
                best = Math.max(best, correlation(g.cell, t));
            }
            if (mixedCase && Character.isLetter(e.getKey())) {
                for (float[] t : lowerTemplates().get(Character.toLowerCase(e.getKey()))) {
                    best = Math.max(best, correlation(g.cell, t));
                }
            }
            if (!mixedCase) {
                for (float[] t : narrowTemplates().get(e.getKey())) {
                    best = Math.max(best, correlation(g.cell, t));
                }
            }
            g.scores.put(e.getKey(), best);
            if (best > g.bestScore) {
                g.bestScore = best;
                g.best = e.getKey();
            }
        }
    }

    private static double correlation(float[] a, float[] b) {
        double ma = 0;
        double mb = 0;
        for (int i = 0; i < a.length; i++) {
            ma += a[i];
            mb += b[i];
        }
        ma /= a.length;
        mb /= b.length;
        double num = 0;
        double da = 0;
        double db = 0;
        for (int i = 0; i < a.length; i++) {
            double x = a[i] - ma;
            double y = b[i] - mb;
            num += x * y;
            da += x * x;
            db += y * y;
        }
        return da == 0 || db == 0 ? 0 : num / Math.sqrt(da * db);
    }

    /** Every letter and digit drawn in a few common sans-serif fonts, plain and bold, as cells. */
    private static synchronized Map<Character, List<float[]>> templates() {
        if (templates == null) {
            templates = renderAll(ALPHABET, commonFonts());
        }
        return templates;
    }

    private static synchronized Map<Character, List<float[]>> lowerTemplates() {
        if (lowerTemplates == null) {
            lowerTemplates = renderAll(LOWER, commonFonts());
        }
        return lowerTemplates;
    }

    private static synchronized Map<Character, List<float[]>> narrowTemplates() {
        if (narrowTemplates == null) {
            narrowTemplates = renderAll(ALPHABET, narrowFonts());
        }
        return narrowTemplates;
    }

    private static Map<Character, List<float[]>> renderAll(String characters, List<Font> fonts) {
        Map<Character, List<float[]>> map = new HashMap<>();
        for (char ch : characters.toCharArray()) {
            List<float[]> cells = new ArrayList<>();
            for (Font font : fonts) {
                // A sharp version, and one drawn at the size characters appear in phone photos, blur included.
                cells.add(render(ch, font.deriveFont(64f)));
                cells.add(render(ch, font.deriveFont(15f)));
            }
            map.put(ch, cells);
        }
        return map;
    }

    private record RollingTemplate(int digit, float[] cell) {
    }

    private static List<RollingTemplate> rollingTemplates;

    /**
     * Each digit rolling over into the next, as a wheel window shows it part-way:
     * the two digits stacked as on the drum, cut at several points between them.
     */
    private static synchronized List<RollingTemplate> rollingTemplates() {
        if (rollingTemplates == null) {
            List<RollingTemplate> list = new ArrayList<>();
            List<Font> fonts = new ArrayList<>(commonFonts());
            fonts.addAll(narrowFonts());
            for (Font font : fonts) {
                for (double spacing : new double[] {1.35, 1.6}) {
                    for (int d = 0; d < 10; d++) {
                        for (double t = 0.15; t < 0.9; t += 0.1) {
                            float[] cell = renderRolling(d, font, spacing, t);
                            if (cell != null) {
                                list.add(new RollingTemplate(d, cell));
                            }
                        }
                    }
                }
            }
            rollingTemplates = list;
        }
        return rollingTemplates;
    }

    /** Digit {@code d} above the next one, {@code spacing} digit-heights apart, seen through a digit-high window {@code t} of the way down. */
    private static float[] renderRolling(int d, Font font, double spacing, double t) {
        int size = 96;
        int height = 320;
        BufferedImage img = new BufferedImage(size, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, size, height);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(font);
        g2.setColor(Color.BLACK);
        FontMetrics fm = g2.getFontMetrics();
        int digitHeight = fm.getAscent() * 72 / 100;
        int baseline = 40 + digitHeight;
        int step = (int) Math.round(spacing * digitHeight);
        String upper = String.valueOf((char) ('0' + d));
        String lower = String.valueOf((char) ('0' + (d + 1) % 10));
        g2.drawString(upper, (size - fm.stringWidth(upper)) / 2, baseline);
        g2.drawString(lower, (size - fm.stringWidth(lower)) / 2, baseline + step);
        g2.dispose();
        Raster r = Raster.of(img, height);
        int x0 = size;
        int x1 = -1;
        int top = height;
        for (int i = 0; i < r.lum.length; i++) {
            if (r.lum[i] < 128) {
                x0 = Math.min(x0, i % size);
                x1 = Math.max(x1, i % size);
                top = Math.min(top, i / size);
            }
        }
        if (x1 < 0) {
            return null;
        }
        // The digit's real height, from where its ink starts to the baseline.
        int inkHeight = baseline - top;
        int y0 = top + (int) Math.round(t * step);
        int[] white = new int[r.lum.length];
        java.util.Arrays.fill(white, 255);
        return cell(r, white, new Box(x0, y0, x1 + 1, Math.min(height, y0 + inkHeight)));
    }

    private static List<Font> commonFonts() {
        List<Font> fonts = new ArrayList<>();
        for (String family : FONTS) {
            fonts.add(new Font(family, Font.PLAIN, 64));
            fonts.add(new Font(family, Font.BOLD, 64));
        }
        return fonts;
    }

    private static List<Font> narrowFonts() {
        List<Font> fonts = new ArrayList<>();
        Set<String> installed = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT));
        for (String family : NARROW_FONTS) {
            if (installed.contains(family)) {
                fonts.add(new Font(family, Font.PLAIN, 64));
                fonts.add(new Font(family, Font.BOLD, 64));
            }
        }
        AffineTransform condensed = AffineTransform.getScaleInstance(0.78, 1);
        fonts.add(new Font("Arial", Font.PLAIN, 64).deriveFont(condensed));
        fonts.add(new Font("Arial", Font.BOLD, 64).deriveFont(condensed));
        return fonts;
    }

    private static float[] render(char ch, Font font) {
        BufferedImage img = new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, 96, 96);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(font);
        g2.setColor(Color.BLACK);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(String.valueOf(ch), (96 - fm.charWidth(ch)) / 2, (96 + fm.getAscent() - fm.getDescent()) / 2);
        g2.dispose();
        Raster r = Raster.of(img, 96);
        int x0 = 96, y0 = 96, x1 = -1, y1 = -1;
        for (int i = 0; i < r.lum.length; i++) {
            if (r.lum[i] < 128) {
                x0 = Math.min(x0, i % 96);
                y0 = Math.min(y0, i / 96);
                x1 = Math.max(x1, i % 96);
                y1 = Math.max(y1, i / 96);
            }
        }
        int[] white = new int[r.lum.length];
        java.util.Arrays.fill(white, 255);
        return cell(r, white, new Box(x0, y0, x1 + 1, y1 + 1));
    }

    private static void trace(String step, Object value) {
        if (LcdReader.trace != null) {
            LcdReader.trace.accept(step, value);
        }
    }
}
