package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.common.Dates;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.Customer;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.Period;
import electricity.billing.system.ui.components.BarChart;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.DataTable;
import electricity.billing.system.ui.components.StatCard;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.JFileChooser;
import javax.swing.JScrollPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Collections at a glance: what was billed and collected, how old the unpaid
 * money is (aging buckets, the way utilities chase arrears), and who owes the
 * most. Everything can be exported to CSV for Excel.
 */
public class ReportsPage extends Page {

    private record Data(List<Customer> customers, List<Bill> bills) {
    }

    private record Debtor(String name, String meterNo, int bills, BigDecimal owed, LocalDate oldestDue) {
    }

    private final StatCard billed = new StatCard("Billed this year", Glyph.RECEIPT, Theme.BLUE, Fmt::moneyShort);
    private final StatCard collected = new StatCard("Collected this year", Glyph.WALLET, Theme.SUCCESS, Fmt::moneyShort);
    private final StatCard rate = new StatCard("Collection rate", Glyph.TRENDING_UP, Theme.AQUA, v -> Math.round(v) + "%");
    private final StatCard overdue = new StatCard("Overdue now", Glyph.ALERT, Theme.DANGER, Fmt::moneyShort);
    private final BarChart aging = new BarChart(Fmt::moneyShort, Fmt::money, "Nothing is outstanding");
    private final BarChart monthly = new BarChart(v -> Math.round(v) + "%", v -> Math.round(v) + "% collected",
            "No bills in the last six months yet");
    private final DataTable debtors = new DataTable("Customer", "Meter", "Overdue bills", "Owed now", "Oldest due date");
    private Data data;

