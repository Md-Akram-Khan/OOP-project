package electricity.billing.system.meterphoto;

import electricity.billing.system.common.AppException;
import electricity.billing.system.model.NidRecord;
import java.awt.image.BufferedImage;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks a photo of a Bangladeshi NID card against the dummy NID database.
 *
 * It finds which stored record's NID number is printed on the card, then
 * checks that the same record's English name and date of birth are printed
 * there too. All three must match. The card is read with Tesseract OCR when
 * it's installed ({@link NidOcr}), which copes with most photos; the built-in
 * reader ({@link SerialMatcher}) is used without it, and as a second opinion
 * for whatever Tesseract missed.
 *
 * This only shows that the card matches a stored record. It can't tell a
 * genuine card from a well-made copy, or that the person holding it is its
 * owner, so it is a simulated identity check for the project, not official
 * authentication.
 */
public final class IdCardReader {

    private static final DateTimeFormatter DAY_FIRST = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_FIRST_SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    /**
     * What the card showed. {@code record} is the record whose NID number is on
     * the card (null if none); the flags say which of its details were found.
     * {@code problem} explains a failed check, or is null when everything matched.
     * {@code read} is a best guess at the card's details, for showing the applicant.
     */
    public record Result(NidRecord record, boolean numberFound, boolean nameFound, boolean birthDateFound, String problem,
            CardText read) {
        public boolean verified() {
            return problem == null;
        }
    }

    /**
     * The NID number, English name and date of birth as read from the card, each
     * null if it couldn't be made out. A best guess that may misread characters;
     * it is shown to the applicant, never used to decide a match.
     */
    public record CardText(String nidNo, String name, String dateOfBirth) {
    }

    /** What one way of reading the card found, before it's put into words for the applicant. */
    record Findings(NidRecord record, boolean several, boolean nameFound, boolean birthDateFound, CardText read) {
    }

    private IdCardReader() {
    }

    /**
     * Checks the photo of the card's back is a photo, and not the front's again.
     * Its contents (address, barcode) aren't read.
     *
     * @throws AppException if it isn't
     */
    public static void requireBack(byte[] back, byte[] front) {
        MeterPhotoReader.decode(back);
        if (java.util.Arrays.equals(back, front)) {
            throw new AppException("The back photo is the same as the front. Choose a photo of the back of your NID card.");
        }
    }

    /**
     * @throws AppException if the photo can't be read at all
     */
    public static Result check(byte[] photo, List<NidRecord> records) {
        BufferedImage image = MeterPhotoReader.decode(photo);
        Findings ocr = null;
        if (TesseractOcr.available()) {
            NidOcr.Reading reading = NidOcr.read(image);
            ocr = NidOcr.check(reading.lines(), records);
            // More passes only for what looks like an NID card: a photo of something else would just take longer.
            if (!complete(ocr) && NidOcr.looksLikeNidCard(reading.lines())) {
                List<String> more = new ArrayList<>(reading.lines());
                more.addAll(NidOcr.secondRound(reading.upright()));
                ocr = NidOcr.check(more, records);
            }
            // The built-in reader only helps find the name or date of birth of a record Tesseract has found.
            if (complete(ocr) || ocr.record() == null) {
                return result(ocr, image);
            }
        }
        Findings builtIn;
        try {
            builtIn = builtIn(image, records);
        } catch (AppException e) {
            if (ocr == null) {
                throw e;
            }
            builtIn = null;
        }
        return result(ocr == null ? builtIn : merge(ocr, builtIn), image);
    }

    private static boolean complete(Findings f) {
        return f.record() != null && !f.several() && f.nameFound() && f.birthDateFound();
    }

    /** Tesseract's findings, with whatever the built-in reader also found for the same record. */
    private static Findings merge(Findings ocr, Findings builtIn) {
        if (builtIn == null) {
            return ocr;
        }
        boolean differ = ocr.record() != null && builtIn.record() != null
                && !ocr.record().nidNo().equals(builtIn.record().nidNo());
        NidRecord record = ocr.record() != null ? ocr.record() : builtIn.record();
        boolean name = (ocr.record() == record && ocr.nameFound()) || (builtIn.record() == record && builtIn.nameFound());
        boolean birth = (ocr.record() == record && ocr.birthDateFound())
                || (builtIn.record() == record && builtIn.birthDateFound());
        CardText a = ocr.read();
        CardText b = builtIn.read();
        CardText read = new CardText(a.nidNo() != null ? a.nidNo() : b.nidNo(), a.name() != null ? a.name() : b.name(),
                a.dateOfBirth() != null ? a.dateOfBirth() : b.dateOfBirth());
        return new Findings(record, ocr.several() || builtIn.several() || differ, name, birth, read);
    }

