package electricity.billing.system.ui.components;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Centres its child at the child's preferred size, shrinking only as far as
 * the space requires. (GridBagLayout instead collapses everything to minimum
 * size the moment it runs short of room.)
 */
public class CenterLayout implements LayoutManager {

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        int w = 0;
        int h = 0;
        for (Component c : parent.getComponents()) {
            if (c.isVisible()) {
                Dimension p = c.getPreferredSize();
                w = Math.max(w, p.width);
                h = Math.max(h, p.height);
            }
        }
        return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        return new Dimension(in.left + in.right, in.top + in.bottom);
    }

    @Override
    public void layoutContainer(Container parent) {
        Insets in = parent.getInsets();
        int availW = parent.getWidth() - in.left - in.right;
        int availH = parent.getHeight() - in.top - in.bottom;
        for (Component c : parent.getComponents()) {
            Dimension p = c.getPreferredSize();
            int w = Math.min(p.width, availW);
            int h = Math.min(p.height, availH);
            c.setBounds(in.left + (availW - w) / 2, in.top + (availH - h) / 2, w, h);
        }
    }
}
