package electricity.billing.system.ui.pages.customer;

import electricity.billing.system.common.Api;
import electricity.billing.system.model.SlabPosition;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Callout;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.Field;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;

/**
 * Plan this month's bill before it arrives:
 * a forecast from today's meter reading with a slab alert, and an appliance
 * calculator that shows what each appliance costs and which ones push the
 * household into a dearer slab.
 */
public class UsagePlannerPage extends Page {

    /** A typical Bangladeshi household appliance and its usual running power. */
    private record Appliance(String name, int watts, int count, double hours) {
    }

    private static final List<Appliance> PRESETS = List.of(
            new Appliance("Ceiling fan", 75, 3, 10),
            new Appliance("LED light", 12, 6, 6),
            new Appliance("Refrigerator (runs about 10 h a day)", 150, 1, 10),
            new Appliance("Air conditioner, 1.5 ton", 1500, 0, 6),
            new Appliance("Television", 100, 1, 4),
            new Appliance("Water pump", 750, 0, 1),
            new Appliance("Rice cooker", 700, 1, 1),
            new Appliance("Iron", 1000, 0, 0.5),
            new Appliance("Computer / laptop", 150, 1, 4),
            new Appliance("Microwave oven", 1200, 0, 0.3),
            new Appliance("Wi-Fi router", 10, 1, 24),
            new Appliance("Electric water heater (geyser)", 2000, 0, 1));
    private static final int DAYS = 30;

    // Forecast
    private final Field reading = Field.text("Today's meter reading", "Number shown on your meter", Glyph.GAUGE);
    private final Btn forecastBtn = new Btn("Forecast my bill", Glyph.TRENDING_UP, Btn.Style.PRIMARY);
    private final Txt forecastTitle = Txt.h2(" ");
    private final Txt forecastText = Txt.muted(" ").wrap(460);
    private final Callout forecastAlert = new Callout(Glyph.ALERT, Theme.WARNING, " ", 440);
    private final JPanel forecastResult;

    // Appliance calculator
    private final List<JSpinner> counts = new ArrayList<>();
    private final List<JSpinner> hours = new ArrayList<>();
    private final Txt totalUnits = Txt.display("0 units");
    private final Txt totalBill = Txt.h2(" ");
    private final Txt slabText = Txt.muted(" ").wrap(380);
    private final JPanel shares = Ui.vbox(8);
    private final Timer debounce;
    private int requestId;

    public UsagePlannerPage(AppContext app) {
        super(app);
        add(header("Usage planner", "See this month's bill coming, and what each appliance costs you."), BorderLayout.NORTH);

        // Forecast card
        forecastBtn.addActionListener(e -> forecast());
        reading.textField().addActionListener(e -> forecast());
        forecastAlert.setVisible(false);
        forecastResult = Ui.vbox(8, forecastTitle, forecastText, forecastAlert);
        forecastResult.setVisible(false);
        Card forecastCard = new Card(new BorderLayout()).pad(24, 26, 24, 26);
        forecastCard.add(Ui.vbox(16,
                Ui.vbox(4, Txt.h2("This month so far"),
                        Txt.small("Read your meter today. We'll project the month from your last bill's reading.").wrap(460)),
                Ui.hbox(12, Ui.width(reading, 280), Ui.top(Ui.pad(Ui.hbox(0, forecastBtn), 26, 0, 0, 0)), Ui.glue()),
                forecastResult), BorderLayout.NORTH);

        // Appliance card
        debounce = new Timer(400, e -> estimate());
        debounce.setRepeats(false);
        JPanel table = Ui.vbox(8);
        JPanel head = Ui.clear(new BorderLayout(12, 0));
        head.add(Txt.caps("Appliance"), BorderLayout.CENTER);
        head.add(Ui.hbox(12, Ui.width(Txt.caps("How many"), 90), Ui.width(Txt.caps("Hours a day"), 90)), BorderLayout.EAST);
        table.add(Ui.left(head));
        for (Appliance a : PRESETS) {
            JSpinner count = spinner(new SpinnerNumberModel(a.count(), 0, 50, 1));
            JSpinner hour = spinner(new SpinnerNumberModel(a.hours(), 0.0, 24.0, 0.5));
            counts.add(count);
            hours.add(hour);
            JPanel row = Ui.clear(new BorderLayout(12, 0));
            row.add(Ui.vbox(2, Txt.body(a.name()), Txt.small(a.watts() + " W")), BorderLayout.CENTER);
            row.add(Ui.hbox(12, count, hour), BorderLayout.EAST);
            table.add(Ui.left(row));
        }
        Card applianceCard = new Card(new BorderLayout(0, 16)).pad(24, 26, 24, 26);
        applianceCard.add(Ui.vbox(4, Txt.h2("Appliance calculator"),
                Txt.small("Set what you run and for how long. Wattages are typical values.")), BorderLayout.NORTH);
        applianceCard.add(table, BorderLayout.CENTER);

        Card resultCard = new Card(new BorderLayout()).pad(24, 26, 24, 26);
        resultCard.add(Ui.vbox(10, Txt.caps("Estimated month (" + DAYS + " days)"), totalUnits, totalBill, slabText,
                Ui.pad(Txt.caps("Who uses the most"), 8, 0, 0, 0), shares), BorderLayout.NORTH);

        JPanel calc = Ui.clear(new BorderLayout(16, 0));
        calc.add(applianceCard, BorderLayout.CENTER);
        calc.add(Ui.top(Ui.fixedWidth(resultCard, 420)), BorderLayout.EAST);
        body(Ui.vbox(16, forecastCard, calc));
    }

