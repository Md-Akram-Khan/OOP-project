package electricity.billing.system.service;

import electricity.billing.system.billing.Tariff;
import electricity.billing.system.db.BillDao;
import electricity.billing.system.db.CustomerDao;
import electricity.billing.system.db.Database;
import electricity.billing.system.db.MeterDao;
import electricity.billing.system.db.TaxDao;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatement;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import electricity.billing.system.model.Months;
import electricity.billing.system.model.TaxRates;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Calculating bills and tracking their payment. */
public class BillingService {

    /** Prices the units with the meter's tariff and saves the bill. One bill per meter per month. */
    public Bill createBill(String meterNo, String month, int year, int units) throws SQLException {
        if (units < 0) {
            throw new AppException("Units consumed cannot be negative.");
        }
        if (!Months.isValid(month)) {
            throw new AppException("Unknown month: " + month);
        }
        return Database.inTransaction(c -> {
            if (!new CustomerDao(c).exists(meterNo)) {
                throw new AppException("No customer has meter number " + meterNo + ".");
            }
            TaxRates rates = new TaxDao(c).load();
            BillType type = new MeterDao(c).find(meterNo).map(Meter::billType).orElse(BillType.NORMAL);
            BigDecimal total = Tariff.forBillType(type).total(units, rates);
            Bill bill = new Bill(meterNo, month, year, units, total, BillStatus.NOT_PAID);
            try {
                new BillDao(c).insert(bill);
            } catch (SQLException e) {
                if (Database.isUniqueViolation(e)) {
                    throw new AppException("Meter " + meterNo + " already has a bill for " + month + " " + year + ".");
                }
                throw e;
            }
            return bill;
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
    public void confirmPayment(String meterNo, String month, int year) throws SQLException {
        int updated = Database.withConnection(c -> new BillDao(c)
                .updateStatus(meterNo, month, year, BillStatus.PAID, BillStatus.NOT_PAID, BillStatus.PENDING));
        if (updated == 0) {
            throw new AppException("That bill is already marked as paid.");
        }
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

    public BillStatement statement(String meterNo, String month, int year) throws SQLException {
        return Database.withConnection(c -> {
            Customer customer = new CustomerDao(c).find(meterNo)
                    .orElseThrow(() -> new AppException("No customer has meter number " + meterNo + "."));
            Meter meter = new MeterDao(c).find(meterNo).orElse(null);
            TaxRates rates = new TaxDao(c).load();
            Bill bill = new BillDao(c).find(meterNo, month, year).orElse(null);
            return new BillStatement(customer, meter, rates, bill);
        });
    }
}
