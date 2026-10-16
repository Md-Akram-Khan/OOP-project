package electricity.billing.system.model;

import java.math.BigDecimal;

/**
 * Where a month's usage sits in the tariff, for slab alerts.
 *
 * @param slabEndsAt last unit of the current slab, or null in the top slab
 * @param nextRate   what each unit after {@code slabEndsAt} costs, or null in the top slab
 * @param lifeline   true while the month qualifies for the lifeline rate; going
 *                   past {@code slabEndsAt} then re-prices every unit, not just the extra ones
 */
public record SlabPosition(String slabLabel, BigDecimal rate, Integer slabEndsAt, BigDecimal nextRate, boolean lifeline) {

    /** Units left before the price per unit goes up, or null in the top slab. */
    public Integer unitsLeft(int units) {
        return slabEndsAt == null ? null : Math.max(0, slabEndsAt - units);
    }
}
