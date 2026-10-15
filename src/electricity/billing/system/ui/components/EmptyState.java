package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.Component;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** A friendly centred message for when there's nothing to show yet. */
public final class EmptyState {

    private EmptyState() {
    }

    public static JPanel of(Glyph glyph, String title, String text, JComponent... actions) {
        JPanel center = Ui.clear(new CenterLayout());
        JPanel stack = Ui.vbox(10, new IconTile(glyph, 56, Theme.BLUE), Txt.h3(title), Txt.muted(text).wrap(300));
        for (Component c : stack.getComponents()) {
            if (c instanceof JComponent j) {
                j.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }
        if (actions.length > 0) {
            JPanel row = Ui.hbox(10, actions);
            row.setAlignmentX(Component.CENTER_ALIGNMENT);
            stack.add(javax.swing.Box.createVerticalStrut(8));
            stack.add(row);
        }
        center.add(stack);
        return center;
    }
}
