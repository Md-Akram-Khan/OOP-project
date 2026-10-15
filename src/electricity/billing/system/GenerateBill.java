package electricity.billing.system;

import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatement;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import electricity.billing.system.model.TaxRates;
import electricity.billing.system.service.BillingService;
import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Customer views a printable bill for a chosen month and year. */
public class GenerateBill extends BaseFrame implements ActionListener {

    private static final String RULE = "  ------------------------------------------------\n";

    private final String meter;
    private final Choice month, year;
    private final JTextArea area;
    private final JButton generate;
    private final BillingService billing = new BillingService();

    GenerateBill(String meter) {
        super("Generate Bill");
        this.meter = meter;
        setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.add(new JLabel("Generate Bill"));
        panel.add(new JLabel(meter));
        month = monthChoice();
        year = yearChoice();
        panel.add(month);
        panel.add(year);
        add(panel, BorderLayout.NORTH);

        area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        area.setText("\n\n   Choose a month and year, then click Generate Bill.");
        add(new JScrollPane(area), BorderLayout.CENTER);

        generate = new JButton("Generate Bill");
        generate.addActionListener(this);
        add(generate, BorderLayout.SOUTH);

        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String billMonth = month.getSelectedItem();
        int billYear = Integer.parseInt(year.getSelectedItem());
        runAsync(() -> billing.statement(meter, billMonth, billYear),
                statement -> {
                    area.setText(format(statement, billMonth, billYear));
                    area.setCaretPosition(0);
                },
                generate);
    }

    private static String format(BillStatement statement, String month, int year) {
        StringBuilder out = new StringBuilder();
        out.append("\n  ELECTRICITY BILL FOR ").append(month.toUpperCase()).append(' ').append(year).append("\n");
        out.append(RULE);

        Customer c = statement.customer();
        line(out, "Customer Name", c.name());
        line(out, "Meter Number", c.meterNo());
        line(out, "Address", c.address());
        line(out, "City", c.city());
        line(out, "State", c.state());
        line(out, "Email", c.email());
        line(out, "Phone", c.phone());
        out.append(RULE);

        Meter m = statement.meter();
        if (m != null) {
            line(out, "Meter Type", m.meterType());
            line(out, "Bill Type", m.billType().label());
            line(out, "Days", String.valueOf(m.days()));
            out.append(RULE);
        }

        TaxRates t = statement.rates();
        line(out, "Cost Per Unit", "Rs. " + t.costPerUnit());
        if (m != null && m.billType() == BillType.INDUSTRIAL) {
            line(out, "Industrial Rate", "x " + t.industrialMultiplier());
        }
        line(out, "Meter Rent", "Rs. " + t.meterRent());
        line(out, "Service Charge", "Rs. " + t.serviceCharge());
        line(out, "Service Tax", "Rs. " + t.serviceTax());
        line(out, "Fixed Tax", "Rs. " + t.fixedTax());
        out.append(RULE);

        Bill b = statement.bill();
        if (b == null) {
            out.append("\n  No bill has been calculated for ").append(month).append(' ').append(year).append(" yet.\n");
        } else {
            line(out, "Units Consumed", String.valueOf(b.units()));
            line(out, "Status", b.status().label());
            out.append(RULE);
            line(out, "TOTAL PAYABLE", "Rs. " + b.total());
        }
        return out.toString();
    }

    private static void line(StringBuilder out, String label, String value) {
        out.append(String.format("  %-16s: %s%n", label, value == null ? "" : value));
    }
}
