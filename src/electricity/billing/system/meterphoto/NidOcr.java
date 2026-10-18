package electricity.billing.system.meterphoto;

import electricity.billing.system.model.NidRecord;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an NID card with Tesseract OCR and checks it against the NID records.
 *
 * One OCR pass misses things on some photos and catches them on others, so the
 * card is read several ways at once: as it is and sharpened, with automatic
 * layout and as scattered text, enlarged when small, and turned if no NID
 * number appears the right way up. All the lines read are pooled. The NID
 * number must equal a record's exactly, then that record's date of birth must
 * appear exactly and its English name closely (OCR may slip a letter, such as
 * "S. MW. ARIFUL ISLAM"; a different name is far off).
 */
final class NidOcr {

    private static final String[] MONTHS = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    /** A number as OCR prints it: digits, look-alike letters between digits, spaces between groups ("867 322 8071"). */
    private static final Pattern NUMBER = Pattern.compile("[0-9$](?:[0-9OoIlSs$B ]*[0-9])?");
    private static final Pattern WORD_DATE = Pattern.compile(
            "([0-9OoIl]{1,3})\\s*[-./]?\\s*([A-Za-z]{3,9})\\.?\\s*[-./,]?\\s*([0-9OoIlSs$]{4})");
    private static final Pattern NUMERIC_DATE = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})");
    /** How close the name must be: at most this share of its letters may be wrong. */
    private static final double NAME_SLIPS = 0.15;
    /** Text is easiest for Tesseract with capitals around 30 pixels; card photos are scaled to about this size. */
    private static final int MIN_SIDE = 1500;
    private static final int MAX_SIDE = 3000;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "nid-ocr");
        t.setDaemon(true);
        return t;
    });

    private NidOcr() {
    }

    /** The lines a first round of passes read, and the photo in grey, the right way up and level, for {@link #secondRound}. */
    record Reading(List<String> lines, BufferedImage upright) {
    }

    /** The card read a few quick ways; most photos are fully read by this alone. */
    static Reading read(BufferedImage photo) {
        BufferedImage plain = gray(photo);
        // The slant is measured at the photo's own size: enlarging a small photo softens the strokes it goes by.
        int angleSize = Math.min(1200, Math.max(photo.getWidth(), photo.getHeight()));
        BufferedImage gray = levelled(plain, angleSize);
        BufferedImage sharp = sharpened(gray);
        List<String> lines = passes(List.of(new Pass(gray, 3), new Pass(gray, 11), new Pass(sharp, 11), new Pass(sharp, 4)));
        if (bestNumber(lines) != null) {
            return new Reading(lines, gray);
        }
        // No NID number the right way up: the photo may be sideways or upside down.
        for (int quarter : new int[] {1, 3, 2}) {
            BufferedImage turned = levelled(turned(plain, quarter), angleSize);
            List<String> more = passes(List.of(new Pass(turned, 11), new Pass(sharpened(turned), 11)));
            if (bestNumber(more) != null) {
                List<String> all = new ArrayList<>(lines);
                all.addAll(more);
                all.addAll(passes(List.of(new Pass(turned, 3), new Pass(sharpened(turned), 4))));
                return new Reading(all, turned);
            }
        }
        return new Reading(lines, gray);
    }

    /**
     * More ways of reading, for a card the first round didn't fully match:
     * enlarged further (small or blurred print) and in black and white (glare,
     * shadows, faded or patterned backgrounds).
     */
    static List<String> secondRound(BufferedImage upright) {
        BufferedImage bigger = enlarged(upright, 1.6);
        BufferedImage bw = blackAndWhite(upright);
        return passes(List.of(new Pass(bigger, 11), new Pass(sharpened(bigger), 11), new Pass(bigger, 6), new Pass(bw, 11),
                new Pass(bw, 4), new Pass(blackAndWhite(bigger), 11)));
    }

    /** What the lines read show about the card, checked against the records. */
    static IdCardReader.Findings check(List<String> lines, List<NidRecord> records) {
        IdCardReader.CardText read = new IdCardReader.CardText(bestNumber(lines), bestName(lines), bestDate(lines));
        List<String> numbers = numbers(lines);
        List<NidRecord> found = new ArrayList<>();
        for (NidRecord r : records) {
            if (numbers.stream().anyMatch(n -> sameNumber(n, r.nidNo()))) {
                found.add(r);
            }
        }
        if (found.size() != 1) {
            return new IdCardReader.Findings(null, found.size() > 1, false, false, read);
        }
        NidRecord record = found.get(0);
        boolean name = nameAppears(lines, record.nameEn());
        boolean birth = dates(lines).contains(record.dateOfBirth());
        return new IdCardReader.Findings(record, false, name, birth, read);
    }

    private record Pass(BufferedImage image, int psm) {
    }

    private static List<String> passes(List<Pass> passes) {
        List<Future<List<String>>> running = new ArrayList<>();
        for (Pass p : passes) {
            running.add(POOL.submit(() -> TesseractOcr.read(p.image(), p.psm())));
        }
        List<String> lines = new ArrayList<>();
        for (Future<List<String>> f : running) {
            try {
                lines.addAll(f.get());
            } catch (Exception e) {
                // That pass failed; the others may still have read the card.
            }
        }
        return lines;
    }

    // ---- NID number ----------------------------------------------------------

    /** Every run of 10 to 17 digits read, with look-alike letters put right. */
    static List<String> numbers(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            Matcher m = NUMBER.matcher(line);
            while (m.find()) {
                String digits = asDigits(m.group().replace(" ", ""));
                if (digits.length() >= 10 && digits.length() <= 17) {
                    out.add(digits);
                }
            }
        }
        return out;
    }

    /**
     * True when a number read is this NID number: equal, the 17-digit form
     * ending in it, or it with a stray digit beside it (a run of a length no NID
     * number has: 11, 12, 14 to 16).
     */
    static boolean sameNumber(String read, String nid) {
        if (read.equals(nid) || (read.length() == 17 && read.endsWith(nid))) {
            return true;
        }
        boolean nidLength = read.length() == 10 || read.length() == 13 || read.length() == 17;
        return !nidLength && read.length() <= nid.length() + 2 && read.contains(nid);
    }

    /** The NID number most often read (10, 13 or 17 digits), preferring lines labelled "NID" or "ID NO". */
    static String bestNumber(List<String> lines) {
        Map<String, Integer> votes = new HashMap<>();
        for (String line : lines) {
            boolean labelled = line.toUpperCase(Locale.ROOT).matches(".*\\b(NID|ID\\s*NO|NO)\\b.*");
            for (String n : numbers(List.of(line))) {
                if (n.length() == 10 || n.length() == 13 || n.length() == 17) {
                    votes.merge(n, labelled ? 3 : 2, Integer::sum);
                }
            }
        }
        return winner(votes);
    }

    // ---- Name ----------------------------------------------------------------

    /** True when some line holds the name, allowing a few OCR slips; initials ("S. M.") may be missing. */
    static boolean nameAppears(List<String> lines, String name) {
        String wanted = letters(name);
        String withoutInitials = IdCardReader.withoutInitials(name);
        for (String line : lines) {
            String seen = letters(line);
            if (closeEnough(wanted, seen) || (withoutInitials != null && closeEnough(letters(withoutInitials), seen))) {
                return true;
            }
        }
        return false;
    }

    private static boolean closeEnough(String wanted, String seen) {
        return wanted.length() >= 3 && slips(wanted, seen) <= Math.floor(wanted.length() * NAME_SLIPS);
    }

    /** The fewest letter changes that turn {@code wanted} into some stretch of {@code seen}. */
    static int slips(String wanted, String seen) {
        int[] previous = new int[seen.length() + 1];
        int[] current = new int[seen.length() + 1];
        for (int i = 1; i <= wanted.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= seen.length(); j++) {
                int cost = wanted.charAt(i - 1) == seen.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return Arrays.stream(previous).min().orElse(wanted.length());
    }

    /** Words printed on the card around the name, so they aren't taken for one. */
    private static final java.util.Set<String> CARD_WORDS = java.util.Set.of("GOVERNMENT", "PEOPLE", "PEOPLES", "REPUBLIC",
            "BANGLADESH", "NATIONAL", "CARD", "DATE", "BIRTH", "NID", "ID", "NO", "NAME", "SIGNATURE", "OF", "THE");
    private static final Pattern NAME_WORD = Pattern.compile("[A-Z][A-Za-z]*\\.?");

    /**
     * The name after a "Name" label, on the same line or the next one, as most
     * often read. When no label was read (it's small print and is often missed),
     * the line that looks most like a name: two to six capitalised words or
     * initials and no card wording, such as "S. M. ARIFUL ISLAM" or "Tajul Islam".
     */
    static String bestName(List<String> lines) {
        String labelled = labelledName(lines);
        if (labelled != null || !looksLikeNidCard(lines)) {
            return labelled;
        }
        Map<String, Integer> votes = new HashMap<>();
        Map<String, String> spelling = new HashMap<>();
        for (String line : lines) {
            String value = line.replaceFirst("^[^A-Za-z]+", "").replaceFirst("[^A-Za-z.]+$", "").trim();
            String[] words = value.split("\\s+");
            boolean nameLike = words.length >= 2 && words.length <= 6 && letters(value).length() >= 5
                    && Arrays.stream(words).allMatch(w -> NAME_WORD.matcher(w).matches())
                    && Arrays.stream(words).noneMatch(w -> CARD_WORDS.contains(letters(w)));
            if (nameLike) {
                votes.merge(letters(value), 1, Integer::sum);
                spelling.putIfAbsent(letters(value), value.replaceAll("\\s+", " "));
            }
        }
        // A candidate counts every line it appears in, so a clean read beats the same name with junk after it.
        String best = null;
        long bestCount = 1;
        for (String candidate : votes.keySet()) {
            long count = lines.stream().filter(l -> letters(l).contains(candidate)).count();
            if (count > bestCount || (count == bestCount && best != null && candidate.length() < best.length())) {
                best = candidate;
                bestCount = count;
            }
        }
        return best == null ? null : spelling.get(best);
    }

    /** True when something on the photo says it's an NID card: an NID-length number, or the card's own wording. */
    private static boolean looksLikeNidCard(List<String> lines) {
        return bestNumber(lines) != null || lines.stream().map(l -> l.toUpperCase(Locale.ROOT))
                .anyMatch(l -> l.contains("NATIONAL ID") || l.contains("BIRTH") || l.contains("BANGLADESH") || l.matches(".*\\bNID\\b.*"));
    }

    private static String labelledName(List<String> lines) {
        Map<String, Integer> votes = new HashMap<>();
        Map<String, String> spelling = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = Pattern.compile("^\\W*N\\s*a\\s*m\\s*e\\b\\W*(.*)$", Pattern.CASE_INSENSITIVE).matcher(lines.get(i));
            if (!m.matches()) {
                continue;
            }
            String value = m.group(1).trim();
            if (letters(value).length() < 3 && i + 1 < lines.size()) {
                value = lines.get(i + 1).trim();
            }
            value = value.replaceAll("[^A-Za-z. ]", "").replaceAll("\\s+", " ").trim();
            if (letters(value).length() >= 3) {
                votes.merge(letters(value), 1, Integer::sum);
                spelling.merge(letters(value), value, (a, b) -> a.length() >= b.length() ? a : b);
            }
        }
        String best = winner(votes);
        return best == null ? null : spelling.get(best);
    }

    // ---- Date of birth -------------------------------------------------------

    /** Every date read: "05 Feb 1976", "1Dec 1990", "05/02/1976" (day first). */
    static List<LocalDate> dates(List<String> lines) {
        List<LocalDate> out = new ArrayList<>();
        for (String line : lines) {
            Matcher m = WORD_DATE.matcher(line);
            while (m.find()) {
                int month = month(m.group(2));
                LocalDate d = month < 0 ? null : date(asDigits(m.group(1)), month + 1, asDigits(m.group(3)));
                if (d != null) {
                    out.add(d);
                }
            }
            Matcher n = NUMERIC_DATE.matcher(line);
            while (n.find()) {
                LocalDate d = date(n.group(1), Integer.parseInt(n.group(2)), n.group(3));
                if (d != null) {
                    out.add(d);
                }
            }
        }
        return out;
    }

    /** The date most often read, preferring lines labelled "Birth", as "05 Feb 1976". */
    static String bestDate(List<String> lines) {
        Map<String, Integer> votes = new HashMap<>();
        for (String line : lines) {
            boolean labelled = line.toUpperCase(Locale.ROOT).contains("BIRTH");
            for (LocalDate d : dates(List.of(line))) {
                votes.merge(DAY.format(d), labelled ? 3 : 2, Integer::sum);
            }
        }
        return winner(votes);
    }

    private static LocalDate date(String day, int month, String year) {
        try {
            int d = Integer.parseInt(day.replaceFirst("^0+(?=\\d)", ""));
            int y = Integer.parseInt(year);
            if (y < 1900 || y > 2100 || d < 1 || d > 31 || month < 1 || month > 12) {
                return null;
            }
            return LocalDate.of(y, month, d);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The month a word names: its first three letters, one of which may be misread if that leaves only one month. */
    private static int month(String word) {
        String w = word.toUpperCase(Locale.ROOT).substring(0, 3);
        for (int m = 0; m < MONTHS.length; m++) {
            if (MONTHS[m].equals(w)) {
                return m;
            }
        }
        int found = -1;
        for (int m = 0; m < MONTHS.length; m++) {
            int same = 0;
            for (int i = 0; i < 3; i++) {
                same += MONTHS[m].charAt(i) == w.charAt(i) ? 1 : 0;
            }
            if (same == 2) {
                if (found >= 0) {
                    return -1;
                }
                found = m;
            }
        }
        return found;
    }

    // ---- Helpers -------------------------------------------------------------

    private static String winner(Map<String, Integer> votes) {
        return votes.entrySet().stream().max(Map.Entry.<String, Integer>comparingByValue()
                .thenComparing(e -> e.getKey().length())).map(Map.Entry::getKey).orElse(null);
    }

    private static String asDigits(String s) {
        return s.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1').replace('S', '5').replace('s', '5')
                .replace('$', '8').replace('B', '8');
    }

    private static String letters(String s) {
        return s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
    }

    /**
     * The photo turned so its lines of print run level, if it was taken at a
     * slant of more than a degree (up to 20). The slant is measured with the
     * image at most {@code measureSize} pixels on its longer side.
     */
    private static BufferedImage levelled(BufferedImage gray, int measureSize) {
        double angle = Raster.of(gray, measureSize).textAngle(20);
        if (Math.abs(angle) < Math.toRadians(1)) {
            return gray;
        }
        BufferedImage out = new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.rotate(-angle, gray.getWidth() / 2.0, gray.getHeight() / 2.0);
        g.drawImage(gray, 0, 0, null);
        g.dispose();
        return out;
    }

    /** The photo in grey, scaled so the card's print is a size Tesseract reads well. */
    private static BufferedImage gray(BufferedImage photo) {
        int longest = Math.max(photo.getWidth(), photo.getHeight());
        double scale = longest < MIN_SIDE ? Math.min(4.0, (double) MIN_SIDE / longest)
                : longest > MAX_SIDE ? (double) MAX_SIDE / longest : 1.0;
        int w = Math.max(1, (int) Math.round(photo.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(photo.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(photo, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** The image enlarged by {@code factor}, up to 4500 pixels on its longer side. */
    private static BufferedImage enlarged(BufferedImage gray, double factor) {
        double scale = Math.min(factor, 4500.0 / Math.max(gray.getWidth(), gray.getHeight()));
        int w = Math.max(1, (int) Math.round(gray.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(gray.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(gray, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Each point black or white, judged against its surroundings, so glare and shadows don't hide the print. */
    private static BufferedImage blackAndWhite(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        int[] px = gray.getRaster().getPixels(0, 0, w, h, (int[]) null);
        int[] mean = boxBlur(px, w, h, Math.max(8, Math.min(w, h) / 40));
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            out[i] = px[i] < mean[i] - 10 ? 0 : 255;
        }
        BufferedImage bw = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        bw.getRaster().setPixels(0, 0, w, h, out);
        return bw;
    }

    /** Edges sharpened (unsharp mask) and contrast stretched: helps blurred and faded photos. */
    private static BufferedImage sharpened(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        int[] px = gray.getRaster().getPixels(0, 0, w, h, (int[]) null);
        int[] blur = boxBlur(px, w, h, Math.max(1, Math.min(w, h) / 400));
        int[] sharp = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            sharp[i] = px[i] + (int) Math.round(1.5 * (px[i] - blur[i]));
        }
        int[] sorted = sharp.clone();
        Arrays.sort(sorted);
        int lo = sorted[sorted.length / 100];
        int hi = sorted[sorted.length - 1 - sorted.length / 100];
        double range = Math.max(1, hi - lo);
        for (int i = 0; i < sharp.length; i++) {
            sharp[i] = (int) Math.max(0, Math.min(255, Math.round((sharp[i] - lo) * 255 / range)));
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        out.getRaster().setPixels(0, 0, w, h, sharp);
        return out;
    }

    private static int[] boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[px.length];
        int[] out = new int[px.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sum = 0;
                int n = 0;
                for (int dx = -r; dx <= r; dx++) {
                    int xx = x + dx;
                    if (xx >= 0 && xx < w) {
                        sum += px[y * w + xx];
                        n++;
                    }
                }
                tmp[y * w + x] = sum / n;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sum = 0;
                int n = 0;
                for (int dy = -r; dy <= r; dy++) {
                    int yy = y + dy;
                    if (yy >= 0 && yy < h) {
                        sum += tmp[yy * w + x];
                        n++;
                    }
                }
                out[y * w + x] = sum / n;
            }
        }
        return out;
    }

    private static BufferedImage turned(BufferedImage image, int quarterTurns) {
        int w = image.getWidth();
        int h = image.getHeight();
        boolean sideways = quarterTurns % 2 == 1;
        BufferedImage out = new BufferedImage(sideways ? h : w, sideways ? w : h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.translate(out.getWidth() / 2.0, out.getHeight() / 2.0);
        g.rotate(quarterTurns * Math.PI / 2);
        g.translate(-w / 2.0, -h / 2.0);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }
}
