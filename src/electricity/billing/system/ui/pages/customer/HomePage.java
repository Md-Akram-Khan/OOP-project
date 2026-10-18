package electricity.billing.system.ui.pages.customer;

import electricity.billing.system.common.Dates;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.TariffPlan;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.Period;
import electricity.billing.system.ui.components.Badge;
import electricity.billing.system.ui.components.BarChart;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Callout;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.StatCard;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import electricity.billing.system.ui.components.CenterLayout;
import java.awt.font.TextAttribute;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;

/** The customer's home: their latest bill front and centre, plus usage at a glance. */
public class HomePage extends Page {

    private final Txt greeting = Txt.h1(" ");
    private final Txt heroPeriod = white("No bills yet", Theme.semibold(18), 255);
    private final Txt heroAmount = white(" ", Theme.semibold(44), 255);
    private final Txt heroNote = white("Your first bill will appear here.", Theme.regular(14), 200);
    private final Badge heroBadge = new Badge("", Theme.SUCCESS).onColor();
    private final Btn heroAction = new Btn("Pay now", Glyph.ARROW_RIGHT, Btn.Style.SECONDARY).trailingIcon();
    private final StatCard lastUnits = new StatCard("Units, latest bill", Glyph.GAUGE, Theme.BLUE, v -> Fmt.number(v));
    private final StatCard average = new StatCard("Average per month", Glyph.TRENDING_UP, Theme.AQUA, v -> Fmt.number(v));
    private final StatCard paidYear = new StatCard("Paid this year", Glyph.WALLET, Theme.VIOLET, Fmt::money);
    private final BarChart chart = new BarChart(v -> Fmt.number(v), v -> Fmt.number(v) + " units", "No usage recorded yet");
    private final Callout alert = new Callout(Glyph.ALERT, Theme.WARNING, " ", 900);
    private Bill latest;

    public HomePage(AppContext app) {
        super(app);
        add(Ui.vbox(6, greeting, Txt.muted("Meter #" + app.account().meterNo() + "  ·  " + Fmt.today())), BorderLayout.NORTH);

        heroBadge.setVisible(false);
        heroAction.setVisible(false);
        heroAction.addActionListener(e -> {
            if (latest != null && latest.status() != BillStatus.PAID) {
                app.go("pay");
            } else if (latest != null) {
                app.go("statement", new Period(latest.month(), latest.year()));
            }
        });
        Card hero = new Card(new BorderLayout()).pad(30, 32, 30, 32)
                .fill(r -> new GradientPaint(r.x, r.y, Theme.BLUE, r.x + r.width, r.y + r.height, Theme.AQUA));
        Txt caps = new Txt("LATEST BILL", Theme.semibold(11).deriveFont(Map.of(TextAttribute.TRACKING, 0.14)),
                () -> new Color(255, 255, 255, 200));
        hero.add(Ui.vbox(8, caps, heroPeriod, heroAmount, Ui.hbox(12, heroBadge, heroNote)), BorderLayout.WEST);
        JPanel action = Ui.clear(new CenterLayout());
        action.add(heroAction);
        hero.add(action, BorderLayout.EAST);

        Card chartCard = new Card(new BorderLayout(0, 18));
        chartCard.add(Ui.vbox(4, Txt.h2("Your usage"), Txt.small("Units consumed per month, last six months")), BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);

        alert.setVisible(false);
        body(Ui.vbox(6, hero, alert, Ui.columns(4, lastUnits, average, paidYear), chartCard));
    }

    private static Txt white(String text, Font font, int alpha) {
        return new Txt(text, font, () -> new Color(255, 255, 255, alpha));
    }

    @Override
    public void onShow(Object param) {
        greeting.setText(Fmt.greeting() + ", " + Fmt.firstName(app.account().name()));
        Tasks.run(this, app.billing::myBills, this::render);
    }

