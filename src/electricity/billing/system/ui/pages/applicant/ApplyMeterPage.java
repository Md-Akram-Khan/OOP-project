package electricity.billing.system.ui.pages.applicant;

import electricity.billing.system.common.Api;
import electricity.billing.system.common.Places;
import electricity.billing.system.model.MeterApplication;
import electricity.billing.system.model.MeterApplication.Address;
import electricity.billing.system.model.MeterApplication.ConnectionType;
import electricity.billing.system.model.MeterApplication.ConsumerType;
import electricity.billing.system.model.MeterApplication.Gender;
import electricity.billing.system.model.MeterApplication.Phase;
import electricity.billing.system.model.TariffCategory;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Callout;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.Segmented;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.math.BigDecimal;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The application form for a new electricity meter, with the same fields as
 * DPDC's online new-connection form (docs/DPDC-new-connection-form-fields.txt).
 * Fields marked * are required. The applicant's name, NID number, date of
 * birth and phone come from their verification and can't be changed here; for
 * an organization they are the proprietor's.
 */
public class ApplyMeterPage extends Page {

    private static final String CHOOSE = "Choose…";
    private static final int WIDE = 760;

    private final Segmented consumerType = new Segmented("Personal", "Organization / Institute", "Investor");
    private final Callout investorNote = new Callout(Glyph.INFO, Theme.BLUE, "Institutional investors apply through BIDA", WIDE - 60);
    private final JPanel formBody;
    private final Txt verified = Txt.muted(" ").wrap(WIDE);

    // Personal
    private final Field fatherName = Field.text("Father's name *", "As on your NID", Glyph.USER);
    private final Field motherName = Field.text("Mother's name *", "As on your NID", Glyph.USER);
    private final JComboBox<Object> gender = Field.combo();
    private final Field spouseName = Field.text("Spouse name", "Optional", Glyph.USER);
    private final JPanel personalBox;

    // Organization
    private final Field organizationName = Field.text("Organization name *", "e.g. Palpara Rice Mill", Glyph.HOME);
    private final Txt proprietor = Txt.small(" ").wrap(WIDE);
    private final JPanel organizationBox;

    private final AddressFields mailing = new AddressFields(false);
    private final AddressFields connection = new AddressFields(true);
    private final JCheckBox sameAddress = new JCheckBox("Same as the mailing address");
    private final Field area = Field.text("Area *", "e.g. Palpara", Glyph.MAP_PIN);
    private final Field nocs = Field.text("NOCS (local office)", "If you know it", Glyph.MAP_PIN);

    // Description of connection
    private final JComboBox<ConnectionType> connectionType = Field.combo();
    private final JComboBox<Phase> phase = Field.combo();
    private final JComboBox<Object> tariff = Field.combo();
    private final Txt tariffNote = new Txt(" ", Theme.regular(12), () -> Theme.DANGER).wrap(WIDE);
    private final Field meterCount = Field.text("Demand meter (number of meters) *", "e.g. 1", Glyph.HASH);
    private final Field load = Field.text("Total demand load (kW) *", "e.g. 2", Glyph.BOLT);
    private final JComboBox<String> differentOwners = Field.combo();
    private final Field differentOwnersField = Field.of("Meter owners are different people? *", differentOwners);
    private final JPanel differentOwnersRow = Ui.grid(2, 18, 6, differentOwnersField, Ui.clear(new BorderLayout()));
    private final Field existingMeters = Field.text("No. of existing meters", "0 if none", Glyph.HASH);
    private final Field existingLoad = Field.text("Total existing load (kW)", "0 if none", Glyph.BOLT);
    private final Field existingCustomerNo = Field.text("Existing customer / meter number (any one)", "e.g. E-756216", Glyph.HASH);
    private final JPanel existingCustomerRow = Ui.grid(2, 18, 6, existingCustomerNo, Ui.clear(new BorderLayout()));
    private final Segmented ownership = new Segmented("I own it", "I rent it");
    private final JTextArea notes = new JTextArea(3, 20);

    private final JCheckBox declaration = new JCheckBox();
    private final Txt formError = new Txt(" ", Theme.regular(13), () -> Theme.DANGER).wrap(WIDE);
    private final Btn submit = new Btn("Submit application", Glyph.CHECK, Btn.Style.PRIMARY);
    private Api.ApplicantProfile profile;

