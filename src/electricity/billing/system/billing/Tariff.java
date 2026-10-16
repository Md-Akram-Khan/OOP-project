package electricity.billing.system.billing;

import electricity.billing.system.model.BillBreakdown;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.TariffPlan;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * How a category prices the units used in a month. Each category supplies its
 * own energy lines; demand charge, VAT, meter rent and the late fee are worked
 * out the same way for everyone by {@link BillCalculator}.
 */
public interface Tariff {

    /** The units split into priced lines, e.g. 75 units at Tk 5.26, then 125 at Tk 8.50. */
    List<BillBreakdown.Line> energyLines(int units, TariffPlan plan);

    static Tariff forBillType(BillType type) {
        return switch (type) {
            case NORMAL -> new NormalTariff();
            case INDUSTRIAL -> new IndustrialTariff();
        };
    }

    static BillBreakdown.Line line(String label, int units, BigDecimal rate) {
        return new BillBreakdown.Line(label, units, rate,
                rate.multiply(BigDecimal.valueOf(units)).setScale(2, RoundingMode.HALF_UP));
    }
}
