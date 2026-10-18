package electricity.billing.system.meterphoto;

import electricity.billing.system.common.AppException;
import electricity.billing.system.model.NidRecord;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks a photo of a Bangladeshi NID card against the dummy NID database.
 *
 * Like the meter photo reader, it doesn't try to read arbitrary text: it finds
 * which stored record's NID number is printed on the card, then checks that
 * the same record's English name and date of birth are printed there too. All
 * three must match.
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
        Raster raster = Raster.atLeast(MeterPhotoReader.decode(photo), 1300, 1600);
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
        if (distinct.isEmpty()) {
            String problem = read.nidNo() == null
                    ? "The NID number on this card couldn't be read. Retake the photo straight on, in good light, with the "
                            + "whole card in focus."
                    : "The NID number read from this card (" + read.nidNo() + ") isn't in the NID records. If it was misread, "
                            + "retake the photo straight on, in good light, with the whole card in focus.";
            return new Result(null, false, false, false, problem, read);
        }
        if (distinct.size() > 1) {
            return new Result(null, false, false, false,
                    "The card matches more than one NID record. Retake the photo so only your card is in view.", read);
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
        if (!name || !birth) {
            return new Result(record, true, name, birth, "The NID number was found, but the " + (!name && !birth
                    ? "name and date of birth" : !name ? "name" : "date of birth") + " on the card "
                    + (!name && !birth ? "don't" : "doesn't") + " match the NID record. "
                    + "Make sure the whole card is in the photo and in focus.", read);
        }
        return new Result(record, true, true, true, null, read);
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