    /** The findings put into words: what matched and, if anything didn't, what to do about it. */
    private static Result result(Findings f, BufferedImage image) {
        String small = smallPhotoAdvice(image);
        CardText read = f.read();
        if (f.several()) {
            return new Result(null, false, false, false,
                    "The card matches more than one NID record. Retake the photo so only your card is in view.", read);
        }
        if (f.record() == null) {
            String problem = read.nidNo() == null
                    ? "The NID number on this card couldn't be read. Retake the photo straight on, in good light, with the "
                            + "whole card in focus."
                    : "The NID number read from this card (" + read.nidNo() + ") isn't in the NID records. If it was misread, "
                            + "retake the photo straight on, in good light, with the whole card in focus.";
            return new Result(null, false, false, false, problem + small, read);
        }
        boolean name = f.nameFound();
        boolean birth = f.birthDateFound();
        if (!name || !birth) {
            return new Result(f.record(), true, name, birth, "The NID number was found, but the " + (!name && !birth
                    ? "name and date of birth" : !name ? "name" : "date of birth") + " on the card "
                    + (!name && !birth ? "don't" : "doesn't") + " match the NID record. "
                    + "Make sure the whole card is in the photo and in focus." + small, read);
        }
        return new Result(f.record(), true, true, true, null, read);
    }

    /**
     * The built-in reader: compares the card's characters with drawn letters.
     *
     * @throws AppException if no printed text is found at all
     */
    private static Findings builtIn(BufferedImage image, List<NidRecord> records) {
        Raster raster = Raster.atLeast(image, 1300, 1600);
        // Level a tilted photo so each printed line can be read as a line.
        double angle = raster.textAngle();
        if (Math.abs(angle) > Math.toRadians(0.4)) {
            raster = raster.rotated(-angle);
        }
        SerialMatcher.Analysis printed = SerialMatcher.analyze(raster, null, true);
        if (printed.isEmpty()) {
            throw new AppException("No printed text could be found in this photo. Take a clear, well-lit photo of the front "
                    + "of your NID card, filling most of the frame.");
        }
        CardText read = NidText.read(printed);
        List<NidRecord> found = new ArrayList<>();
        for (NidRecord r : records) {
            if (SerialMatcher.score(printed, r.nidNo()).matched()) {
                found.add(r);
            }
        }
        // A number found inside a longer matching number is the longer one (e.g. 10-digit inside 17-digit forms).
        List<NidRecord> distinct = new ArrayList<>(found);
        distinct.removeIf(a -> found.stream().anyMatch(b -> b != a && b.nidNo().contains(a.nidNo())
                && b.nidNo().length() > a.nidNo().length()));
        if (distinct.size() != 1) {
            return new Findings(null, distinct.size() > 1, false, false, read);
        }
        NidRecord record = distinct.get(0);
        // Initials ("S. M.") are small and their dots blur into them, so the rest of the name may match without them.
        String withoutInitials = withoutInitials(record.nameEn());
        boolean name = SerialMatcher.wordsInRow(printed, record.nameEn())
                || (withoutInitials != null && SerialMatcher.wordsInRow(printed, withoutInitials));
        // The full date, or at least month and year: small lower-case letters ("ec" in "Dec") blur first, and the
        // NID number and name have already matched this record.
        boolean birth = SerialMatcher.wordsInRow(printed, DAY_FIRST.format(record.dateOfBirth()))
                || SerialMatcher.wordsInRow(printed, DAY_FIRST_SHORT.format(record.dateOfBirth()))
                || SerialMatcher.wordsInRow(printed, MONTH_YEAR.format(record.dateOfBirth()));
        return new Findings(record, false, name, birth, read);
    }

    /** Which OCR engine reads cards, for the server's start-up log. */
    public static String ocrEngine() {
        return TesseractOcr.describe();
    }

    /** Photos smaller than this (longer side, in pixels) are too small for a card's small print to be read reliably. */
    private static final int SMALL_PHOTO = 1000;

    /**
     * Extra advice for a refused photo that is too small: on a screenshot or a
     * compressed copy the name can be only a few pixels tall, and no amount of
     * enlarging brings the letters back. Empty for a photo of a reasonable size.
     */
    private static String smallPhotoAdvice(BufferedImage image) {
        if (Math.max(image.getWidth(), image.getHeight()) >= SMALL_PHOTO) {
            return "";
        }
        return " This photo is only " + image.getWidth() + " x " + image.getHeight() + " pixels, too small for the card's "
                + "small print to be read reliably. Use the original photo from your phone's camera (at least " + SMALL_PHOTO
                + " pixels wide), not a screenshot or a compressed copy.";
    }

    /** The name without its initials ("S. M. ARIFUL ISLAM" → "ARIFUL ISLAM"), or null if it has none or too little is left. */
    static String withoutInitials(String name) {
        String[] words = name.trim().split("\\s+");
        List<String> kept = new ArrayList<>();
        for (String word : words) {
            if (SerialMatcher.normalize(word).length() > 2) {
                kept.add(word);
            }
        }
        boolean hadInitials = kept.size() < words.length;
        int letters = kept.stream().mapToInt(w -> SerialMatcher.normalize(w).length()).sum();
        return hadInitials && kept.size() >= 2 && letters >= 8 ? String.join(" ", kept) : null;
    }
}
