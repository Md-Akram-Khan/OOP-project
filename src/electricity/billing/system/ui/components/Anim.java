package electricity.billing.system.ui.components;

import java.util.function.DoubleConsumer;
import javax.swing.Timer;

/** Tiny animation helper: runs a callback every frame with eased progress from 0 to 1. */
public final class Anim {

    private static final int FRAME_MS = 15;

    private Anim() {
    }

    public static Timer run(int durationMs, DoubleConsumer frame) {
        return run(durationMs, frame, null);
    }

    public static Timer run(int durationMs, DoubleConsumer frame, Runnable done) {
        long start = System.nanoTime();
        Timer timer = new Timer(FRAME_MS, null);
        timer.addActionListener(e -> {
            double t = Math.min(1, (System.nanoTime() - start) / 1e6 / durationMs);
            frame.accept(easeOut(t));
            if (t >= 1) {
                timer.stop();
                if (done != null) {
                    done.run();
                }
            }
        });
        frame.accept(0);
        timer.start();
        return timer;
    }

    public static void stop(Timer timer) {
        if (timer != null) {
            timer.stop();
        }
    }

    public static double easeOut(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    public static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
