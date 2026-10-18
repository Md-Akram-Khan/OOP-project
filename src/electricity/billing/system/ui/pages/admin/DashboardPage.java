package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.common.Dates;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.Complaint;
import electricity.billing.system.model.Customer;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.Period;
import electricity.billing.system.ui.components.Avatar;
import electricity.billing.system.ui.components.Badge;
import electricity.billing.system.ui.components.BarChart;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.DataTable;
import electricity.billing.system.ui.components.IconTile;
import electricity.billing.system.ui.components.StatCard;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import electricity.billing.system.ui.components.CenterLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** The admin's overview: key numbers, billing trend, payments to confirm and the latest bills. */
public class DashboardPage extends Page {

    private record Data(List<Customer> customers, List<Bill> bills, List<Complaint> complaints) {
    }

    private final StatCard customersCard = new StatCard("Customers", Glyph.USERS, Theme.BLUE, Fmt::number);
    private final StatCard billsCard = new StatCard("Bills this month", Glyph.RECEIPT, Theme.AQUA, Fmt::number);
    private final StatCard outstandingCard = new StatCard("Outstanding", Glyph.WALLET, Theme.AMBER, Fmt::moneyShort);
    private final StatCard pendingCard = new StatCard("Open complaints", Glyph.MAIL, Theme.VIOLET, Fmt::number);
    private final BarChart chart = new BarChart(Fmt::moneyShort, Fmt::money, "No bills in the last six months yet");
    private final JPanel attention = Ui.clear(new BorderLayout());
    private final DataTable recent = new DataTable("Customer", "Period", "Amount", "Status");
    private final Txt greeting = Txt.h1(" ");

    public DashboardPage(AppContext app) {
        super(app);
        Btn newBill = new Btn("New bill", Glyph.PLUS, Btn.Style.PRIMARY);
        newBill.addActionListener(e -> app.go("calculate"));
        Btn newCustomer = new Btn("Add customer", Glyph.USER_PLUS, Btn.Style.SECONDARY);
        newCustomer.addActionListener(e -> app.go("add-customer"));

        JPanel header = Ui.clear(new BorderLayout());
        header.add(Ui.vbox(6, greeting, Txt.muted(Fmt.today() + "  ·  Here's what's happening across your grid.")), BorderLayout.WEST);
        JPanel actions = Ui.clear(new CenterLayout());
        actions.add(Ui.hbox(10, newCustomer, newBill));
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        Card chartCard = new Card(new BorderLayout(0, 18));
        chartCard.add(Ui.vbox(4, Txt.h2("Billing overview"), Txt.small("Total billed per month, last six months")), BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);

        Card attentionCard = new Card(new BorderLayout(0, 16));
        attentionCard.add(Ui.vbox(4, Txt.h2("Needs attention"), Txt.small("Overdue bills and open complaints")), BorderLayout.NORTH);
        attentionCard.add(attention, BorderLayout.CENTER);

        JPanel middle = Ui.clear(new BorderLayout(16, 0));
        middle.add(chartCard, BorderLayout.CENTER);
        middle.add(Ui.fixedWidth(attentionCard, 380), BorderLayout.EAST);

        Btn viewAll = new Btn("View all", Glyph.ARROW_RIGHT, Btn.Style.GHOST).trailingIcon();
        viewAll.addActionListener(e -> app.go("bills"));
        Card recentCard = new Card(new BorderLayout(0, 14));
        JPanel recentHeader = Ui.clear(new BorderLayout());
        recentHeader.add(Ui.vbox(4, Txt.h2("Recent bills"), Txt.small("The latest bills across all customers")), BorderLayout.WEST);
        recentHeader.add(viewAll, BorderLayout.EAST);
        recentCard.add(recentHeader, BorderLayout.NORTH);
        recent.column(0, new DataTable.NameRenderer(), 260);
        recent.column(2, new DataTable.MoneyRenderer(), 140);
        recent.column(3, new DataTable.StatusRenderer(), 160);
        JScrollPane scroll = recent.scroll();
        scroll.setPreferredSize(new Dimension(10, 42 + 48 * 6));
        recentCard.add(scroll, BorderLayout.CENTER);

        body(Ui.vbox(6,
                Ui.columns(4, customersCard, billsCard, outstandingCard, pendingCard),
                middle,
                recentCard));
    }

    @Override
    public void onShow(Object param) {
        greeting.setText(Fmt.greeting() + ", " + Fmt.firstName(app.account().name()));
        Tasks.run(this, () -> new Data(app.customers.findAll(), app.billing.search(null, null, null), app.support.complaints()),
                this::render);
    }

