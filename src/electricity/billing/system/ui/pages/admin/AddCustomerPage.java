package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Validation;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.ChoiceCard;
import electricity.billing.system.ui.components.FadeHost;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.GradientText;
import electricity.billing.system.ui.components.Segmented;
import electricity.billing.system.ui.components.Stepper;
import electricity.billing.system.ui.components.SuccessCheck;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Adds a customer in two steps (details, then meter) and celebrates the new meter number. */
public class AddCustomerPage extends Page {

    private static final Glyph[] METER_GLYPHS = {Glyph.BOLT, Glyph.SOLAR, Glyph.CHIP};
    private static final String[] METER_NOTES = {
        "Standard grid connection", "Rooftop or ground solar", "Remote-read digital meter"};

    private final Stepper stepper = new Stepper("Customer details", "Meter set-up", "Done");
    private final FadeHost host = new FadeHost();

    private final Field meterNo = Field.text("Meter number", "Exactly as printed on the meter, e.g. 4401987", Glyph.HASH);
    private final Field name = Field.text("Full name", "e.g. Ayesha Rahman", Glyph.USER);
    private final Field email = Field.text("Email", "name@example.com", Glyph.MAIL);
    private final Field phone = Field.text("Phone", "Mobile number", Glyph.PHONE);
    private final Field address = Field.text("Address", "House, road, area", Glyph.MAP_PIN);
    private final Field city = Field.text("City", "City", Glyph.HOME);
    private final Field state = Field.text("State / division", "State", Glyph.HOME);
    private final Txt detailsError = new Txt(" ", Theme.regular(13), () -> Theme.DANGER).wrap(620);
    private final Txt meterError = new Txt(" ", Theme.regular(13), () -> Theme.DANGER).wrap(620);

    private final List<ChoiceCard> meterCards;
    private int meterType;
    private final Segmented tariff = new Segmented(BillType.NORMAL.displayName(), BillType.INDUSTRIAL.displayName());
    private final Txt tariffNote = Txt.small(BillType.NORMAL.description()).wrap(560);
    private final Field load = Field.text("Sanctioned load (kW)", "e.g. 2", Glyph.BOLT);
    private final Btn createBtn = new Btn("Create customer", Glyph.CHECK, Btn.Style.PRIMARY);

    private final SuccessCheck check = new SuccessCheck();
    private final GradientText meterNumber = new GradientText("000000", Theme.semibold(44));
    private final Txt doneText = Txt.muted(" ").wrap(460);
    private String createdMeter;

    private final JPanel details;
    private final JPanel meter;
    private final JPanel done;

