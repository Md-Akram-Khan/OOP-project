package electricity.billing.system.meterphoto;

import electricity.billing.system.meterphoto.SerialMatcher.ReadLine;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A best-effort reading of the NID number, English name and date of birth on
 * the front of an NID card, from the characters the matcher saw. It is only
 * for showing the applicant what was made out of their photo; whether the card
 * matches a record is decided by {@link IdCardReader}'s stricter checks.
 *
 * Letters and look-alike digits are put right by context: O, I, L, S, B, Z in
 * a number are read as 0, 1, 1, 5, 8, 2, and the other way round in a name.
 */
final class NidText {

    private static final String[] MONTHS = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    /** Digits, allowing look-alike letters between digits: a whole number starts and ends with a real digit. */
    private static final Pattern NUMBER = Pattern.compile("[0-9](?:[0-9OILSBZ ]*[0-9])?");

    private NidText() {
    }

    static IdCardReader.CardText read(SerialMatcher.Analysis analysis) {
        List<ReadLine> lines = SerialMatcher.readLines(analysis);
        return new IdCardReader.CardText(number(lines), name(lines), birthDate(lines));
    }

    /** The longest run of 10 to 17 digits (NID numbers are 10, 13 or 17 long), from the most clearly read line. */
    private static String number(List<ReadLine> lines) {
        String best = null;
        double bestScore = 0;
        for (ReadLine line : lines) {
            Matcher m = NUMBER.matcher(line.text());
            while (m.find()) {
                String digits = asDigits(m.group().replace(" ", ""));
                boolean nidLength = digits.length() == 10 || digits.length() == 13 || digits.length() == 17;
                if (digits.length() < 10 || digits.length() > 17) {
                    continue;
                }
                double score = line.score() + (nidLength ? 1 : 0);
                if (score > bestScore) {
                    best = digits;
                    bestScore = score;
                }
            }
        }
        return best;
    }

    /**
     * What follows the "Name" label, as letters: on the same line (old cards,
     * "Name: Tajul Islam"), or on the line below when the label stands alone
     * (smart cards print a small "Name" above the name).
     */
    private static String name(List<ReadLine> lines) {
        String best = null;
        double bestScore = 0;
        for (ReadLine line : lines) {
            String text = line.text().trim();
            int after = afterLabel(text, "NAME");
            String rest;
            if (after >= 0) {
                rest = text.substring(after);
            } else if (labelOnly(text, "NAME")) {
                ReadLine value = lineBeside(lines, line);
                if (value == null) {
                    value = lineBelow(lines, line);
                }
                if (value == null) {
                    continue;
                }
                rest = value.text();
            } else {
                continue;
            }
            String value = asLetters(rest).replaceAll("[^A-Z ]", "").trim().replaceAll(" +", " ");
            if (value.replace(" ", "").length() >= 3 && line.score() + value.length() / 100.0 > bestScore) {
                best = value;
                bestScore = line.score() + value.length() / 100.0;
            }
        }
        return best;
    }

    /** The first "day month year" on the card, such as 01 Dec 1990; the front shows no other date. */
    private static String birthDate(List<ReadLine> lines) {
        String best = null;
        double bestScore = 0;
        for (ReadLine line : lines) {
            String s = line.text().replace(" ", "");
            for (int i = 0; i + 8 <= s.length(); i++) {
                for (int dayLength = 2; dayLength >= 1; dayLength--) {
                    if (i + dayLength + 7 > s.length()) {
                        continue;
                    }
                    String day = asDigits(s.substring(i, i + dayLength));
                    String month = asLetters(s.substring(i + dayLength, i + dayLength + 3));
                    String year = asDigits(s.substring(i + dayLength + 3, i + dayLength + 7));
                    int monthIndex = month(month);
                    if (!day.matches("\\d+") || !year.matches("(19|20)\\d\\d") || monthIndex < 0
                            || Integer.parseInt(day) < 1 || Integer.parseInt(day) > 31) {
                        continue;
                    }
                    double score = line.score() + (month.equals(MONTHS[monthIndex]) ? 0.5 : 0);
                    if (score > bestScore) {
                        String name = MONTHS[monthIndex].charAt(0) + MONTHS[monthIndex].substring(1).toLowerCase(Locale.ROOT);
                        best = String.format("%02d %s %s", Integer.parseInt(day), name, year);
                        bestScore = score;
                    }
                }
            }
        }
        return best;
    }

