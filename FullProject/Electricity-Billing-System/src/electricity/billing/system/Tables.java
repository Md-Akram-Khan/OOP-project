package electricity.billing.system;

import electricity.billing.system.model.Bill;
import electricity.billing.system.model.Customer;
import java.util.List;
import javax.swing.table.DefaultTableModel;

/** Read-only table models for the list screens. */
final class Tables {

    static final int BILL_METER = 0;
    static final int BILL_MONTH = 1;
    static final int BILL_YEAR = 2;
    static final int BILL_STATUS = 5;

    private Tables() {
    }

    static DefaultTableModel bills(List<Bill> bills) {
        DefaultTableModel model = readOnly("Meter No", "Month", "Year", "Units", "Total Bill (Rs.)", "Status");
        for (Bill b : bills) {
            model.addRow(new Object[] {b.meterNo(), b.month(), b.year(), b.units(), b.total(), b.status().label()});
        }
        return model;
    }

    static DefaultTableModel customers(List<Customer> customers) {
        DefaultTableModel model = readOnly("Name", "Meter No", "Address", "City", "State", "Email", "Phone");
        for (Customer c : customers) {
            model.addRow(new Object[] {c.name(), c.meterNo(), c.address(), c.city(), c.state(), c.email(), c.phone()});
        }
        return model;
    }

    private static DefaultTableModel readOnly(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
}