    public AddCustomerPage(AppContext app) {
        super(app);
        add(header("New customer", "Add a customer and set up their meter in two quick steps."), BorderLayout.NORTH);

        // Step 1: details
        Btn next = new Btn("Continue", Glyph.ARROW_RIGHT, Btn.Style.PRIMARY).trailingIcon();
        next.addActionListener(e -> toMeterStep());
        detailsError.setVisible(false);
        meterError.setVisible(false);
        details = Ui.vbox(18,
                Ui.vbox(4, Txt.h2("Who is the customer?"),
                        Txt.small("Type the meter number printed on their meter. They'll use it with this email to claim their account.")),
                Ui.grid(2, 20, 6, meterNo, name, email, phone, address, city, state),
                detailsError,
                Ui.hbox(0, Ui.glue(), next));

        // Step 2: meter
        meterCards = new java.util.ArrayList<>();
        JPanel meterRow = Ui.columns(14);
        for (int i = 0; i < Meter.TYPES.size(); i++) {
            int index = i;
            ChoiceCard card = ChoiceCard.simple(METER_GLYPHS[i], Meter.TYPES.get(i), METER_NOTES[i], () -> pickMeter(index));
            meterCards.add(card);
            meterRow.add(card);
        }
        meterCards.get(0).setSelected(true);
        Btn back = new Btn("Back", Glyph.ARROW_LEFT, Btn.Style.GHOST);
        back.addActionListener(e -> showStep(0));
        createBtn.addActionListener(e -> createCustomer());
        Ui.width(tariff, 420);
        tariff.onChange(i -> tariffNote.setText((i == 0 ? BillType.NORMAL : BillType.INDUSTRIAL).description()));
        load.setValue(Meter.DEFAULT_LOAD_KW.stripTrailingZeros().toPlainString());
        meter = Ui.vbox(18,
                Ui.vbox(4, Txt.h2("Meter set-up"), Txt.small("Choose the meter and the customer category it's billed under.")),
                Txt.caps("Meter type"), meterRow,
                Txt.caps("Customer category"), tariff, tariffNote,
                Ui.width(load, 260),
                Txt.small("Bills are made from a photo of the meter, which must show this meter number.").wrap(560),
                Txt.small("The demand charge is the sanctioned load × the tariff's rate per kW. A typical home is 1–3 kW. "
                        + "Bills cover 30 days and are due 15 days after the meter is read.").wrap(560),
                meterError,
                Ui.hbox(10, back, Ui.glue(), createBtn));

        // Step 3: done
        Btn copy = new Btn("Copy", Glyph.COPY, Btn.Style.SECONDARY);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(createdMeter), null);
            Toast.success(this, "Meter number copied.");
        });
        Btn another = new Btn("Add another", Glyph.USER_PLUS, Btn.Style.SECONDARY);
        another.addActionListener(e -> reset());
        Btn firstBill = new Btn("Record first bill", Glyph.ARROW_RIGHT, Btn.Style.PRIMARY).trailingIcon();
        firstBill.addActionListener(e -> app.go("calculate", createdMeter));
        done = Ui.vbox(12, check, Txt.h1("Customer added"), doneText,
                Ui.hbox(14, meterNumber, copy), javax.swing.Box.createVerticalStrut(10), Ui.hbox(10, another, firstBill));
        for (Component c : done.getComponents()) {
            if (c instanceof JComponent j) {
                j.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }

        Card card = new Card(new BorderLayout()).pad(28, 30, 28, 30);
        card.add(host, BorderLayout.CENTER);
        body(Ui.vbox(10, Ui.pad(stepper, 0, 12, 0, 0), card));
        showStep(0);
    }

    @Override
    public void onShow(Object param) {
        if (createdMeter != null) {
            reset();
        }
    }

    private void showStep(int step) {
        stepper.setStep(step);
        host.show(step == 0 ? details : step == 1 ? meter : done);
    }

    private Customer draft() {
        return new Customer(meterNo.value(), name.value(), address.value(), city.value(), state.value(), email.value(), phone.value());
    }

    private void toMeterStep() {
        try {
            Validation.meterNumber(meterNo.value());
            Validation.customer(draft());
        } catch (AppException e) {
            detailsError.setText(e.getMessage());
            detailsError.setVisible(true);
            details.revalidate();
            return;
        }
        detailsError.setVisible(false);
        showStep(1);
    }

    private void pickMeter(int index) {
        meterType = index;
        for (int i = 0; i < meterCards.size(); i++) {
            meterCards.get(i).setSelected(i == index);
        }
    }

    private void createCustomer() {
        Customer customer = draft();
        String type = Meter.TYPES.get(meterType);
        BillType billType = tariff.selectedIndex() == 0 ? BillType.NORMAL : BillType.INDUSTRIAL;
        BigDecimal loadKw;
        try {
            loadKw = new BigDecimal(load.value());
            if (loadKw.signum() <= 0 || loadKw.compareTo(Meter.MAX_LOAD_KW) > 0) {
                throw new NumberFormatException();
            }
            load.setError(null);
        } catch (NumberFormatException e) {
            load.setError("Enter the load in kW, e.g. 2 or 2.5.");
            return;
        }
        createBtn.setBusy(true, "Creating…");
        meterError.setVisible(false);
        // Customer and meter are saved together: if anything fails, nothing is saved and the admin can simply retry.
        Tasks.run(() -> app.customers.create(customer, type, billType, loadKw), saved -> {
            createBtn.setBusy(false, null);
            createdMeter = saved.meterNo();
            meterNumber.setText(saved.meterNo());
            doneText.setText(saved.name() + " is set up on meter " + saved.meterNo()
                    + ". They'll use this meter number with their email to create their account.");
            showStep(2);
            check.play();
        }, error -> {
            createBtn.setBusy(false, null);
            String message = Tasks.message(error);
            meterError.setText(message);
            meterError.setVisible(true);
            meter.revalidate();
            Toast.error(this, message);
        });
    }

    private void reset() {
        for (Field f : new Field[] {meterNo, name, email, phone, address, city, state}) {
            f.setValue("");
        }
        pickMeter(0);
        tariff.select(0);
        load.setValue(Meter.DEFAULT_LOAD_KW.stripTrailingZeros().toPlainString());
        load.setError(null);
        createdMeter = null;
        detailsError.setVisible(false);
        meterError.setVisible(false);
        showStep(0);
    }
}
