package electricity.billing.system.common;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/** Taka amounts in messages: "Tk 1,963" for whole amounts, "Tk 92.98" otherwise. */
public final class Money {

    private Money() {
    }

    public static String tk(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        boolean whole = value.stripTrailingZeros().scale() <= 0;
        return "Tk " + new DecimalFormat(whole ? "#,##0" : "#,##0.00").format(value);
    }
}