    private JSpinner spinner(SpinnerNumberModel model) {
        JSpinner s = new JSpinner(model);
        s.setPreferredSize(new Dimension(90, 36));
        s.addChangeListener(e -> debounce.restart());
        return s;
    }

    @Override
    public void onShow(Object param) {
        estimate();
    }

    // ---- Forecast -----------------------------------------------------------

    private void forecast() {
        int value;
        try {
            value = Integer.parseInt(reading.value());
            if (value < 0) {
                throw new NumberFormatException();
            }
            reading.setError(null);
        } catch (NumberFormatException e) {
            reading.setError("Enter the whole number shown on your meter.");
            return;
        }
        forecastBtn.setBusy(true, "Working it out…");
        Tasks.run(() -> app.billing.forecast(value), f -> {
            forecastBtn.setBusy(false, null);
            showForecast(f);
        }, error -> {
            forecastBtn.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
        });
    }

    private void showForecast(Api.Forecast f) {
        Api.Estimate e = f.estimate();
        forecastTitle.setText("About " + Fmt.number(f.projectedUnits()) + " units and " + Fmt.taka(e.breakdown().payable()) + " this month");
        forecastText.setText(Fmt.number(f.unitsSoFar()) + " units in " + f.daysSoFar() + (f.daysSoFar() == 1 ? " day" : " days")
                + " since your last reading (" + Fmt.number(f.lastReading()) + " on " + Fmt.date(f.lastReadingDate()) + "). "
                + "At this pace a " + f.cycleDays() + "-day month lands in the " + e.slab().slabLabel() + " slab.");
        SlabPosition slab = e.slab();
        if (slab.slabEndsAt() != null) {
            int left = slab.slabEndsAt() - f.unitsSoFar();
            boolean over = f.projectedUnits() > slab.slabEndsAt();
            String budget = f.dailyBudget() == null ? "" : " Keep to about " + f.dailyBudget() + " units a day to stay below it.";
            String what = slab.lifeline() ? "Past " + slab.slabEndsAt() + " units you lose the lifeline rate and every unit is re-priced."
                    : "Units past " + slab.slabEndsAt() + " cost " + Fmt.rate(slab.nextRate()) + " each instead of " + Fmt.rate(slab.rate()) + ".";
            forecastAlert.look(over ? Glyph.ALERT : Glyph.INFO, over ? Theme.WARNING : Theme.BLUE)
                    .title(left > 0 ? left + " units left before the next slab" : "You've reached the next slab")
                    .lines(what + budget);
            forecastAlert.setVisible(true);
        } else {
            forecastAlert.setVisible(false);
        }
        forecastResult.setVisible(true);
        revalidate();
        repaint();
    }

    // ---- Appliance calculator -----------------------------------------------

    private double kwhFor(int i) {
        Appliance a = PRESETS.get(i);
        int count = ((Number) counts.get(i).getValue()).intValue();
        double h = ((Number) hours.get(i).getValue()).doubleValue();
        return a.watts() * count * h * DAYS / 1000.0;
    }

    private void estimate() {
        double kwh = 0;
        for (int i = 0; i < PRESETS.size(); i++) {
            kwh += kwhFor(i);
        }
        int units = (int) Math.round(kwh);
        totalUnits.setText(Fmt.number(units) + " units");
        int id = ++requestId;
        Tasks.run(() -> app.billing.estimate(units), e -> {
            if (id == requestId) {
                showEstimate(units, e);
            }
        }, error -> {
            if (id == requestId) {
                totalBill.setText(Tasks.message(error));
            }
        });
    }

    private void showEstimate(int units, Api.Estimate e) {
        BigDecimal bill = e.breakdown().payable();
        totalBill.setText("≈ " + Fmt.taka(bill) + " a month");
        SlabPosition slab = e.slab();
        String text = "Lands in the " + slab.slabLabel() + " slab (" + Fmt.rate(slab.rate()) + " a unit).";
        if (slab.slabEndsAt() != null) {
            text += " " + Math.max(0, slab.slabEndsAt() - units) + " units of headroom before "
                    + (slab.lifeline() ? "you lose the lifeline rate." : Fmt.rate(slab.nextRate()) + " a unit.");
        }
        slabText.setText(text);

        // Each appliance's share of the energy charge, biggest first
        BigDecimal energy = e.breakdown().energy();
        List<int[]> order = new ArrayList<>();
        for (int i = 0; i < PRESETS.size(); i++) {
            if (kwhFor(i) > 0) {
                order.add(new int[] {i});
            }
        }
        order.sort(Comparator.comparingDouble((int[] o) -> kwhFor(o[0])).reversed());
        shares.removeAll();
        for (int[] o : order.subList(0, Math.min(5, order.size()))) {
            double k = kwhFor(o[0]);
            BigDecimal cost = units == 0 ? BigDecimal.ZERO
                    : energy.multiply(BigDecimal.valueOf(k / units)).setScale(0, RoundingMode.HALF_UP);
            JPanel row = Ui.clear(new BorderLayout(10, 0));
            row.add(Ui.vbox(2, Txt.body(PRESETS.get(o[0]).name()), Txt.small(Fmt.number(k) + " units")), BorderLayout.CENTER);
            row.add(Ui.top(Txt.h3("≈ " + Fmt.taka(cost))), BorderLayout.EAST);
            shares.add(Ui.left(row));
        }
        if (order.isEmpty()) {
            shares.add(Ui.left(Txt.small("Add some appliances to see where your money goes.")));
        }
        shares.revalidate();
        shares.repaint();
    }
}
