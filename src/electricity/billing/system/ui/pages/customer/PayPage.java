package electricity.billing.system.ui.pages.customer;

import electricity.billing.system.client.PaymentClient;
import electricity.billing.system.common.Api;
import electricity.billing.system.common.Dates;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.Payment;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.Period;
import electricity.billing.system.ui.components.Badge;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.ChoiceCard;
import electricity.billing.system.ui.components.EmptyState;
import electricity.billing.system.ui.components.FadeHost;
import electricity.billing.system.ui.components.IconTile;
import electricity.billing.system.ui.components.Spinner;
import electricity.billing.system.ui.components.SuccessCheck;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Pay an open bill online with SSLCommerz: the app opens SSLCommerz's secure
 * checkout in the browser, then waits while the server confirms the payment
 * with the gateway. The bill turns Paid on its own; no admin step is needed.
 */
public class PayPage extends Page {

    private static final int POLL_MS = 3000;
    private static final long GIVE_UP_MS = 20 * 60 * 1000;

    private final PaymentClient payments = new PaymentClient();
    private final JPanel list = Ui.vbox(0);
    private final JPanel content = Ui.clear(new BorderLayout());
    private final FadeHost checkout = new FadeHost();
    private final List<ChoiceCard> cards = new ArrayList<>();

    private final Txt payingLabel = Txt.h2(" ");
    private final Txt payingAmount = Txt.display(" ");
    private final Btn payBtn = new Btn("Pay with SSLCommerz", Glyph.CREDIT_CARD, Btn.Style.PRIMARY).fullWidth();
    private final Badge modeBadge = new Badge("Sandbox · test mode", Theme.WARNING);
    private final Txt testCard = Txt.small("Test card 4111 1111 1111 1111 · exp 12/26 · CVV 111 · OTP 111111").wrap(300);
    private final Txt doneText = Txt.muted(" ").wrap(300);
    private final Txt lateNote = Txt.small(" ").wrap(300);
    private final SuccessCheck check = new SuccessCheck();
    private final Btn manualPaid = new Btn("I've paid another way", Glyph.CHECK, Btn.Style.SECONDARY).fullWidth();

    private final JPanel readyView;
    private final JPanel waitingView;
    private final JPanel doneView;
    private final JPanel offlineView;

    private Api.PaymentConfig config;
    private Bill chosen;
    private Api.PaymentStart active;
    private Timer poller;
    private long pollStarted;
    private boolean polling;

    public PayPage(AppContext app) {
        super(app);
        add(header("Pay bill", "Pay securely online with SSLCommerz: cards, mobile banking and more."), BorderLayout.NORTH);

        payBtn.addActionListener(e -> startPayment());
        manualPaid.addActionListener(e -> reportManualPayment());

        readyView = Ui.vbox(14,
                Txt.caps("Paying"), payingLabel, payingAmount, lateNote, modeBadge,
                Box.createVerticalStrut(2), payBtn,
                Txt.small("You'll pay on SSLCommerz's secure page in your browser. "
                        + "This page updates by itself when the payment is confirmed.").wrap(300),
                testCard);

        Btn reopen = new Btn("Reopen payment page", Glyph.ARROW_RIGHT, Btn.Style.SECONDARY).trailingIcon().fullWidth();
        reopen.addActionListener(e -> openBrowser(active == null ? null : active.gatewayUrl()));
        Btn stop = new Btn("Cancel", Btn.Style.GHOST).fullWidth();
        stop.addActionListener(e -> {
            stopPolling();
            showReady();
        });
        waitingView = centred(new Spinner(64), Txt.h2("Waiting for your payment"),
                Txt.muted("Finish paying in your browser. We'll confirm it with SSLCommerz automatically.").wrap(290),
                Box.createVerticalStrut(6), reopen, stop);

        Btn invoice = new Btn("View invoice", Glyph.FILE_TEXT, Btn.Style.PRIMARY).fullWidth();
        invoice.addActionListener(e -> {
            if (chosen != null) {
                app.go("statement", new Period(chosen.month(), chosen.year()));
            }
        });
        doneView = centred(check, Txt.h2("Payment received"), doneText, Box.createVerticalStrut(6), invoice);

        offlineView = Ui.vbox(14,
                new IconTile(Glyph.INFO, 42, Theme.WARNING),
                Txt.h3("Online payment isn't available"),
                Txt.muted("The server hasn't been set up with SSLCommerz yet. "
                        + "If you've paid another way, let us know and an admin will confirm it.").wrap(300),
                manualPaid);

        body(content);
    }

