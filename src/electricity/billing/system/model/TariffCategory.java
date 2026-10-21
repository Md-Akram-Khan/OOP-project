package electricity.billing.system.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The BERC tariff categories a new connection is applied under, as on DPDC's
 * application form, with the load each allows. This system has two tariffs,
 * so each category is billed on the closer one ({@link #billType()}):
 * commercial, industrial, construction, temporary and charging-station
 * connections on the commercial and industrial flat rate; residential,
 * agricultural, educational and religious, street lighting and general ones
 * on the residential slabs.
 *
 * @param maxKw the most load allowed, or null for no upper limit
 */
public record TariffCategory(String code, String name, BigDecimal minKw, BigDecimal maxKw, BillType billType) {

    public static final List<TariffCategory> ALL = List.of(
            lt("LT-A", "Residential Light and Power", BillType.NORMAL),
            lt("LT-B", "Agricultural Pumping", BillType.NORMAL),
            lt("LT-C1", "Small Industrial", BillType.INDUSTRIAL),
            lt("LT-C2", "Low Tension Construction", BillType.INDUSTRIAL),
            lt("LT-D1", "Education, Religious, Charity and Hospital", BillType.NORMAL),
            lt("LT-D2", "Street Light and Water Pumps and Battery", BillType.NORMAL),
            lt("LT-D3", "Low Tension Battery Charging Station", BillType.INDUSTRIAL),
            lt("LT-E", "Commercial and Offices", BillType.INDUSTRIAL),
            lt("LT-T", "Low Tension Temporary", BillType.INDUSTRIAL),
            mt("MT-1", "Medium Tension Residential", BillType.NORMAL),
            mt("MT-2", "Medium Tension Commercial and Office", BillType.INDUSTRIAL),
            mt("MT-3", "Medium Tension Industries", BillType.INDUSTRIAL),
            mt("MT-4", "Medium Tension Construction", BillType.INDUSTRIAL),
            mt("MT-5", "Medium Tension General", BillType.NORMAL),
            mt("MT-6", "Medium Tension Temporary", BillType.INDUSTRIAL),
            mt("MT-7", "Medium Tension Battery Charging Station", BillType.INDUSTRIAL),
            mt("MT-8", "Medium Tension Agriculture", BillType.NORMAL),
            ht("HT-1", "High Tension General", BillType.NORMAL),
            ht("HT-2", "High Tension Commercial and Office", BillType.INDUSTRIAL),
            ht("HT-3", "High Tension Industries", BillType.INDUSTRIAL),
            ht("HT-4", "High Tension Construction", BillType.INDUSTRIAL),
            new TariffCategory("EHT-1", "Extra High Tension General", kw(20001), kw(140000), BillType.NORMAL),
            new TariffCategory("EHT-2", "Extra High Tension General", kw(140001), null, BillType.NORMAL));

    public static Optional<TariffCategory> of(String code) {
        return ALL.stream().filter(t -> t.code.equals(code)).findFirst();
    }

    /** True when {@code totalKw} is within this category's range. */
    public boolean fits(BigDecimal totalKw) {
        return totalKw.compareTo(minKw) >= 0 && (maxKw == null || totalKw.compareTo(maxKw) <= 0);
    }

    /** Medium, high and extra-high tension supplies are three-phase. */
    public boolean threePhase() {
        return !code.startsWith("LT");
    }

    /** Within what this system can connect: loads up to {@link Meter#MAX_LOAD_KW}. */
    public boolean available() {
        return minKw.compareTo(Meter.MAX_LOAD_KW) <= 0;
    }

    /** "2 - 80 kW", or "more than 140001 kW". */
    public String range() {
        return maxKw == null ? "more than " + minKw.toPlainString() + " kW"
                : minKw.toPlainString() + " - " + maxKw.toPlainString() + " kW";
    }

    @Override
    public String toString() {
        return code + ": " + name + " (" + range() + ")";
    }

    private static TariffCategory lt(String code, String name, BillType billType) {
        return new TariffCategory(code, name, kw(2), kw(80), billType);
    }

    private static TariffCategory mt(String code, String name, BillType billType) {
        return new TariffCategory(code, name, kw(51), kw(5000), billType);
    }

    private static TariffCategory ht(String code, String name, BillType billType) {
        return new TariffCategory(code, name, kw(5001), kw(30000), billType);
    }

    private static BigDecimal kw(long value) {
        return BigDecimal.valueOf(value);
    }
}