    private void render(List<Bill> bills) {
        latest = bills.isEmpty() ? null : bills.get(0);
        if (latest == null) {
            heroPeriod.setText("No bills yet");
            heroAmount.setText(" ");
            heroNote.setText("Your first bill will appear here once your meter is read.");
            heroBadge.setVisible(false);
            heroAction.setVisible(false);
        } else {
            LocalDate today = Dates.today();
            heroPeriod.setText(Fmt.period(latest.month(), latest.year()));
            heroAmount.setText(Fmt.money(latest.amountDue(today)));
            heroBadge.set(latest, today);
            heroBadge.setVisible(true);
            heroNote.setText(latest.units() + " units" + (latest.dueDate() == null || latest.status() == BillStatus.PAID ? ""
                    : latest.isOverdue(today) ? "  ·  was due " + Fmt.date(latest.dueDate()) : "  ·  due " + Fmt.date(latest.dueDate())));
            boolean unpaid = latest.status() != BillStatus.PAID;
            heroAction.setText(unpaid ? "Pay now" : "View invoice");
            heroAction.setVisible(true);
        }

        lastUnits.setValue(latest == null ? 0 : latest.units(), latest == null ? "No readings yet" : latest.month());
        double avg = bills.stream().mapToInt(Bill::units).average().orElse(0);
        average.setValue(avg, "Across " + bills.size() + (bills.size() == 1 ? " bill" : " bills"));
        int year = Year.now().getValue();
        double paid = bills.stream().filter(b -> b.year() == year && b.status() == BillStatus.PAID)
                .map(Bill::total).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
        long open = bills.stream().filter(b -> b.status() != BillStatus.PAID).count();
        paidYear.setValue(paid, open == 0 ? "Nothing outstanding" : open + " bill" + (open == 1 ? "" : "s") + " still open");

        showAlert(null);
        if (latest != null && !latest.isLegacy()) {
            Bill bill = latest;
            Tasks.run(() -> app.billing.myStatement(bill.month(), bill.year()), st -> {
                if (bill == latest) {
                    showAlert(st.plan());
                }
            }, error -> { });
        }

        chart.setData(Period.lastMonths(6).stream().map(p -> new BarChart.Bar(Fmt.shortMonth(p.month()),
                bills.stream().filter(b -> p.matches(b.month(), b.year())).mapToInt(Bill::units).sum())).toList());
    }

    /** The one thing most worth knowing right now: overdue, due soon, or a dear slab. */
    private void showAlert(TariffPlan plan) {
        LocalDate today = Dates.today();
        Bill b = latest;
        if (b == null) {
            alert.setVisible(false);
            return;
        }
        String period = Fmt.period(b.month(), b.year());
        long daysLeft = b.dueDate() == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(today, b.dueDate());
        if (b.isOverdue(today)) {
            show(Glyph.ALERT, Theme.DANGER, "Your " + period + " bill is overdue",
                    "It was due on " + Fmt.date(b.dueDate()) + ". You now owe " + Fmt.money(b.amountDue(today))
                            + ", including the late fee. Pay soon to avoid disconnection.");
        } else if (b.status() != BillStatus.PAID && daysLeft <= 5) {
            show(Glyph.CLOCK, Theme.WARNING, "Due " + (daysLeft == 0 ? "today" : daysLeft == 1 ? "tomorrow" : "in " + daysLeft + " days"),
                    "Pay " + Fmt.money(b.total()) + " by " + Fmt.date(b.dueDate()) + " to avoid the late fee.");
        } else if (plan != null && plan.billType() == BillType.NORMAL && plan.qualifiesForLifeline(b.units())) {
            show(Glyph.CHECK_CIRCLE, Theme.SUCCESS, "You're on the lifeline rate",
                    "Using " + plan.lifelineMaxUnits() + " units or fewer a month gets every unit at " + Fmt.rate(plan.lifelineRate())
                            + ", the lowest rate there is.");
        } else if (plan != null && plan.billType() == BillType.NORMAL && plan.slabs().size() >= 3
                && plan.slabIndexFor(b.units()) >= plan.slabs().size() - 2) {
            int index = plan.slabIndexFor(b.units());
            show(Glyph.TRENDING_UP, Theme.AMBER, "High usage: your last bill reached the " + plan.slabLabel(index) + " slab",
                    "Units above " + plan.slabs().get(index - 1).uptoUnits() + " cost " + Fmt.rate(plan.slabs().get(index).rate())
                            + " each, against " + Fmt.rate(plan.slabs().get(index - 1).rate()) + " below. "
                            + "The Usage planner shows which appliances push you over.");
        } else {
            alert.setVisible(false);
        }
    }

    private void show(Glyph glyph, java.awt.Color color, String title, String text) {
        alert.look(glyph, color).title(title).lines(text);
        alert.setVisible(true);
        revalidate();
        repaint();
    }
}
