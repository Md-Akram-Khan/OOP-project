package electricity.billing.system.ui.pages.applicant;

import electricity.billing.system.common.Api;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.MeterApplication;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.Segmented;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** The application form for a new electricity meter. Name, NID and phone come from the applicant's verification. */
public class ApplyMeterPage extends Page {

    private static final List<String> DIVISIONS = List.of("Barishal", "Chattogram", "Dhaka", "Khulna", "Mymensingh",
            "Rajshahi", "Rangpur", "Sylhet");

    private final Txt verified = Txt.muted(" ");
    private final Field email = Field.text("Email", "name@example.com", Glyph.MAIL);
    private final Field house = Field.text("House / holding", "e.g. #109", Glyph.HOME);
    private final Field road = Field.text("Road / village", "e.g. Palpara", Glyph.MAP_PIN);
    private final Field area = Field.text("Area / union", "e.g. Palpara", Glyph.MAP_PIN);
    private final Field postOffice = Field.text("Post office", "e.g. Chandpur", Glyph.MAIL);
    private final Field district = Field.text("District", "e.g. Chandpur", Glyph.MAP_PIN);
    private final JComboBox<String> division = Field.combo();
    private final Segmented category = new Segmented(BillType.NORMAL.displayName(), BillType.INDUSTRIAL.displayName());
    private final Field load = Field.text("Load needed (kW)", "e.g. 2", Glyph.BOLT);
    private final Segmented phase = new Segmented("Single phase", "Three phase");
    private final Segmented ownership = new Segmented("I own it", "I rent it");
    private final JTextArea notes = new JTextArea(3, 20);
    private final Txt formError = new Txt(" ", Theme.regular(13), () -> Theme.DANGER).wrap(700);
    private final Btn submit = new Btn("Submit application", Glyph.CHECK, Btn.Style.PRIMARY);

    public ApplyMeterPage(AppContext app) {
        super(app);
        add(header("Apply for a new meter", "Tell us where the meter is needed. We'll review it and keep you updated here."),
                BorderLayout.NORTH);
        DIVISIONS.forEach(division::addItem);
        division.setSelectedItem("Dhaka");
        load.setValue("2");
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setFont(Theme.regular(14));
        JScrollPane notesScroll = new JScrollPane(notes);
        notesScroll.setPreferredSize(new Dimension(300, 80));
        formError.setVisible(false);
        submit.addActionListener(e -> submit());
        Ui.width(category, 420);
        Ui.width(phase, 300);
        Ui.width(ownership, 300);

        Card form = new Card(new BorderLayout()).pad(26, 28, 26, 28);
        form.add(Ui.vbox(16,
                Ui.vbox(4, Txt.h2("Applicant"), verified),
                Ui.width(email, 420),
                Txt.h2("Where the meter is needed"),
                Ui.grid(2, 18, 6, house, road, area, postOffice, district, Field.of("Division", division)),
                Txt.h2("Connection"),
                Txt.caps("Category"), category,
                Ui.width(load, 260),
                Ui.columns(24, Ui.vbox(6, Txt.caps("Supply"), phase), Ui.vbox(6, Txt.caps("Premises"), ownership)),
                Txt.caps("Anything else we should know (optional)"), Ui.left(notesScroll),
                formError,
                Ui.hbox(0, Ui.glue(), submit)), BorderLayout.NORTH);
        body(form);
    }

    @Override
    public void onShow(Object param) {
        formError.setVisible(false);
        Tasks.run(this, app.applications::profile, me -> verified.setText(me.name() + "  ·  NID " + me.nidNo()
                + "  ·  " + me.phone() + "  (verified, can't be changed here)"));
    }

    private void submit() {
        formError.setVisible(false);
        BigDecimal kw;
        try {
            kw = new BigDecimal(load.value());
            load.setError(null);
        } catch (NumberFormatException e) {
            load.setError("Enter the load in kW, e.g. 2 or 3.5.");
            return;
        }
        Api.NewApplication form = new Api.NewApplication(email.value(), house.value(), road.value(), area.value(),
                postOffice.value(), district.value(), (String) division.getSelectedItem(),
                category.selectedIndex() == 0 ? BillType.NORMAL : BillType.INDUSTRIAL, kw,
                phase.selectedIndex() == 0 ? MeterApplication.Phase.SINGLE : MeterApplication.Phase.THREE,
                ownership.selectedIndex() == 0 ? MeterApplication.Ownership.OWNER : MeterApplication.Ownership.TENANT,
                notes.getText());
        submit.setBusy(true, "Submitting…");
        Tasks.run(() -> app.applications.submit(form), saved -> {
            submit.setBusy(false, null);
            Toast.success(this, "Application " + saved.reference() + " submitted. We'll update you on your home page.");
            for (Field f : new Field[] {house, road, area, postOffice, district}) {
                f.setValue("");
            }
            notes.setText("");
            app.go("home");
        }, error -> {
            submit.setBusy(false, null);
            formError.setText(Tasks.message(error));
            formError.setVisible(true);
            revalidate();
        });
    }
}
