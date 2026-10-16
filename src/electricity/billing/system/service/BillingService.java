package electricity.billing.system.service;

import electricity.billing.system.billing.BillCalculator;
import electricity.billing.system.billing.BillChecker;
import electricity.billing.system.billing.BillExplainer;
import electricity.billing.system.billing.SlabGuard;
import electricity.billing.system.common.Api;
import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Dates;
import electricity.billing.system.common.Money;
import electricity.billing.system.db.BillDao;
import electricity.billing.system.db.CustomerDao;
import electricity.billing.system.db.Database;
import electricity.billing.system.db.MeterDao;
import electricity.billing.system.db.NotificationDao;
import electricity.billing.system.db.TariffDao;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillBreakdown;
import electricity.billing.system.model.BillCharges;
import electricity.billing.system.model.BillExplanation;
import electricity.billing.system.model.BillStatement;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import electricity.billing.system.model.Months;
import electricity.billing.system.model.Notification;
import electricity.billing.system.model.TariffPlan;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** Pricing meter readings with the BERC tariff, and tracking bills until they're paid. */
public class BillingService {

    /** A reading priced, with what the bill checker thinks of it. Nothing is saved. */
    private record Priced(Meter meter, TariffPlan plan, int previous, int current, BillBreakdown breakdown,
            List<String> warnings, LocalDate dueDate) {
    }

    /**
     * Prices a meter reading and saves the bill. One bill per meter per month.
     * If the bill checker has concerns, the bill is only saved once the admin
     * has seen them ({@code acknowledgeWarnings}).
     */
    public Bill createBill(String meterNo, String month, int year, Integer previousReading, int currentReading,
            boolean acknowledgeWarnings) throws SQLException {
        return Database.inTransaction(c -> {
            Priced p = price(c, meterNo, month, year, previousReading, currentReading);
            if (!p.warnings().isEmpty() && !acknowledgeWarnings) {
                throw new AppException("Please review this bill before saving it:\n• " + String.join("\n• ", p.warnings()));
            }
            BillBreakdown b = p.breakdown();
            Bill bill = new Bill(meterNo, month, year, b.units(), b.payable(), BillStatus.NOT_PAID, p.previous(), p.current(),
                    new BillCharges(p.plan().id(), b.energy(), b.loadKw(), b.demand(), b.vat(), b.meterRent(), b.lateFee()),
                    b.payableAfterDue(), p.dueDate(), Dates.today(), p.warnings());
            try {
                new BillDao(c).insert(bill);
            } catch (SQLException e) {
                if (Database.isUniqueViolation(e)) {
                    throw new AppException("Meter " + meterNo + " already has a bill for " + month + " " + year + ".");
                }
                throw e;
            }
            notifyNewBill(c, bill, p);
            return bill;
        });
    }

    /** Prices a reading without saving anything, for the live preview. */
    public Api.BillPreview preview(String meterNo, String month, int year, Integer previousReading, int currentReading)
            throws SQLException {
        return Database.withConnection(c -> {
            Priced p = price(c, meterNo, month, year, previousReading, currentReading);
            return new Api.BillPreview(p.meter().billType(), p.plan().name(), p.previous(), p.current(), p.breakdown(),
                    SlabGuard.position(p.breakdown().units(), p.plan()), p.warnings(), p.dueDate());
        });
    }

    /** The meter's last reading, so the admin only has to type today's. */
    public Api.LastReading lastReading(String meterNo) throws SQLException {
        return Database.withConnection(c -> {
            requireCustomer(c, meterNo);
            Meter meter = new MeterDao(c).findOrDefault(meterNo);
            TariffPlan plan = new TariffDao(c).forMonth(meter.billType(), Dates.today().withDayOfMonth(1));
            Optional<Bill> last = new BillDao(c).lastReading(meterNo);
            return new Api.LastReading(meterNo, last.map(Bill::currentReading).orElse(null),
                    last.map(b -> b.month() + " " + b.year()).orElse(null), meter.billType(), meter.loadKw(), plan.name());
        });
    }

    /** What a month of {@code units} would cost this customer, e.g. for the appliance calculator. */
    public Api.Estimate estimate(String meterNo, int units) throws SQLException {
        if (units < 0 || units > 100_000) {
            throw new AppException("Units must be between 0 and 100,000.");
        }
        return Database.withConnection(c -> {
            Meter meter = new MeterDao(c).findOrDefault(meterNo);
            TariffPlan plan = new TariffDao(c).forMonth(meter.billType(), Dates.today().withDayOfMonth(1));
            return estimate(plan, meter, units);
        });
    }

