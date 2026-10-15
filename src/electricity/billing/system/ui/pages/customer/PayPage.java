package electricity.billing.system.ui.pages.customer;

import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Badge;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.ChoiceCard;
import electricity.billing.system.ui.components.EmptyState;
import electricity.billing.system.ui.components.IconTile;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JPanel;

/** Pay an open bill: pick it, pay on Paytm, then report the payment for the admin to confirm. */
public class PayPage extends Page {

    private static final String PAYTM = "https://paytm.com/online-payments";

    private final JPanel list = Ui.vbox(0);
    private final JPanel stepsHolder = Ui.clear(new BorderLayout());
    private final Txt selectedLabel = Txt.h2(" ");
    private final Txt selectedAmount = Txt.display(" ");
    private final Btn paid = new Btn("I've paid", Glyph.CHECK, Btn.Style.SUCCESS).fullWidth();
    private final JPanel content = Ui.clear(new BorderLayout());
    private final List<ChoiceCard> cards = new ArrayList<>();
    private Bill chosen;

    public PayPage(AppContext app) {
        super(app);
        add(header("Pay bill", "Settle an open bill online in two steps."), BorderLayout.NORTH);

        Btn openPaytm = new Btn("Open Paytm", Glyph.CREDIT_CARD, Btn.Style.PRIMARY).fullWidth();
        openPaytm.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(PAYTM));
            } catch (Exception ex) {
                Toast.error(this, "Couldn't open the browser. Visit " + PAYTM + " instead.");
            }
        });
        paid.addActionListener(e -> reportPayment());

        Card steps = new Card(new BorderLayout()).pad(26, 26, 26, 26);
        steps.add(Ui.vbox(16,
                Txt.caps("Paying"), selectedLabel, selectedAmount,
                Box.createVerticalStrut(4),
                step(1, "Pay on Paytm", "Opens Paytm's payment page in your browser."), openPaytm,
                Box.createVerticalStrut(4),
                step(2, "Tell us you've paid", "Your bill shows Paid once an admin confirms it."), paid), BorderLayout.NORTH);
        stepsHolder.add(Ui.fixedWidth(steps, 380), BorderLayout.NORTH);

        Card listCard = new Card(new BorderLayout(0, 16));
        listCard.add(Ui.vbox(4, Txt.h2("Open bills"), Txt.small("Choose the bill you're paying")), BorderLayout.NORTH);
        listCard.add(list, BorderLayout.CENTER);

        JPanel row = Ui.clear(new BorderLayout(16, 0));
        row.add(listCard, BorderLayout.CENTER);
        row.add(stepsHolder, BorderLayout.EAST);
        content.add(row, BorderLayout.CENTER);
        body(content);
    }

    private static JPanel step(int number, String title, String text) {
        JPanel badge = Ui.clear(new BorderLayout());
        IconTile tile = IconTile.brand(number == 1 ? Glyph.CREDIT_CARD : Glyph.CHECK_CIRCLE, 36);
        badge.add(tile, BorderLayout.NORTH);
        return Ui.hbox(12, badge, Ui.vbox(3, Txt.h3(number + ". " + title), Txt.small(text).wrap(215)));
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, app.billing::myBills, this::render);
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
            JPanel row = Ui.clear(new BorderLayout(16, 0));
            Card listCard = new Card(new BorderLayout(0, 16));
            listCard.add(Ui.vbox(4, Txt.h2("Open bills"), Txt.small("Choose the bill you're paying")), BorderLayout.NORTH);
            listCard.add(Ui.top(list), BorderLayout.CENTER);
            row.add(listCard, BorderLayout.CENTER);
            row.add(stepsHolder, BorderLayout.EAST);
            content.add(row, BorderLayout.CENTER);

            for (Bill bill : open) {
                JPanel inner = Ui.clear(new BorderLayout(14, 0));
                inner.add(new IconTile(Glyph.RECEIPT, 42, Theme.BLUE), BorderLayout.WEST);
                inner.add(Ui.vbox(4, Txt.h3(Fmt.period(bill.month(), bill.year())), Txt.small(Fmt.number(bill.units()) + " units")),
                        BorderLayout.CENTER);
                inner.add(Ui.vbox(6, Txt.h3(Fmt.money(bill.total())), Badge.of(bill.status())), BorderLayout.EAST);
                ChoiceCard[] holder = new ChoiceCard[1];
                holder[0] = new ChoiceCard(inner, () -> choose(bill, holder[0]));
                cards.add(holder[0]);
                list.add(Ui.left(holder[0]));
                list.add(Box.createVerticalStrut(10));
            }
            for (Bill bill : review) {
                list.add(Ui.left(Ui.hbox(10, Badge.of(bill.status()),
                        Txt.small(Fmt.period(bill.month(), bill.year()) + "  ·  " + Fmt.money(bill.total())))));
                list.add(Box.createVerticalStrut(8));
            }
            choose(open.get(0), cards.get(0));
        }
        content.revalidate();
        content.repaint();
    }

    private void choose(Bill bill, ChoiceCard card) {
        chosen = bill;
        cards.forEach(c -> c.setSelected(c == card));
        selectedLabel.setText(Fmt.period(bill.month(), bill.year()));
        selectedAmount.setText(Fmt.money(bill.total()));
    }

    private void reportPayment() {
        if (chosen == null) {
            return;
        }
        Bill bill = chosen;
        paid.setBusy(true, "Sending…");
        Tasks.run(() -> {
            app.billing.submitPayment(bill.month(), bill.year());
            return null;
        }, done -> {
            paid.setBusy(false, null);
            Toast.success(this, "Thanks! Your " + bill.month() + " payment is waiting for confirmation.");
            onShow(null);
        }, error -> {
            paid.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
        });
    }
}