    public ApplyMeterPage(AppContext app) {
        super(app);
        add(header("Apply for a new meter", "Fields marked * are required. We'll review your application and keep you "
                + "updated here."), BorderLayout.NORTH);
        investorNote.lines("For institutional investors, use the BIDA web portal: bidaquickserv.org");
        investorNote.setVisible(false);
        consumerType.onChange(i -> showConsumerType());

        // Names may be typed in Bangla (and parents' names come from the NID record in Bangla): use a font that has both.
        for (Field f : new Field[] {fatherName, motherName, spouseName}) {
            f.textField().setFont(Theme.bangla(14));
        }
        gender.addItem(CHOOSE);
        for (Gender g : Gender.values()) {
            gender.addItem(g);
        }
        personalBox = Ui.grid(2, 18, 6, fatherName, motherName, Field.of("Gender *", gender), spouseName);
        organizationBox = Ui.vbox(8, Ui.width(organizationName, 420), Ui.left(proprietor));
        organizationBox.setVisible(false);

        sameAddress.setOpaque(false);
        sameAddress.setFont(Theme.semibold(13));
        sameAddress.addActionListener(e -> {
            if (sameAddress.isSelected()) {
                connection.copyFrom(mailing);
            } else {
                connection.clear();
            }
        });

        for (ConnectionType t : ConnectionType.values()) {
            connectionType.addItem(t);
        }
        for (Phase p : Phase.values()) {
            phase.addItem(p);
        }
        tariff.addItem(CHOOSE);
        TariffCategory.ALL.forEach(tariff::addItem);
        tariff.addActionListener(e -> tariffChanged());
        tariffNote.setVisible(false);
        differentOwners.addItem(CHOOSE);
        differentOwners.addItem("Yes");
        differentOwners.addItem("No");
        differentOwnersRow.setVisible(false);
        existingCustomerRow.setVisible(false);
        meterCount.setValue("1");
        onEdit(load, this::loadChanged);
        onEdit(meterCount, () -> showRow(differentOwnersRow, number(meterCount.value()) > 1));
        onEdit(existingMeters, () -> showRow(existingCustomerRow, number(existingMeters.value()) > 0));
        Ui.width(ownership, 300);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setFont(Theme.regular(14));
        JScrollPane notesScroll = new JScrollPane(notes);
        notesScroll.setPreferredSize(new Dimension(300, 70));

        declaration.setOpaque(false);
        JPanel declarationRow = Ui.clear(new BorderLayout(8, 0));
        declarationRow.add(Ui.top(declaration), BorderLayout.WEST);
        declarationRow.add(Txt.small("I/We hereby declare that I/we shall comply with the Electricity Act of the People's "
                + "Republic of Bangladesh and the tariff structure approved by the Ministry of Power, Energy and Mineral "
                + "Resources, including any later amendments, and agree to abide by the utility's Terms of Service and "
                + "Privacy Policy. *").wrap(WIDE - 40), BorderLayout.CENTER);

        formError.setVisible(false);
        submit.addActionListener(e -> submit());

        formBody = Ui.vbox(16,
                Ui.vbox(4, Txt.h2("Applicant"), verified),
                personalBox,
                organizationBox,
                Txt.h2("Mailing address"),
                mailing.panel,
                Ui.hbox(12, Txt.h2("Connection address"), sameAddress),
                connection.panel,
                Ui.grid(2, 18, 6, area, nocs),
                Txt.h2("Description of connection"),
                Ui.grid(2, 18, 6, Field.of("Connection type", connectionType), Field.of("Phase", phase)),
                Ui.grid(1, 0, 6, Field.of("Tariff category *", tariff)),
                Ui.left(tariffNote),
                Ui.grid(2, 18, 6, meterCount, load),
                differentOwnersRow,
                Ui.grid(2, 18, 6, existingMeters, existingLoad),
                existingCustomerRow,
                Ui.vbox(6, Txt.caps("Premises *"), ownership),
                Txt.caps("Anything else we should know (optional)"), Ui.grid(1, 0, 0, notesScroll),
                Txt.h2("Declaration"),
                declarationRow,
                formError,
                Ui.hbox(0, Ui.glue(), submit));

        Card form = new Card(new BorderLayout()).pad(26, 28, 26, 28);
        form.add(Ui.vbox(16, Ui.vbox(6, Txt.caps("Consumer type *"), Ui.width(consumerType, 560)), investorNote, formBody),
                BorderLayout.NORTH);
        body(form);
    }