    private static Api.Estimate estimate(TariffPlan plan, Meter meter, int units) {
        return new Api.Estimate(plan.name(), BillCalculator.calculate(units, meter.loadKw(), plan),
                SlabGuard.position(units, plan));
    }

    /**
     * Projects this month's bill from a reading the customer took today: the
     * units since the last bill's reading, scaled to a full billing cycle.
     */
    public Api.Forecast forecast(String meterNo, int currentReading) throws SQLException {
        LocalDate today = Dates.today();
        return Database.withConnection(c -> {
            Meter meter = new MeterDao(c).findOrDefault(meterNo);
            Bill last = new BillDao(c).lastReading(meterNo).filter(b -> b.issuedOn() != null)
                    .orElseThrow(() -> new AppException("There's no meter reading on file for you yet. "
                            + "Forecasts start after your first bill under the new tariff."));
            int sinceLast = currentReading - last.currentReading();
            if (sinceLast < 0) {
                throw new AppException("That's lower than your last bill's reading (" + last.currentReading()
                        + "). Check the number on your meter.");
            }
            int cycle = Math.max(1, meter.days());
            int days = (int) Math.max(1, ChronoUnit.DAYS.between(last.issuedOn(), today));
            int projected = (int) Math.round((double) sinceLast / days * cycle);
            TariffPlan plan = new TariffDao(c).forMonth(meter.billType(), today.withDayOfMonth(1));
            Api.Estimate estimate = estimate(plan, meter, projected);
            Integer budget = null;
            Integer slabEnd = estimate.slab().slabEndsAt();
            if (slabEnd != null && days < cycle) {
                budget = Math.max(0, (slabEnd - sinceLast) / (cycle - days));
            }
            return new Api.Forecast(last.currentReading(), last.issuedOn(), sinceLast, days, cycle, projected, estimate, budget);
        });
    }

    /** Customer says they have paid. The bill waits for an admin to confirm it. */
    public void submitPayment(String meterNo, String month, int year) throws SQLException {
        int updated = Database.withConnection(c -> new BillDao(c)
                .updateStatus(meterNo, month, year, BillStatus.PENDING, BillStatus.NOT_PAID));
        if (updated == 0) {
            throw new AppException("There's no unpaid bill for " + month + " " + year + ".");
        }
    }

    /** Admin confirms a payment, whether submitted online or paid in person. */
    public Bill confirmPayment(String meterNo, String month, int year) throws SQLException {
        return Database.inTransaction(c -> {
            BillDao bills = new BillDao(c);
            int updated = bills.updateStatus(meterNo, month, year, BillStatus.PAID, BillStatus.NOT_PAID, BillStatus.PENDING);
            if (updated == 0) {
                throw new AppException("That bill is already marked as paid.");
            }
            Bill bill = bills.find(meterNo, month, year).orElseThrow();
            new NotificationDao(c).insert(meterNo, Notification.Kind.PAYMENT, "Payment received for " + month + " " + year,
                    "Your " + month + " bill is now marked as paid. Thank you!");
            return bill;
        });
    }

    public Optional<Bill> find(String meterNo, String month, int year) throws SQLException {
        return Database.withConnection(c -> new BillDao(c).find(meterNo, month, year));
    }

    /** Any filter left null matches everything. */
    public List<Bill> search(String meterNo, String month, Integer year) throws SQLException {
        return Database.withConnection(c -> new BillDao(c).search(meterNo, month, year));
    }

    public List<Bill> billsFor(String meterNo) throws SQLException {
        return search(meterNo, null, null);
    }

    public List<TariffPlan> tariffs() throws SQLException {
        return Database.withConnection(c -> new TariffDao(c).all());
    }

    public BillStatement statement(String meterNo, String month, int year) throws SQLException {
        return Database.withConnection(c -> {
            Customer customer = requireCustomer(c, meterNo);
            Meter meter = new MeterDao(c).find(meterNo).orElse(null);
            List<Bill> history = new BillDao(c).search(meterNo, null, null);
            Bill bill = history.stream().filter(b -> b.month().equals(month) && b.year() == year).findFirst().orElse(null);
            TariffDao tariffs = new TariffDao(c);
            TariffPlan plan;
            BillBreakdown breakdown = null;
            BillExplanation explanation = null;
            if (bill != null && !bill.isLegacy()) {
                plan = tariffs.find(bill.charges().tariffPlanId()).orElseThrow();
                breakdown = BillCalculator.calculate(bill.units(), bill.charges().loadKw(), plan);
                int index = history.indexOf(bill);
                Bill previous = index + 1 < history.size() ? history.get(index + 1) : null;
                explanation = BillExplainer.explain(bill, previous, plan, bill.charges().loadKw());
            } else {
                BillType type = meter == null ? BillType.NORMAL : meter.billType();
                plan = tariffs.forMonth(type, Dates.monthStart(month, year));
            }
            return new BillStatement(customer, meter, plan, bill, breakdown, explanation);
        });
    }

