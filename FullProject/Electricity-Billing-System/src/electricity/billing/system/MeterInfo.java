package electricity.billing.system;

import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Meter;
import electricity.billing.system.service.CustomerService;
import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Second step of adding a customer: meter and tariff set-up. */
public class MeterInfo extends BaseFrame implements ActionListener {

    private final String meterNumber;
    private final Choice meterType, billType;
    private final JButton submit;
    private final CustomerService customers = new CustomerService();

    MeterInfo(String meterNumber) {
        super("Meter Info");
        this.meterNumber = meterNumber;
        setLayout(new BorderLayout());

        JPanel p = new JPanel();
        p.setLayout(null);
        p.setBackground(PANEL_BLUE);

        JLabel heading = new JLabel("Meter Information");
        heading.setBounds(130, 70, 250, 25);
        heading.setFont(new Font("Tahoma", Font.PLAIN, 24));
        p.add(heading);

        JLabel lblnumber = new JLabel("Meter Number");
        lblnumber.setBounds(60, 120, 100, 20);
        p.add(lblnumber);
        JLabel lblmeternumber = new JLabel(meterNumber);
        lblmeternumber.setBounds(200, 120, 100, 20);
        p.add(lblmeternumber);

        JLabel lbltype = new JLabel("Meter Type");
        lbltype.setBounds(60, 160, 100, 20);
        p.add(lbltype);
        meterType = new Choice();
        meterType.add("Electric Meter");
        meterType.add("Solar Meter");
        meterType.add("Smart Meter");
        meterType.setBounds(200, 160, 200, 20);
        p.add(meterType);

        JLabel lblbill = new JLabel("Bill Type");
        lblbill.setBounds(60, 200, 100, 20);
        p.add(lblbill);
        billType = new Choice();
        for (BillType type : BillType.values()) {
            billType.add(type.label());
        }
        billType.setBounds(200, 200, 200, 20);
        p.add(billType);

        JLabel note = new JLabel("Note: By default the bill is calculated for "
                + Meter.DEFAULT_BILLING_DAYS + " days.");
        note.setBounds(60, 240, 400, 20);
        p.add(note);

        submit = darkButton("Submit");
        submit.setBounds(150, 290, 100, 25);
        submit.addActionListener(this);
        p.add(submit);

        add(p, BorderLayout.CENTER);
        add(new JLabel(icon("MeterInfo.jpg")), BorderLayout.WEST);
        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Meter meter = new Meter(meterNumber, meterType.getSelectedItem(),
                BillType.fromLabel(billType.getSelectedItem()), Meter.DEFAULT_BILLING_DAYS);
        runAsync(() -> {
            customers.saveMeter(meter);
            return null;
        }, done -> {
            showInfo("Meter information saved.");
            dispose();
        }, submit);
    }
}
