package electricity.billing.system.ui;

import electricity.billing.system.model.Months;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A billing month, e.g. January 2026; passed between pages when opening a specific bill. */
public record Period(String month, int year) {

    public static Period current() {
        return new Period(Months.current(), LocalDate.now().getYear());
    }

    /** The last {@code count} months, oldest first, ending with the current month. */
    public static List<Period> lastMonths(int count) {
        List<Period> periods = new ArrayList<>();
        LocalDate now = LocalDate.now().withDayOfMonth(1);
        for (int i = count - 1; i >= 0; i--) {
            LocalDate d = now.minusMonths(i);
            periods.add(new Period(Months.NAMES.get(d.getMonthValue() - 1), d.getYear()));
        }
        return periods;
    }

    public boolean matches(String otherMonth, int otherYear) {
        return month.equals(otherMonth) && year == otherYear;
    }

    @Override
    public String toString() {
        return month + " " + year;
    }
}
