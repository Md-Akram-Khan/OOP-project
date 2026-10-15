package electricity.billing.system.ui.components;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Shows one component at a time; each new one fades in while sliding up a few pixels. */
public class FadeHost extends JPanel {

    private Component current;
    private float alpha = 1f;
    private int offset;
    private Timer anim;

    public FadeHost() {
        super(new BorderLayout());
        setOpaque(false);
    }

    public void show(Component next) {
        if (next == current) {
            return;
        }
        if (current != null) {
            remove(current);
        }
        current = next;
        add(next, BorderLayout.CENTER);
        revalidate();
        Anim.stop(anim);
        anim = Anim.run(320, t -> {
            alpha = (float) t;
            offset = (int) Math.round(16 * (1 - t));
            repaint();
        });
    }

    @Override
    public void paint(Graphics g) {
        if (alpha >= 1f && offset == 0) {
            super.paint(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha))));
        g2.translate(0, offset);
        super.paint(g2);
        g2.dispose();
    }
}