    public ReportsPage(AppContext app) {
        super(app);
        Btn export = new Btn("Export bills (CSV)", Glyph.FILE_TEXT, Btn.Style.PRIMARY);
        export.addActionListener(e -> exportCsv());
        add(header("Reports", "Collections, arrears by age, and the customers who owe the most.", export), BorderLayout.NORTH);

        Card agingCard = new Card(new BorderLayout(0, 18));
        agingCard.add(Ui.vbox(4, Txt.h2("Arrears by age"), Txt.small("Unpaid money by days past the due date")), BorderLayout.NORTH);
        agingCard.add(aging, BorderLayout.CENTER);
        Card monthlyCard = new Card(new BorderLayout(0, 18));
        monthlyCard.add(Ui.vbox(4, Txt.h2("Collection rate"), Txt.small("Share of each month's billing that has been paid")),
                BorderLayout.NORTH);
        monthlyCard.add(monthly, BorderLayout.CENTER);

        debtors.column(0, new DataTable.NameRenderer(), 240);
        debtors.column(3, new DataTable.MoneyRenderer(), 150);
        JScrollPane scroll = debtors.scroll();
        scroll.setPreferredSize(new Dimension(10, 42 + 48 * 6));
        Card debtorsCard = new Card(new BorderLayout(0, 14));
        debtorsCard.add(Ui.vbox(4, Txt.h2("Top defaulters"), Txt.small("Customers with overdue bills, largest amount first")),
                BorderLayout.NORTH);
        debtorsCard.add(scroll, BorderLayout.CENTER);

        body(Ui.vbox(6,
                Ui.columns(4, billed, collected, rate, overdue),
                Ui.columns(16, agingCard, monthlyCard),
                debtorsCard));
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, () -> new Data(app.customers.findAll(), app.billing.search(null, null, null)), this::render);
    }

    private void render(Data d) {
        data = d;
        LocalDate today = Dates.today();
        int year = today.getYear();
        List<Bill> bills = d.bills();
        Map<String, String> names = d.customers().stream().collect(Collectors.toMap(Customer::meterNo, Customer::name, (a, b) -> a));

        BigDecimal billedYear = sum(bills.stream().filter(b -> b.year() == year).toList(), Bill::total);
        BigDecimal paidYear = sum(bills.stream().filter(b -> b.year() == year && b.status() == BillStatus.PAID).toList(), Bill::total);
        List<Bill> late = bills.stream().filter(b -> b.isOverdue(today)).toList();
        billed.setValue(billedYear.doubleValue(), bills.stream().filter(b -> b.year() == year).count() + " bills in " + year);
        collected.setValue(paidYear.doubleValue(), Fmt.money(billedYear.subtract(paidYear)) + " still to collect");
        rate.setValue(billedYear.signum() == 0 ? 0 : paidYear.doubleValue() * 100 / billedYear.doubleValue(), "Paid ÷ billed, " + year);
        overdue.setValue(sum(late, b -> b.amountDue(today)).doubleValue(), late.size() + (late.size() == 1 ? " bill" : " bills")
                + " past the due date");

        // Aging buckets
        Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        for (String key : List.of("Not due", "1–30", "31–60", "61–90", "90+")) {
            buckets.put(key, BigDecimal.ZERO);
        }
        BigDecimal noDate = BigDecimal.ZERO;
        for (Bill b : bills) {
            if (b.status() != BillStatus.NOT_PAID) {
                continue;
            }
            if (b.dueDate() == null) {
                noDate = noDate.add(b.total());
                continue;
            }
            long days = ChronoUnit.DAYS.between(b.dueDate(), today);
            String key = days <= 0 ? "Not due" : days <= 30 ? "1–30" : days <= 60 ? "31–60" : days <= 90 ? "61–90" : "90+";
            buckets.merge(key, b.amountDue(today), BigDecimal::add);
        }
        if (noDate.signum() > 0) {
            buckets.put("Old bills", noDate);
        }
        aging.setData(buckets.entrySet().stream().map(e -> new BarChart.Bar(e.getKey(), e.getValue().doubleValue())).toList());

        monthly.setData(Period.lastMonths(6).stream().map(p -> {
            List<Bill> inMonth = bills.stream().filter(b -> p.matches(b.month(), b.year())).toList();
            BigDecimal total = sum(inMonth, Bill::total);
            BigDecimal paid = sum(inMonth.stream().filter(b -> b.status() == BillStatus.PAID).toList(), Bill::total);
            return new BarChart.Bar(Fmt.shortMonth(p.month()), total.signum() == 0 ? 0 : paid.doubleValue() * 100 / total.doubleValue());
        }).toList());

        List<Debtor> list = new ArrayList<>();
        late.stream().collect(Collectors.groupingBy(Bill::meterNo)).forEach((meter, owed) -> list.add(new Debtor(
                names.getOrDefault(meter, meter), meter, owed.size(), sum(owed, b -> b.amountDue(today)),
                owed.stream().map(Bill::dueDate).min(Comparator.naturalOrder()).orElse(null))));
        list.sort(Comparator.comparing(Debtor::owed).reversed());
        debtors.clear();
        for (Debtor x : list) {
            debtors.addRow(x.name(), x.meterNo(), x.bills(), x.owed(), Fmt.date(x.oldestDue()));
        }
        if (list.isEmpty()) {
            debtors.addRow("No overdue bills", "", "", "", "");
        }
    }

    private static BigDecimal sum(List<Bill> bills, Function<Bill, BigDecimal> amount) {
        return bills.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void exportCsv() {
        if (data == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export bills");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV file (Excel)", "csv"));
        chooser.setSelectedFile(new File("bills-" + Dates.today() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }
        Map<String, String> names = data.customers().stream().collect(Collectors.toMap(Customer::meterNo, Customer::name, (a, b) -> a));
        LocalDate today = Dates.today();
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Meter,Customer,Month,Year,Previous reading,Current reading,Units,Energy,Demand charge,VAT,Meter rent,"
                + "Total by due date,Total after due date,Due date,Status,Overdue\r\n");
        for (Bill b : data.bills()) {
            var ch = b.charges();
            csv.append(String.join(",", cell(b.meterNo()), cell(names.getOrDefault(b.meterNo(), "")), cell(b.month()),
                    String.valueOf(b.year()), num(b.previousReading()), num(b.currentReading()), String.valueOf(b.units()),
                    money(ch == null ? null : ch.energy()), money(ch == null ? null : ch.demand()), money(ch == null ? null : ch.vat()),
                    money(ch == null ? null : ch.meterRent()), money(b.total()), money(b.totalAfterDue()),
                    b.dueDate() == null ? "" : b.dueDate().toString(), cell(b.status().label()), b.isOverdue(today) ? "Yes" : "No"))
                    .append("\r\n");
        }
        try {
            Files.writeString(file.toPath(), csv, StandardCharsets.UTF_8);
            Toast.success(this, "Exported " + data.bills().size() + " bills to " + file.getName() + ".");
        } catch (IOException e) {
            Toast.error(this, "Couldn't save the file: " + e.getMessage());
        }
    }

    /** Quotes a text cell, and stops spreadsheet formulas sneaking in through names. */
    private static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static String num(Integer value) {
        return value == null ? "" : value.toString();
    }

    private static String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
