package electricity.billing.system.ui.theme;

import electricity.billing.system.model.Months;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

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

    /** Whole-taka amounts without decimals: "Tk 1,963". */
    public static String taka(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.stripTrailingZeros().scale() <= 0 ? CURRENCY + WHOLE.format(value) : money(value);
    }

    /** A per-unit or per-kW rate: "Tk 5.26". */
    public static String rate(BigDecimal rate) {
        return CURRENCY + MONEY.format(rate == null ? BigDecimal.ZERO : rate);
    }

    /** "2 kW", "2.5 kW". */
    public static String kw(BigDecimal kw) {
        return (kw == null ? "?" : kw.stripTrailingZeros().toPlainString()) + " kW";
    }

    /** "5%". */
    public static String percent(BigDecimal percent) {
        return (percent == null ? "0" : percent.stripTrailingZeros().toPlainString()) + "%";
    }

    /** "23 Sep 2026", or a dash when there's no date. */
    public static String date(LocalDate date) {
        return date == null ? "—" : DAY.format(date);
    }

    public static String dateTime(LocalDateTime at) {
        return at == null ? "—" : TIME.format(at);
    }

    /** "just now", "5 min ago", "3 h ago", "2 days ago", then the date. */
    public static String ago(LocalDateTime at, LocalDateTime now) {
        if (at == null) {
            return "";
        }
        long minutes = ChronoUnit.MINUTES.between(at, now);
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + " min ago";
        }
        if (minutes < 24 * 60) {
            return minutes / 60 + " h ago";
        }
        long days = minutes / (24 * 60);
        return days < 7 ? days + (days == 1 ? " day ago" : " days ago") : DAY.format(at);
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
