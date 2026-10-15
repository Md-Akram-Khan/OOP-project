package electricity.billing.system.ui.pages.customer;

import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Validation;
import electricity.billing.system.model.Customer;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Avatar;
import electricity.billing.system.ui.components.Badge;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** The customer's profile, with editable contact details. */
public class ProfilePage extends Page {

    private final Avatar avatar = new Avatar("", 92);
    private final Txt name = Txt.h2(" ");
    private final Txt email = Txt.muted(" ");
    private final Badge meter = new Badge(" ", Theme.BLUE);

    private final Field emailField = Field.text("Email", "name@example.com", Glyph.MAIL);
    private final Field phone = Field.text("Phone", "Mobile number", Glyph.PHONE);
    private final Field address = Field.text("Address", "House, road, area", Glyph.MAP_PIN);
    private final Field city = Field.text("City", "City", Glyph.HOME);
    private final Field state = Field.text("State / division", "State", Glyph.HOME);
    private final Btn save = new Btn("Save changes", Glyph.CHECK, Btn.Style.PRIMARY);
    private final Btn reset = new Btn("Discard", Btn.Style.GHOST);
    private Customer loaded;

    public ProfilePage(AppContext app) {
        super(app);
        add(header("Profile", "Keep your contact details up to date."), BorderLayout.NORTH);

        Card identity = new Card(new BorderLayout()).pad(30, 24, 30, 24);
        JPanel who = Ui.vbox(10, avatar, name, email, meter);
        for (Component c : who.getComponents()) {
            if (c instanceof JComponent j) {
                j.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }
        identity.add(who, BorderLayout.NORTH);

        save.setEnabled(false);
        reset.setEnabled(false);
        save.addActionListener(e -> saveChanges());
        reset.addActionListener(e -> fill(loaded));
        DocumentListener dirty = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markDirty();
            }
        };
        for (Field f : fields()) {
            f.textField().getDocument().addDocumentListener(dirty);
        }

        Card form = new Card(new BorderLayout()).pad(26, 28, 26, 28);
        form.add(Ui.vbox(18,
                Ui.vbox(4, Txt.h2("Contact details"), Txt.small("Your name and meter number can only be changed by an admin.")),
                Ui.grid(2, 20, 6, emailField, phone, address, city, state),
                Ui.hbox(10, Ui.glue(), reset, save)), BorderLayout.NORTH);

        JPanel row = Ui.clear(new BorderLayout(16, 0));
        row.add(Ui.fixedWidth(identity, 320), BorderLayout.WEST);
        row.add(form, BorderLayout.CENTER);
        body(row);
    }

    private Field[] fields() {
        return new Field[] {emailField, phone, address, city, state};
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, app.customers::myProfile, this::fill);
    }

    private void fill(Customer c) {
        if (c == null) {
            return;
        }
        loaded = c;
        avatar.setName(c.name());
        name.setText(c.name());
        email.setText(c.email());
        meter.set("Meter #" + c.meterNo(), Theme.BLUE);
        emailField.setValue(c.email());
        phone.setValue(c.phone());
        address.setValue(c.address());
        city.setValue(c.city());
        state.setValue(c.state());
        save.setEnabled(false);
        reset.setEnabled(false);
    }

    private void markDirty() {
        if (loaded == null) {
            return;
        }
        boolean changed = !emailField.value().equals(loaded.email()) || !phone.value().equals(loaded.phone())
                || !address.value().equals(loaded.address()) || !city.value().equals(loaded.city())
                || !state.value().equals(loaded.state());
        save.setEnabled(changed);
        reset.setEnabled(changed);
    }

    private void saveChanges() {
        Customer changed = new Customer(loaded.meterNo(), loaded.name(), address.value(), city.value(), state.value(),
                emailField.value(), phone.value());
        try {
            Validation.customer(changed);
        } catch (AppException e) {
            Toast.error(this, e.getMessage());
            return;
        }
        save.setBusy(true, "Saving…");
        Tasks.run(() -> app.customers.updateMyContact(changed), updated -> {
            save.setBusy(false, null);
            fill(updated);
            Toast.success(this, "Your details are saved.");
        }, error -> {
            save.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
        });
    }
}