    @Override
    public void onShow(Object param) {
        formError.setVisible(false);
        Tasks.run(this, app.applications::profile, me -> {
            profile = me;
            String born = me.dateOfBirth() == null ? "" : "  ·  born " + Fmt.date(me.dateOfBirth());
            verified.setText(me.name() + "  ·  NID " + me.nidNo() + born + "  ·  " + me.phone()
                    + "  (verified from your NID and phone, can't be changed here)");
            proprietor.setText("Proprietor / applicant: " + me.name() + born + ", from your verified NID.");
            if (fatherName.value().isEmpty() && me.fatherName() != null) {
                fatherName.setValue(me.fatherName());
            }
            if (motherName.value().isEmpty() && me.motherName() != null) {
                motherName.setValue(me.motherName());
            }
            mailing.prefillMobile(me.phone());
            connection.prefillMobile(me.phone());
        });
    }

    private void showRow(JPanel row, boolean show) {
        if (row.isVisible() != show) {
            row.setVisible(show);
            revalidate();
        }
    }

    private void showConsumerType() {
        int i = consumerType.selectedIndex();
        investorNote.setVisible(i == 2);
        formBody.setVisible(i != 2);
        personalBox.setVisible(i == 0);
        organizationBox.setVisible(i == 1);
        revalidate();
        repaint();
    }

    /** Medium and higher tension tariffs are three-phase; the highest can't be applied for here. */
    private void tariffChanged() {
        TariffCategory t = tariff.getSelectedItem() instanceof TariffCategory c ? c : null;
        if (t != null && t.threePhase()) {
            phase.setSelectedItem(Phase.THREE);
        }
        boolean unavailable = t != null && !t.available();
        tariffNote.setText(unavailable ? t.code() + " is for loads over 5000 kW, which the utility connects directly. "
                + "It can't be applied for here." : " ");
        tariffNote.setVisible(unavailable);
        revalidate();
    }

    /** Over 7 kW needs a three-phase supply, as DPDC's form sets it. */
    private void loadChanged() {
        BigDecimal kw = decimal(load.value());
        if (kw == null) {
            return;
        }
        boolean threePhaseTariff = tariff.getSelectedItem() instanceof TariffCategory c && c.threePhase();
        phase.setSelectedItem(kw.compareTo(new BigDecimal("7")) > 0 || threePhaseTariff ? Phase.THREE : Phase.SINGLE);
    }

    private void submit() {
        formError.setVisible(false);
        int type = consumerType.selectedIndex();
        BigDecimal kw = decimal(load.value());
        Integer meters = integer(meterCount.value());
        Integer existing = integer(existingMeters.value());
        BigDecimal existingKw = decimal(existingLoad.value());
        if (!numbers(kw, meters, existing, existingKw)) {
            return;
        }
        String owners = (String) differentOwners.getSelectedItem();
        Api.NewApplication form = new Api.NewApplication(
                type == 1 ? ConsumerType.ORGANIZATION : ConsumerType.PERSONAL,
                fatherName.value(), motherName.value(), spouseName.value(),
                gender.getSelectedItem() instanceof Gender g ? g : null,
                organizationName.value(),
                mailing.value(), connection.value(), area.value(), nocs.value(),
                (ConnectionType) connectionType.getSelectedItem(), (Phase) phase.getSelectedItem(),
                tariff.getSelectedItem() instanceof TariffCategory t ? t.code() : null,
                meters, kw, "Yes".equals(owners) ? Boolean.TRUE : "No".equals(owners) ? Boolean.FALSE : null,
                existing, existingKw, existingCustomerNo.value(),
                ownership.selectedIndex() == 0 ? MeterApplication.Ownership.OWNER : MeterApplication.Ownership.TENANT,
                notes.getText(), declaration.isSelected());
        submit.setBusy(true, "Submitting…");
        Tasks.run(() -> app.applications.submit(form), saved -> {
            submit.setBusy(false, null);
            Toast.success(this, "Application " + saved.reference() + " submitted. We'll update you on your home page.");
            reset();
            app.go("home");
        }, error -> {
            submit.setBusy(false, null);
            formError.setText(Tasks.message(error));
            formError.setVisible(true);
            revalidate();
        });
    }

    /** Marks number fields that hold something other than a number; true when all are fine. */
    private boolean numbers(BigDecimal kw, Integer meters, Integer existing, BigDecimal existingKw) {
        boolean ok = true;
        ok &= mark(load, load.value().isEmpty() || kw != null, "Enter the load in kW, e.g. 2 or 3.5.");
        ok &= mark(meterCount, meterCount.value().isEmpty() || meters != null, "Enter a whole number, e.g. 1.");
        ok &= mark(existingMeters, existingMeters.value().isEmpty() || existing != null, "Enter a whole number, or leave it empty.");
        ok &= mark(existingLoad, existingLoad.value().isEmpty() || existingKw != null, "Enter the load in kW, or leave it empty.");
        return ok;
    }

    private static boolean mark(Field field, boolean fine, String message) {
        field.setError(fine ? null : message);
        return fine;
    }