    /** The month whose short name differs from {@code letters} in at most one place, or -1. */
    private static int month(String letters) {
        for (int m = 0; m < MONTHS.length; m++) {
            int same = 0;
            for (int i = 0; i < 3; i++) {
                same += MONTHS[m].charAt(i) == letters.charAt(i) ? 1 : 0;
            }
            if (same == 3) {
                return m;
            }
        }
        for (int m = 0; m < MONTHS.length; m++) {
            int same = 0;
            for (int i = 0; i < 3; i++) {
                same += MONTHS[m].charAt(i) == letters.charAt(i) ? 1 : 0;
            }
            if (same == 2) {
                return m;
            }
        }
        return -1;
    }

    /**
     * Where the value starts if the line begins with the word {@code label}, read
     * loosely: one character wrong, missing or extra, or two when a letter was
     * split in two ("NAN1E" for "NAME"); -1 if it doesn't. "NATIONAL" is not "NAME".
     */
    private static int afterLabel(String text, String label) {
        String upper = text.toUpperCase(Locale.ROOT);
        int space = upper.indexOf(' ');
        if (space > 0) {
            String word = upper.substring(0, space);
            int allowed = word.length() == label.length() + 1 ? 2 : 1;
            return distance(word, label) <= allowed ? space + 1 : -1;
        }
        return upper.length() > label.length() && distance(upper.substring(0, label.length()), label) <= 1 ? label.length() : -1;
    }

    /** True when the whole line is just the label, read loosely as in {@link #afterLabel}. */
    private static boolean labelOnly(String text, String label) {
        String word = text.toUpperCase(Locale.ROOT).replace(" ", "");
        int allowed = word.length() == label.length() + 1 ? 2 : 1;
        return word.length() >= label.length() - 1 && distance(word, label) <= allowed;
    }

    /** The nearest line to the right of {@code label} on the same row (the label and its value read as separate lines); null if none. */
    private static ReadLine lineBeside(List<ReadLine> lines, ReadLine label) {
        int h = Math.max(1, label.area().height());
        double cy = (label.area().y0() + label.area().y1()) / 2.0;
        ReadLine best = null;
        for (ReadLine line : lines) {
            double lineCy = (line.area().y0() + line.area().y1()) / 2.0;
            int gap = line.area().x0() - label.area().x1();
            boolean beside = Math.abs(lineCy - cy) < 0.5 * h && gap >= 0 && gap < 10 * h;
            if (beside && (best == null || line.area().x0() < best.area().x0())) {
                best = line;
            }
        }
        return best;
    }

    /** The nearest line starting just under {@code label}, roughly level with its left edge; null if none. */
    private static ReadLine lineBelow(List<ReadLine> lines, ReadLine label) {
        int h = Math.max(1, label.area().height());
        ReadLine best = null;
        for (ReadLine line : lines) {
            int gap = line.area().y0() - label.area().y1();
            boolean under = gap > -h / 2 && gap < 3 * h && Math.abs(line.area().x0() - label.area().x0()) < 4 * h;
            if (under && line != label && (best == null || line.area().y0() < best.area().y0())) {
                best = line;
            }
        }
        return best;
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static String asDigits(String s) {
        return s.replace('O', '0').replace('D', '0').replace('I', '1').replace('L', '1').replace('S', '5').replace('B', '8')
                .replace('Z', '2');
    }

    private static String asLetters(String s) {
        return s.replace('0', 'O').replace('1', 'I').replace('5', 'S').replace('8', 'B').replace('2', 'Z');
    }
}
