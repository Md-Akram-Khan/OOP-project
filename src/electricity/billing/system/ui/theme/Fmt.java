package electricity.billing.system.ui.theme;

import electricity.billing.system.model.Months;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display formatting for money, numbers, periods and greetings. */
public final class Fmt {

    /** Bangladeshi taka, matching what SSLCommerz charges. */
    public static final String CURRENCY = "Tk ";

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final DecimalFormat WHOLE = new DecimalFormat("#,##0");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    private Fmt() {
    }

    public static String money(BigDecimal amount) {
        return CURRENCY + MONEY.format(amount == null ? BigDecimal.ZERO : amount);
    }

    public static String money(double amount) {
        return CURRENCY + MONEY.format(amount);
    }

    /** Tk 1.2K / Tk 3.4M style, for chart axes and tight spaces. */
    public static String moneyShort(double amount) {
        if (amount >= 1_000_000) {
            return CURRENCY + new DecimalFormat("0.#").format(amount / 1_000_000) + "M";
        }
        if (amount >= 1_000) {
            return CURRENCY + new DecimalFormat("0.#").format(amount / 1_000) + "K";
        }
        return CURRENCY + WHOLE.format(amount);
    }

    public static String number(double value) {
        return WHOLE.format(value);
    }

    public static String period(String month, int year) {
        return month + " " + year;
    }

    public static String shortMonth(String month) {
        return month.length() > 3 ? month.substring(0, 3) : month;
    }

    public static int monthIndex(String month) {
        return Months.NAMES.indexOf(month);
    }

    public static String today() {
        return DATE.format(LocalDate.now());
    }

    public static String greeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) {
            return "Good morning";
        }
        if (hour < 17) {
            return "Good afternoon";
        }
        return "Good evening";
    }

    public static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return "there";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].replace(".", "").toLowerCase(Locale.ROOT);
        boolean prefix = java.util.Set.of("md", "mr", "mrs", "ms", "dr", "engr", "sk").contains(first);
        return prefix && parts.length > 1 ? parts[1] : parts[0];
    }

    public static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }
}
