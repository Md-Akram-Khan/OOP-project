package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.model.Slab;
import electricity.billing.system.model.TariffPlan;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Badge;
import electricity.billing.system.ui.components.Callout;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;

/**
 * The tariffs bills are priced with, as stored in the database: slabs,
 * lifeline band, demand charge, VAT, late fee and meter rent, with where
 * each figure came from.
 */
public class TariffsPage extends Page {

    private final JPanel list = Ui.vbox(16);

    public TariffsPage(AppContext app) {
        super(app);
        add(header("Tariffs", "The BERC rates bills are priced with. Each bill keeps the plan it was made with."), BorderLayout.NORTH);
        Callout how = new Callout(Glyph.INFO, Theme.BLUE, "Changing a tariff", 900).lines(
                "Rates live in the tariff_plan and tariff_slab tables, not in the code. When BERC publishes a new order, add a new "
                        + "plan with its effective date (see database/migration-v4-bd-billing.sql). Bills for that month onwards use it; "
                        + "older bills keep their old plan.");
        body(Ui.vbox(16, how, list));
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, app.billing::tariffs, this::render);
    }

    private void render(List<TariffPlan> plans) {
        list.removeAll();
        for (TariffPlan p : plans) {
            list.add(planCard(p));
        }
        if (plans.isEmpty()) {
            list.add(Ui.left(Txt.muted("No tariffs found. Run database/migration-v4-bd-billing.sql.")));
        }
        list.revalidate();
        list.repaint();
    }

    private JPanel planCard(TariffPlan p) {
        Card card = new Card(new BorderLayout(24, 0)).pad(24, 26, 24, 26);
        JPanel slabs = Ui.vbox(8, Ui.left(Txt.caps("Energy rate per unit (kWh)")));
        if (p.hasLifeline()) {
            slabs.add(row("Lifeline 0–" + p.lifelineMaxUnits() + " (whole month)", Fmt.rate(p.lifelineRate())));
        }
        for (int i = 0; i < p.slabs().size(); i++) {
            Slab s = p.slabs().get(i);
            slabs.add(row(p.slabs().size() == 1 ? "All units" : p.slabLabel(i) + " units", Fmt.rate(s.rate())));
        }
        JPanel charges = Ui.vbox(8, Ui.left(Txt.caps("Other charges")),
                row("Demand charge", Fmt.rate(p.demandRatePerKw()) + " per kW / month"),
                row("VAT", Fmt.percent(p.vatPercent()) + " of energy + demand"),
                row("Late fee", Fmt.percent(p.lateFeePercent()) + " of energy + demand"),
                row("Meter rent", Fmt.rate(p.meterRent()) + " / month"),
                row("Due", p.dueDays() + " days after the bill date"));
        JPanel head = Ui.vbox(6,
                Ui.hbox(10, Txt.h2(p.name()), new Badge(p.billType().displayName(), Theme.BLUE)),
                Txt.small("In force from " + Fmt.date(p.effectiveFrom()) + "  ·  " + p.billType().description()),
                Txt.small(p.source() == null ? "" : "Source: " + p.source()).wrap(900));
        card.add(head, BorderLayout.NORTH);
        JPanel cols = Ui.columns(40, Ui.top(slabs), Ui.top(charges));
        cols.setBorder(new javax.swing.border.EmptyBorder(18, 0, 0, 0));
        card.add(cols, BorderLayout.CENTER);
        return card;
    }

    private static JPanel row(String label, String value) {
        JPanel r = Ui.clear(new BorderLayout());
        r.add(Txt.body(label), BorderLayout.WEST);
        r.add(Txt.h3(value), BorderLayout.EAST);
        return r;
    }
}
