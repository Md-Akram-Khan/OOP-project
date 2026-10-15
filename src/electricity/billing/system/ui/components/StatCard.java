package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.function.DoubleFunction;
import javax.swing.Timer;

/** A dashboard tile: icon, label, a number that counts up when it changes, and a footnote. */
public class StatCard extends Card {

    private final Txt value = Txt.display("—");
    private final Txt note = Txt.small(" ");
    private final DoubleFunction<String> format;
    private double shown;
    private Timer anim;

    public StatCard(String label, Glyph glyph, Color color, DoubleFunction<String> format) {
        super(new BorderLayout());
        this.format = format;
        hoverable();
        pad(18, 20, 18, 20);
        value.setFont(Theme.semibold(28));
        add(Ui.vbox(14,
                Ui.hbox(12, new IconTile(glyph, 40, color), Txt.muted(label)),
                Ui.vbox(4, value, note)), BorderLayout.CENTER);
    }

    public void setValue(double target, String footnote) {
        note.setText(footnote == null || footnote.isEmpty() ? " " : footnote);
        Anim.stop(anim);
        double from = shown;
        anim = Anim.run(900, t -> {
            shown = Anim.lerp(from, target, t);
            value.setText(format.apply(t >= 1 ? target : shown));
        });
    }
}