    private void reset() {
        for (Field f : new Field[] {spouseName, organizationName, area, nocs, load, existingMeters, existingLoad, existingCustomerNo}) {
            f.setValue("");
        }
        meterCount.setValue("1");
        mailing.clear();
        connection.clear();
        sameAddress.setSelected(false);
        tariff.setSelectedIndex(0);
        differentOwners.setSelectedIndex(0);
        declaration.setSelected(false);
        notes.setText("");
        if (profile != null) {
            mailing.prefillMobile(profile.phone());
            connection.prefillMobile(profile.phone());
        }
    }

    private static void onEdit(Field field, Runnable action) {
        field.textField().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        });
    }

    private static int number(String text) {
        Integer n = integer(text);
        return n == null ? 0 : n;
    }

    private static Integer integer(String text) {
        try {
            return text == null || text.isBlank() ? null : Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String text) {
        try {
            return text == null || text.isBlank() ? null : new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One address block of the form: mailing or connection. */
    private static final class AddressFields {
        final Field house = Field.text("House/Plot/Dag No *", "e.g. #109", Glyph.HOME);
        final Field road = Field.text("Lane/Road No", "e.g. Road 7", Glyph.MAP_PIN);
        final Field section = Field.text("Section", "Optional", Glyph.MAP_PIN);
        final Field block = Field.text("Block", "Optional", Glyph.MAP_PIN);
        final JComboBox<String> division = Field.combo();
        final JComboBox<String> district = Field.combo();
        final JComboBox<String> thana = Field.combo();
        final Field postCode = Field.text("Post code", "4 digits, e.g. 1207", Glyph.MAIL);
        final Field email;
        final Field telephone = Field.text("Telephone", "Land line, optional", Glyph.PHONE);
        final Field mobile = Field.text("Mobile *", "01XXXXXXXXX", Glyph.PHONE);
        final JPanel panel;

        AddressFields(boolean emailRequired) {
            email = Field.text(emailRequired ? "E-mail *" : "E-mail", emailRequired ? "Bills and receipts are sent here"
                    : "Optional", Glyph.MAIL);
            division.addItem(CHOOSE);
            Places.DIVISIONS.forEach(division::addItem);
            district.addItem(CHOOSE);
            thana.setEditable(true);
            thana.putClientProperty(com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT, "Type or choose");
            division.addActionListener(e -> fillDistricts());
            district.addActionListener(e -> fillThanas());
            panel = Ui.grid(2, 18, 6, house, road, section, block, Field.of("Division *", division),
                    Field.of("District *", district), Field.of("Thana / upazila *", thana), postCode, email, telephone, mobile);
        }

        private void fillDistricts() {
            Object chosen = district.getSelectedItem();
            district.removeAllItems();
            district.addItem(CHOOSE);
            Places.DISTRICTS.getOrDefault((String) division.getSelectedItem(), java.util.List.of()).forEach(district::addItem);
            district.setSelectedItem(chosen);
        }

        private void fillThanas() {
            Object typed = thana.getEditor().getItem();
            thana.removeAllItems();
            Places.thanas((String) district.getSelectedItem()).forEach(thana::addItem);
            thana.getEditor().setItem(typed);
        }

        Address value() {
            return new Address(house.value(), road.value(), section.value(), block.value(), thanaText(),
                    chosen(district), chosen(division), postCode.value(), email.value(), telephone.value(), mobile.value());
        }

        private String thanaText() {
            Object item = thana.getEditor().getItem();
            return item == null ? "" : item.toString().trim();
        }

        private static String chosen(JComboBox<String> box) {
            Object item = box.getSelectedItem();
            return item == null || CHOOSE.equals(item) ? null : item.toString();
        }

        void copyFrom(AddressFields other) {
            for (Field[] pair : new Field[][] {{house, other.house}, {road, other.road}, {section, other.section},
                {block, other.block}, {postCode, other.postCode}, {email, other.email}, {telephone, other.telephone},
                {mobile, other.mobile}}) {
                pair[0].setValue(pair[1].value());
            }
            division.setSelectedItem(other.division.getSelectedItem());
            district.setSelectedItem(other.district.getSelectedItem());
            thana.getEditor().setItem(other.thanaText());
        }

        void clear() {
            for (Field f : new Field[] {house, road, section, block, postCode, email, telephone, mobile}) {
                f.setValue("");
            }
            division.setSelectedIndex(0);
            thana.getEditor().setItem("");
        }

        void prefillMobile(String phone) {
            if (mobile.value().isEmpty() && phone != null) {
                mobile.setValue(phone);
            }
        }
    }
}
