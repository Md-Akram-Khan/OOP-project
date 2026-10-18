package electricity.billing.system.ui;

import electricity.billing.system.client.AuthClient;
import electricity.billing.system.common.Api;
import electricity.billing.system.model.NidRecord;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Callout;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.FadeHost;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.Photos;
import electricity.billing.system.ui.components.Stepper;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.io.File;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Sign-up for someone who needs a new meter, in three steps: verify the phone
 * with a one-time code, verify identity with photos of both sides of the NID
 * card (the front's details are checked), then
 * choose a username and password. The result is an applicant account that can
 * apply for a meter.
 */
public class NewMeterSignupDialog extends JDialog {

    private static final int WIDTH = 560;
    private static final String NID_HINT = "Photograph each side straight on, in good light, with the whole card in the photo.";

    private final AuthClient auth = new AuthClient();
    private final Consumer<String> onCreated;
    private final Stepper stepper = new Stepper("Phone", "NID card", "Account");
    private final FadeHost host = new FadeHost();

    // Step 1: phone
    private final Field phone = Field.text("Mobile number", "01XXXXXXXXX", Glyph.PHONE);
    private final Btn send = new Btn("Send code", Glyph.ARROW_RIGHT, Btn.Style.PRIMARY).trailingIcon();
    private final Field code = Field.text("6-digit code", "From the SMS", Glyph.LOCK);
    private final Btn verify = new Btn("Verify", Glyph.CHECK, Btn.Style.PRIMARY);
    private final Btn resend = new Btn("Send again", Btn.Style.GHOST);
    private final Callout demo = new Callout(Glyph.INFO, Theme.BLUE, "Demo SMS (not really sent)", WIDTH - 170);
    private final JPanel codeBox;
    private final Txt phoneError = error();
    private final JPanel browserBox;
    private final Txt browserText = Txt.muted(" ").wrap(WIDTH - 110);
    private final Btn reopen = new Btn("Reopen the page", Glyph.ARROW_RIGHT, Btn.Style.SECONDARY).trailingIcon();
    private Timer countdown;
    private Timer poller;
    private String phoneMode = "demo";
    private Api.PhoneSession browserSession;
    private String phoneToken;
    private String verifiedPhone;

    // Step 2: NID
    private final CardSide front = new CardSide("Front", "With your photo and NID number");
    private final CardSide back = new CardSide("Back", "With your address and the barcode");
    private final Txt nidStatus = Txt.small(NID_HINT).wrap(WIDTH - 110);
    private final Callout nidProblem = new Callout(Glyph.ALERT, Theme.DANGER, "The card couldn't be verified", WIDTH - 170);
    private final JPanel identityBox = Ui.vbox(6);
    private final JPanel extractedBox = Ui.vbox(6);
    private final Btn toAccount = new Btn("Continue", Glyph.ARROW_RIGHT, Btn.Style.PRIMARY).trailingIcon();
    private String identityToken;

    // Step 3: account
    private final Field username = Field.text("Username", "3-30 letters or numbers", Glyph.USER);
    private final Field password = Field.password("Password", "At least 8 characters");
    private final Field confirm = Field.password("Confirm password", "Repeat password");
    private final Txt accountError = error();
    private final Btn create = new Btn("Create account", Glyph.CHECK, Btn.Style.PRIMARY).fullWidth();

    private final JPanel phoneStep;
    private final JPanel nidStep;
    private final JPanel accountStep;

    public NewMeterSignupDialog(Window owner, Consumer<String> onCreated) {
        super(owner, "Apply for a new meter: sign up", ModalityType.APPLICATION_MODAL);
        this.onCreated = onCreated;

        // Step 1
        send.addActionListener(e -> sendCode());
        phone.textField().addActionListener(e -> sendCode());
        verify.addActionListener(e -> verifyCode());
        code.textField().addActionListener(e -> verifyCode());
        resend.addActionListener(e -> sendCode());
        JPanel phoneRow = Ui.clear(new BorderLayout(10, 0));
        phoneRow.add(phone, BorderLayout.CENTER);
        phoneRow.add(Ui.top(Ui.pad(Ui.hbox(0, send), 26, 0, 0, 0)), BorderLayout.EAST);
        JPanel codeRow = Ui.clear(new BorderLayout(10, 0));
        codeRow.add(code, BorderLayout.CENTER);
        codeRow.add(Ui.top(Ui.pad(Ui.hbox(8, verify, resend), 26, 0, 0, 0)), BorderLayout.EAST);
        codeBox = Ui.vbox(12, codeRow, demo);
        codeBox.setVisible(false);
        reopen.addActionListener(e -> openBrowser());
        Btn cancelBrowser = new Btn("Cancel", Btn.Style.GHOST);
        cancelBrowser.addActionListener(e -> stopPolling());
        browserBox = Ui.vbox(10, new electricity.billing.system.ui.components.Spinner(40), browserText,
                Ui.hbox(8, reopen, cancelBrowser));
        browserBox.setVisible(false);
        phoneStep = Ui.vbox(14,
                Ui.vbox(4, Txt.h2("Verify your mobile number"),
                        Txt.small("We'll text you a one-time code. It expires in 5 minutes.").wrap(WIDTH - 80)),
                phoneRow, codeBox, browserBox, phoneError);

        // Step 2
        front.choose.addActionListener(e -> chooseSide(front));
        back.choose.addActionListener(e -> chooseSide(back));
        toAccount.addActionListener(e -> showStep(2));
        toAccount.setVisible(false);
        nidProblem.setVisible(false);
        identityBox.setVisible(false);
        nidStep = Ui.vbox(14,
                Ui.vbox(4, Txt.h2("Verify your identity"),
                        Txt.small("Upload photos of both sides of your National ID card. The details on the front are "
                                + "checked against the NID records.").wrap(WIDTH - 80)),
                Ui.columns(16, front.panel, back.panel), nidStatus, extractedBox, nidProblem, identityBox,
                Ui.hbox(0, Ui.glue(), toAccount),
                Txt.small("For this project the NID records are a sample database, not the Election Commission's: the check "
                        + "shows the card matches a stored record, not that it's genuine.").wrap(WIDTH - 110));

        // Step 3
        create.addActionListener(e -> createAccount());
        accountStep = Ui.vbox(14,
                Ui.vbox(4, Txt.h2("Choose your sign-in"),
                        Txt.small("You'll sign in on the Customer side. Once your meter is connected, this account becomes "
                                + "your customer account.").wrap(WIDTH - 80)),
                username, Ui.columns(12, password, confirm), accountError, create);

        Card card = new Card(new BorderLayout()).pad(24, 26, 24, 26);
        card.add(host, BorderLayout.CENTER);
        JPanel content = Ui.themed(new BorderLayout(0, 12), Theme::bg);
        content.setBorder(new EmptyBorder(20, 20, 20, 20));
        content.add(Ui.pad(stepper, 0, 8, 6, 8), BorderLayout.NORTH);
        content.add(card, BorderLayout.CENTER);
        setContentPane(content);
        showStep(0);
        setSize(WIDTH + 80, 780);
        setLocationRelativeTo(owner);
        extractedBox.setVisible(false);
        Tasks.run(auth::phoneMode, mode -> {
            phoneMode = mode;
            if ("firebase".equals(mode)) {
                send.setText("Verify with Firebase");
            }
        }, error -> { });
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                stopPolling();
            }
        });
    }

    private static Txt error() {
        Txt t = new Txt(" ", Theme.regular(13), () -> Theme.DANGER).wrap(WIDTH - 80);
        t.setVisible(false);
        return t;
    }

    private void showStep(int step) {
        stepper.setStep(step);
        host.show(Ui.vertical(step == 0 ? phoneStep : step == 1 ? nidStep : accountStep));
    }

    private void showError(Txt label, String message) {
        label.setText(message);
        label.setVisible(message != null);
        label.getParent().revalidate();
    }

    // ---- Step 1 -------------------------------------------------------------

    private void sendCode() {
        if ("firebase".equals(phoneMode)) {
            startFirebase();
            return;
        }
        String number = phone.value();
        showError(phoneError, null);
        send.setBusy(true, "Sending…");
        resend.setEnabled(false);
        Tasks.run(() -> auth.sendOtp(number), sent -> {
            send.setBusy(false, null);
            verifiedPhone = sent.phone();
            phone.setValue(sent.phone());
            codeBox.setVisible(true);
            demo.setVisible(sent.demoCode() != null);
            if (sent.demoCode() != null) {
                demo.lines("Your code is " + sent.demoCode() + ". This project has no paid SMS gateway, so the code is shown "
                        + "here instead of being texted. Add one in the server's .env to send real SMS.");
            }
            code.setValue("");
            code.input().requestFocusInWindow();
            startCountdown(sent.resendInSeconds());
            phoneStep.revalidate();
        }, error -> {
            send.setBusy(false, null);
            resend.setEnabled(true);
            showError(phoneError, Tasks.message(error));
        });
    }

    private void startCountdown(int seconds) {
        if (countdown != null) {
            countdown.stop();
        }
        int[] left = {seconds};
        resend.setEnabled(false);
        resend.setText("Send again (" + left[0] + ")");
        countdown = new Timer(1000, e -> {
            left[0]--;
            if (left[0] <= 0) {
                countdown.stop();
                resend.setText("Send again");
                resend.setEnabled(true);
            } else {
                resend.setText("Send again (" + left[0] + ")");
            }
        });
        countdown.start();
    }

    private void verifyCode() {
        String typed = code.value();
        showError(phoneError, null);
        verify.setBusy(true, "Checking…");
        Tasks.run(() -> auth.verifyOtp(verifiedPhone == null ? phone.value() : verifiedPhone, typed), ok -> {
            verify.setBusy(false, null);
            phoneToken = ok.phoneToken();
            if (countdown != null) {
                countdown.stop();
            }
            showStep(1);
        }, error -> {
            verify.setBusy(false, null);
            showError(phoneError, Tasks.message(error));
        });
    }

    // ---- Step 1 with Firebase: the code is texted by Firebase and entered in the browser ----

    private void startFirebase() {
        String number = phone.value();
        showError(phoneError, null);
        send.setBusy(true, "Opening…");
        Tasks.run(() -> auth.startPhoneVerification(number), started -> {
            send.setBusy(false, null);
            browserSession = started;
            verifiedPhone = started.phone();
            phone.setValue(started.phone());
            browserText.setText("Finish in your browser: Firebase will text a 6-digit code to " + started.phone()
                    + ". Enter it on the page, and this window moves on by itself.");
            browserBox.setVisible(true);
            phoneStep.revalidate();
            openBrowser();
            startPolling();
        }, error -> {
            send.setBusy(false, null);
            showError(phoneError, Tasks.message(error));
        });
    }

    private void openBrowser() {
        if (browserSession == null) {
            return;
        }
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(browserSession.url()));
        } catch (Exception e) {
            showError(phoneError, "Couldn't open the browser. Open this address yourself: " + browserSession.url());
        }
    }

    private void startPolling() {
        stopPolling();
        String session = browserSession.session();
        boolean[] busy = {false};
        poller = new Timer(2000, e -> {
            if (busy[0]) {
                return;
            }
            busy[0] = true;
            Tasks.run(() -> auth.phoneVerificationStatus(session), status -> {
                busy[0] = false;
                if (status.verified() && browserSession != null && session.equals(browserSession.session())) {
                    stopPolling();
                    phoneToken = status.phoneToken();
                    browserBox.setVisible(false);
                    showStep(1);
                }
            }, error -> {
                busy[0] = false;
                stopPolling();
                showError(phoneError, Tasks.message(error));
            });
        });
        poller.start();
    }

    private void stopPolling() {
        if (poller != null) {
            poller.stop();
        }
        browserBox.setVisible(false);
    }

    // ---- Step 2 -------------------------------------------------------------

    /** One side of the NID card: its photo, once chosen, and the button to choose it. */
    private static final class CardSide {
        final JLabel thumb = new JLabel();
        final Btn choose;
        final JPanel panel;
        final String name;
        Photos.Upload upload;

        CardSide(String name, String hint) {
            this.name = name;
            choose = new Btn("Choose " + name.toLowerCase() + " photo", Glyph.FILE_TEXT, Btn.Style.SECONDARY);
            thumb.setHorizontalAlignment(SwingConstants.CENTER);
            thumb.setPreferredSize(new Dimension(230, 146));
            thumb.setBorder(new LineBorder(Theme.border(), 1, true));
            thumb.setIcon(Icons.of(Glyph.FILE_TEXT, 36, Theme::muted));
            panel = Ui.vbox(8, Ui.left(Txt.caps(name + " of the card")), Ui.left(Txt.small(hint).wrap(230)), Ui.left(thumb),
                    Ui.left(Ui.hbox(0, choose)));
        }
    }

    private void chooseSide(CardSide side) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a photo of the " + side.name.toLowerCase() + " of your NID card");
        chooser.setFileFilter(new FileNameExtensionFilter("Photos (JPEG, PNG)", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        nidProblem.setVisible(false);
        identityBox.setVisible(false);
        extractedBox.setVisible(false);
        toAccount.setVisible(false);
        identityToken = null;
        side.choose.setBusy(true, "Opening…");
        Tasks.run(() -> Photos.loadOriginal(file), upload -> {
            // Show the photo straight away, whatever the check finds.
            side.upload = upload;
            side.thumb.setIcon(Photos.icon(upload.image(), 226, 142));
            side.choose.setBusy(false, null);
            side.choose.setText("Use another photo");
            if (front.upload != null && back.upload != null) {
                checkNid();
            } else {
                nidStatus.setText("Now choose a photo of the " + (front.upload == null ? "front" : "back") + " of the card.");
                nidStep.revalidate();
            }
        }, error -> {
            side.choose.setBusy(false, null);
            nidProblem.lines(Tasks.message(error));
            nidProblem.setVisible(true);
            nidStep.revalidate();
        });
    }

    private void checkNid() {
        byte[] frontBytes = front.upload.bytes();
        byte[] backBytes = back.upload.bytes();
        setChoosing(false);
        nidStatus.setText("Reading both sides and checking the NID records…");
        Tasks.run(() -> auth.verifyNid(phoneToken, frontBytes, backBytes), result -> {
            setChoosing(true);
            showExtracted(result.fields());
            nidProblem.setVisible(!result.verified());
            identityBox.setVisible(false);
            toAccount.setVisible(false);
            if (result.verified()) {
                identityToken = result.identityToken();
                showIdentity(result.record());
                nidStatus.setText("Verified against the NID records.");
                toAccount.setVisible(true);
            } else {
                nidStatus.setText(NID_HINT);
                nidProblem.lines(result.message());
            }
            nidStep.revalidate();
            nidStep.repaint();
        }, error -> {
            setChoosing(true);
            nidStatus.setText(NID_HINT);
            nidProblem.lines(Tasks.message(error));
            nidProblem.setVisible(true);
            nidStep.revalidate();
        });
    }

    /** Lets the applicant pick photos again, or stops them while a check is running. */
    private void setChoosing(boolean enabled) {
        front.choose.setEnabled(enabled);
        back.choose.setEnabled(enabled);
    }

    /** What was read from the card itself, field by field. */
    private void showExtracted(java.util.List<Api.CardField> fields) {
        extractedBox.removeAll();
        extractedBox.add(Ui.left(Txt.caps("Read from your NID card")));
        for (Api.CardField f : fields) {
            JPanel row = Ui.clear(new BorderLayout(10, 0));
            row.add(Ui.width(Txt.small(f.label()), 110), BorderLayout.WEST);
            row.add(new Txt(f.value(), f.matched() ? Theme.semibold(14) : Theme.regular(14),
                    f.matched() ? Theme::text : () -> Theme.DANGER), BorderLayout.CENTER);
            row.add(new Txt(f.matched() ? "✓ matches" : "✗ no match", Theme.semibold(12),
                    f.matched() ? () -> Theme.SUCCESS : () -> Theme.DANGER), BorderLayout.EAST);
            extractedBox.add(Ui.left(row));
        }
        if (fields.stream().anyMatch(f -> !f.matched())) {
            extractedBox.add(Ui.left(Txt.small("Unmatched details are what the reader made out of your photo, and it can "
                    + "misread blurred characters. Check them against your card.").wrap(WIDTH - 110)));
        }
        extractedBox.setVisible(!fields.isEmpty());
    }

    private void showIdentity(NidRecord r) {
        identityBox.removeAll();
        identityBox.add(Ui.left(new Txt("✓ Identity verified", Theme.semibold(15), () -> Theme.SUCCESS)));
        identityBox.add(Ui.left(Txt.caps("From the NID records")));
        identityBox.add(Ui.left(new Txt(r.nameBn() + "  ·  " + r.nameEn(), Theme.bangla(15), Theme::text)));
        if (r.fatherBn() != null) {
            identityBox.add(Ui.left(new Txt("পিতা: " + r.fatherBn(), Theme.bangla(13), Theme::muted)));
        }
        if (r.motherBn() != null) {
            identityBox.add(Ui.left(new Txt("মাতা: " + r.motherBn(), Theme.bangla(13), Theme::muted)));
        }
        if (r.addressBn() != null) {
            identityBox.add(Ui.left(new Txt("ঠিকানা: " + r.addressBn(), Theme.bangla(13), Theme::muted).wrap(WIDTH - 110)));
        }
        String extra = (r.bloodGroup() == null ? "" : "Blood group " + r.bloodGroup() + "  ·  ")
                + (r.issueDate() == null ? "" : "Issued " + Fmt.date(r.issueDate()) + "  ·  ") + "Phone " + verifiedPhone + " verified";
        identityBox.add(Ui.left(Txt.small(extra)));
        identityBox.setVisible(true);
    }

    // ---- Step 3 -------------------------------------------------------------

    private void createAccount() {
        showError(accountError, null);
        confirm.setError(null);
        char[] pass = password.password();
        if (!Arrays.equals(pass, confirm.password())) {
            confirm.setError("Passwords don't match.");
            return;
        }
        String user = username.value();
        create.setBusy(true, "Creating account…");
        Tasks.run(() -> {
            auth.createApplicant(identityToken, user, pass);
            return null;
        }, done -> {
            create.setBusy(false, null);
            dispose();
            onCreated.accept(user);
        }, error -> {
            create.setBusy(false, null);
            showError(accountError, Tasks.message(error));
        });
    }
}