    private void render(Data data) {
        Map<String, String> names = data.customers().stream()
                .collect(Collectors.toMap(Customer::meterNo, Customer::name, (a, b) -> a));
        Period now = Period.current();
        List<Bill> bills = data.bills();

        long thisMonth = bills.stream().filter(b -> now.matches(b.month(), b.year())).count();
        LocalDate today = Dates.today();
        double outstanding = bills.stream().filter(b -> b.status() != BillStatus.PAID)
                .map(b -> b.amountDue(today)).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
        long overdue = bills.stream().filter(b -> b.isOverdue(today)).count();
        List<Bill> late = bills.stream().filter(b -> b.isOverdue(today))
                .sorted(java.util.Comparator.comparing(Bill::dueDate)).toList();
        List<Complaint> open = data.complaints().stream().filter(c -> c.status() != Complaint.Status.RESOLVED).toList();
        long unpaid = bills.stream().filter(b -> b.status() != BillStatus.PAID).count();

        customersCard.setValue(data.customers().size(), data.customers().isEmpty() ? "Add your first customer" : "Connected meters");
        billsCard.setValue(thisMonth, now.toString());
        outstandingCard.setValue(outstanding, unpaid + " unpaid · " + overdue + " overdue");
        long unanswered = open.stream().filter(c -> c.status() == Complaint.Status.OPEN).count();
        pendingCard.setValue(open.size(), open.isEmpty() ? "All caught up" : unanswered + " not answered yet");

        Function<Period, Double> totalFor = p -> bills.stream().filter(b -> p.matches(b.month(), b.year()))
                .map(Bill::total).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
        chart.setData(Period.lastMonths(6).stream()
                .map(p -> new BarChart.Bar(Fmt.shortMonth(p.month()), totalFor.apply(p))).toList());

        renderAttention(late, open, names, today);

        recent.clear();
        bills.stream().limit(6).forEach(b -> recent.addRow(names.getOrDefault(b.meterNo(), b.meterNo()),
                Fmt.period(b.month(), b.year()), b.amountDue(today), Badge.Label.of(b, today)));
    }

    private void renderAttention(List<Bill> late, List<Complaint> complaints, Map<String, String> names, LocalDate today) {
        attention.removeAll();
        if (late.isEmpty() && complaints.isEmpty()) {
            JPanel done = Ui.clear(new CenterLayout());
            JPanel stack = Ui.vbox(10, new IconTile(Glyph.CHECK_CIRCLE, 52, Theme.SUCCESS), Txt.h3("All caught up"),
                    Txt.small("No overdue bills and no open complaints."));
            stack.getComponents();
            for (java.awt.Component c : stack.getComponents()) {
                if (c instanceof javax.swing.JComponent j) {
                    j.setAlignmentX(CENTER_ALIGNMENT);
                }
            }
            done.add(stack);
            attention.add(done, BorderLayout.CENTER);
        } else {
            JPanel list = Ui.vbox(10);
            for (Bill bill : late.stream().limit(4).toList()) {
                String name = names.getOrDefault(bill.meterNo(), bill.meterNo());
                JPanel row = Ui.clear(new BorderLayout(12, 0));
                row.add(new Avatar(name, 36), BorderLayout.WEST);
                row.add(Ui.vbox(2, Txt.h3(name), Txt.small(Fmt.money(bill.amountDue(today)) + "  ·  "
                        + Fmt.shortMonth(bill.month()) + "  ·  due " + Fmt.date(bill.dueDate()))), BorderLayout.CENTER);
                row.add(Ui.top(Badge.of(bill, today)), BorderLayout.EAST);
                list.add(Ui.left(row));
                list.add(javax.swing.Box.createVerticalStrut(12));
            }
            if (late.size() > 4) {
                list.add(Ui.left(Txt.small("+ " + (late.size() - 4) + " more overdue in Bills & payments")));
                list.add(javax.swing.Box.createVerticalStrut(12));
            }
            if (!complaints.isEmpty()) {
                Btn view = new Btn("View", Glyph.ARROW_RIGHT, Btn.Style.SECONDARY).trailingIcon();
                view.addActionListener(e -> app.go("complaints"));
                long fresh = complaints.stream().filter(c -> c.status() == Complaint.Status.OPEN).count();
                JPanel row = Ui.clear(new BorderLayout(12, 0));
                row.add(new IconTile(Glyph.MAIL, 36, Theme.DANGER), BorderLayout.WEST);
                row.add(Ui.vbox(2, Txt.h3(complaints.size() + (complaints.size() == 1 ? " open complaint" : " open complaints")),
                        Txt.small(fresh + " not answered yet")), BorderLayout.CENTER);
                row.add(view, BorderLayout.EAST);
                list.add(Ui.left(row));
            }
            attention.add(list, BorderLayout.NORTH);
        }
        attention.revalidate();
        attention.repaint();
    }
}
