package electricity.billing.system.model;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Month names as stored in the bill table ("January" ... "December"). */
public final class Months {

    public static final List<String> NAMES = Arrays.stream(Month.values())
            .map(m -> m.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
            .toList();

    private Months() {
    }

    public static String current() {
        return NAMES.get(LocalDate.now().getMonthValue() - 1);
    }

    public static boolean isValid(String month) {
        return NAMES.contains(month);
    }
}