    private static JPanel centred(Component... parts) {
        JPanel stack = Ui.vbox(12, parts);
        for (Component c : stack.getComponents()) {
            if (c instanceof JComponent j) {
                j.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }
        return stack;
    }

    @Override
    public void onShow(Object param) {
        if (polling) {
            return;
        }
        Tasks.run(this, () -> new Object[] {payments.config(), app.billing.myBills()}, result -> {
            config = (Api.PaymentConfig) result[0];
            @SuppressWarnings("unchecked")
            List<Bill> bills = (List<Bill>) result[1];
            render(bills);
        });
    }

    private void render(List<Bill> bills) {
        list.removeAll();
        cards.clear();
        chosen = null;
        List<Bill> open = bills.stream().filter(b -> b.status() == BillStatus.NOT_PAID).toList();
        List<Bill> review = bills.stream().filter(b -> b.status() == BillStatus.PENDING).toList();

        content.removeAll();
        if (open.isEmpty()) {
            String text = review.isEmpty() ? "You have no open bills. Nice work!"
                    : review.size() + " payment" + (review.size() == 1 ? " is" : "s are") + " waiting for confirmation.";
            Btn history = new Btn("View my bills", Glyph.RECEIPT, Btn.Style.SECONDARY);
            history.addActionListener(e -> app.go("bills"));
            content.add(EmptyState.of(Glyph.CHECK_CIRCLE, "You're all paid up", text, history), BorderLayout.CENTER);
            content.setPreferredSize(new Dimension(10, 420));
        } else {
            content.setPreferredSize(null);
            Card listCard = new Card(new BorderLayout(0, 16));
            listCard.add(Ui.vbox(4, Txt.h2("Open bills"), Txt.small("Choose the bill you're paying")), BorderLayout.NORTH);
            listCard.add(Ui.top(list), BorderLayout.CENTER);
            Card checkoutCard = new Card(new BorderLayout()).pad(26, 26, 26, 26);
            checkoutCard.add(checkout, BorderLayout.NORTH);

            JPanel row = Ui.clear(new BorderLayout(16, 0));
            row.add(listCard, BorderLayout.CENTER);
            row.add(Ui.top(Ui.fixedWidth(checkoutCard, 390)), BorderLayout.EAST);
            content.add(row, BorderLayout.CENTER);

            for (Bill bill : open) {
                JPanel inner = Ui.clear(new BorderLayout(14, 0));
                inner.add(new IconTile(Glyph.RECEIPT, 42, Theme.BLUE), BorderLayout.WEST);
                LocalDate today = Dates.today();
                String due = bill.dueDate() == null ? "" : bill.isOverdue(today) ? "  ·  was due " + Fmt.date(bill.dueDate())
                        : "  ·  due " + Fmt.date(bill.dueDate());
                inner.add(Ui.vbox(4, Txt.h3(Fmt.period(bill.month(), bill.year())), Txt.small(Fmt.number(bill.units()) + " units" + due)),
                        BorderLayout.CENTER);
                inner.add(Ui.vbox(6, Txt.h3(Fmt.money(bill.amountDue(today))), Badge.of(bill, today)), BorderLayout.EAST);
                ChoiceCard[] holder = new ChoiceCard[1];
                holder[0] = new ChoiceCard(inner, () -> choose(bill, holder[0]));
                cards.add(holder[0]);
                list.add(Ui.left(holder[0]));
                list.add(Box.createVerticalStrut(10));
            }
            for (Bill bill : review) {
                list.add(Ui.left(Ui.hbox(10, Badge.of(bill.status()),
                        Txt.small(Fmt.period(bill.month(), bill.year()) + "  ·  " + Fmt.money(bill.amountDue(Dates.today()))))));
                list.add(Box.createVerticalStrut(8));
            }
            choose(open.get(0), cards.get(0));
            showReady();
        }
        content.revalidate();
        content.repaint();
    }

    private void choose(Bill bill, ChoiceCard card) {
        if (polling) {
            return;
        }
        chosen = bill;
        cards.forEach(c -> c.setSelected(c == card));
        LocalDate today = Dates.today();
        payingLabel.setText(Fmt.period(bill.month(), bill.year()));
        payingAmount.setText(Fmt.money(bill.amountDue(today)));
        lateNote.setVisible(bill.dueDate() != null);
        lateNote.setText(bill.isOverdue(today)
                ? "Includes the late fee: this bill was due on " + Fmt.date(bill.dueDate()) + "."
                : bill.totalAfterDue() == null ? " "
                : "Pay by " + Fmt.date(bill.dueDate()) + ". After that it's " + Fmt.money(bill.totalAfterDue()) + " with the late fee.");
    }

    private void showReady() {
        boolean online = config != null && config.enabled();
        modeBadge.setVisible(online && config.sandbox());
        testCard.setVisible(online && config.sandbox());
        checkout.show(online ? readyView : offlineView);
    }

    private void startPayment() {
        if (chosen == null) {
            return;
        }
        Bill bill = chosen;
        payBtn.setBusy(true, "Opening secure checkout…");
        Tasks.run(() -> payments.start(bill.month(), bill.year()), start -> {
            payBtn.setBusy(false, null);
            active = start;
            openBrowser(start.gatewayUrl());
            checkout.show(waitingView);
            startPolling();
        }, error -> {
            payBtn.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
        });
    }

    private void openBrowser(String url) {
        if (url == null) {
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            Toast.error(this, "Couldn't open your browser. Copy this address into it: " + url);
        }
    }

    private void startPolling() {
        stopPolling();
        polling = true;
        pollStarted = System.currentTimeMillis();
        poller = new Timer(POLL_MS, e -> poll());
        poller.start();
    }

    private void stopPolling() {
        polling = false;
        if (poller != null) {
            poller.stop();
        }
    }

    private boolean pollInFlight;

    private void poll() {
        if (active == null || pollInFlight) {
            return;
        }
        if (System.currentTimeMillis() - pollStarted > GIVE_UP_MS) {
            stopPolling();
            showReady();
            Toast.info(this, "Stopped waiting. If you did pay, your bill will update once it's confirmed.");
            return;
        }
        String tranId = active.tranId();
        pollInFlight = true;
        Tasks.run(() -> payments.status(tranId), state -> {
            pollInFlight = false;
            if (!polling || active == null || !tranId.equals(active.tranId())) {
                return;
            }
            if (state.status() == Payment.Status.PAID) {
                stopPolling();
                boolean review = state.billStatus() == BillStatus.PENDING;
                doneText.setText(review
                        ? "SSLCommerz flagged this payment for a quick check. An admin will confirm it shortly."
                        : "Thanks! Your " + chosen.month() + " " + chosen.year() + " bill is now paid.");
                checkout.show(doneView);
                check.play();
                Toast.success(this, "Payment of " + payingAmount.getText() + " received.");
            } else if (state.status() == Payment.Status.FAILED || state.status() == Payment.Status.CANCELLED) {
                stopPolling();
                Toast.error(this, state.status() == Payment.Status.CANCELLED
                        ? "Payment cancelled. Nothing was charged." : "The payment didn't go through. Nothing was charged.");
                showReady();
            }
        }, error -> pollInFlight = false);
    }

    private void reportManualPayment() {
        if (chosen == null) {
            return;
        }
        Bill bill = chosen;
        manualPaid.setBusy(true, "Sending…");
        Tasks.run(() -> {
            app.billing.submitPayment(bill.month(), bill.year());
            return null;
        }, done -> {
            manualPaid.setBusy(false, null);
            Toast.success(this, "Thanks! Your " + bill.month() + " payment is waiting for an admin to confirm it.");
            onShow(null);
        }, error -> {
            manualPaid.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
        });
    }
}
