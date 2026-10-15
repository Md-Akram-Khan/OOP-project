package electricity.billing.system.ui;

import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import java.awt.BorderLayout;
import java.awt.Dimension;
import electricity.billing.system.ui.components.CenterLayout;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;

/**
 * One screen inside the main window. Pages put their header at the top
 * (NORTH) and their content below (CENTER), and reload data in onShow.
 */
public abstract class Page extends JPanel implements Scrollable {

    protected final AppContext app;

    protected Page(AppContext app) {
        super(new BorderLayout(0, 24));
        this.app = app;
        setOpaque(false);
        setBorder(new EmptyBorder(30, 34, 34, 34));
    }

    /** Called every time the page is opened. */
    public void onShow(Object param) {
    }

    /** Pages with their own scrolling table return false and fill the window instead. */
    public boolean scrolls() {
        return true;
    }

    /** Title and subtitle on the left, action buttons on the right. */
    protected JPanel header(String title, String subtitle, JComponent... actions) {
        JPanel header = Ui.clear(new BorderLayout(16, 0));
        header.add(Ui.vbox(6, Txt.h1(title), Txt.muted(subtitle)), BorderLayout.WEST);
        if (actions.length > 0) {
            JPanel right = Ui.clear(new CenterLayout());
            right.add(Ui.hbox(10, actions));
            header.add(right, BorderLayout.EAST);
        }
        return header;
    }

    /** Places the content under the header; on scrolling pages it keeps its natural height. */
    protected void body(JComponent content) {
        if (scrolls()) {
            JPanel top = Ui.clear(new BorderLayout());
            top.add(content, BorderLayout.NORTH);
            add(top, BorderLayout.CENTER);
        } else {
            add(content, BorderLayout.CENTER);
        }
    }

    // Scrollable: follow the viewport's width so content wraps instead of overflowing.

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 18;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return visible.height - 40;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
    }
}
