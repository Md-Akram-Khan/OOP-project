package electricity.billing.system.common;

import electricity.billing.system.model.Months;
import java.time.LocalDate;
import java.time.ZoneId;

/** Dates as Bangladesh sees them, so due dates don't shift with the computer's time zone. */
public final class Dates {

    public static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

    private Dates() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** The first day of a billing month, e.g. 1 September 2026. */
    public static LocalDate monthStart(String month, int year) {
        return LocalDate.of(year, Months.NAMES.indexOf(month) + 1, 1);
    }
}