    // ---- Pricing ------------------------------------------------------------

    private Priced price(Connection c, String meterNo, String month, int year, Integer previousReading, int currentReading)
            throws SQLException {
        if (!Months.isValid(month)) {
            throw new AppException("Unknown month: " + month);
        }
        requireCustomer(c, meterNo);
        BillDao bills = new BillDao(c);
        Integer lastOnFile = bills.lastReading(meterNo).map(Bill::currentReading).orElse(null);
        Integer previous = previousReading != null ? previousReading : lastOnFile;
        if (previous == null) {
            throw new AppException("Meter " + meterNo + " has no earlier reading on file. Enter the previous reading "
                    + "shown on the meter or the last paper bill.");
        }
        if (previous < 0 || currentReading < 0) {
            throw new AppException("Meter readings can't be negative.");
        }
        if (currentReading < previous) {
            throw new AppException("The current reading (" + currentReading + ") is lower than the previous reading ("
                    + previous + "). If the meter was replaced, enter the new meter's starting reading as the previous reading.");
        }
        Meter meter = new MeterDao(c).findOrDefault(meterNo);
        TariffPlan plan = new TariffDao(c).forMonth(meter.billType(), Dates.monthStart(month, year));
        int units = currentReading - previous;
        BillBreakdown breakdown = BillCalculator.calculate(units, meter.loadKw(), plan);
        List<Bill> history = bills.search(meterNo, null, null).stream()
                .filter(b -> !(b.month().equals(month) && b.year() == year)).toList();
        List<String> warnings = BillChecker.check(meter.billType(), units, previousReading, lastOnFile, breakdown, history);
        return new Priced(meter, plan, previous, currentReading, breakdown, warnings, Dates.today().plusDays(plan.dueDays()));
    }

    private static void notifyNewBill(Connection c, Bill bill, Priced p) throws SQLException {
        NotificationDao notes = new NotificationDao(c);
        String period = bill.month() + " " + bill.year();
        notes.insert(bill.meterNo(), Notification.Kind.BILL, "New bill for " + period,
                bill.units() + " units. Pay " + Money.tk(bill.total()) + " by " + NotificationService.DAY.format(bill.dueDate())
                        + " (" + Money.tk(bill.totalAfterDue()) + " after the due date).");
        TariffPlan plan = p.plan();
        if (plan.billType() == BillType.NORMAL && plan.slabs().size() >= 3
                && plan.slabIndexFor(bill.units()) >= plan.slabs().size() - 2) {
            int index = plan.slabIndexFor(bill.units());
            notes.insert(bill.meterNo(), Notification.Kind.USAGE, "High usage: you reached the " + plan.slabLabel(index) + " slab",
                    "Units in this slab cost " + Money.tk(plan.slabs().get(index).rate()) + " each. Use the Usage planner to "
                            + "see which appliances push you over " + plan.slabs().get(index - 1).uptoUnits() + " units.");
        }
        List<Bill> earlier = new BillDao(c).search(bill.meterNo(), null, null).stream()
                .filter(b -> b != null && !(b.month().equals(bill.month()) && b.year() == bill.year()) && b.units() > 0)
                .limit(6).toList();
        double average = earlier.stream().mapToInt(Bill::units).average().orElse(0);
        if (earlier.size() >= 2 && bill.units() > average * 1.5) {
            notes.insert(bill.meterNo(), Notification.Kind.USAGE, "Usage is up this month",
                    bill.units() + " units, against your average of " + Math.round(average) + ". That's "
                            + BigDecimal.valueOf(bill.units() / average).setScale(1, RoundingMode.HALF_UP) + "× your usual.");
        }
    }

    private static Customer requireCustomer(Connection c, String meterNo) throws SQLException {
        return new CustomerDao(c).find(meterNo)
                .orElseThrow(() -> new AppException("No customer has meter number " + meterNo + "."));
    }
}
