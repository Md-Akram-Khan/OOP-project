package electricity.billing.system;

import electricity.billing.system.model.Months;
import electricity.billing.system.common.AppException;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Image;
import java.net.URL;
import java.time.Year;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/** Shared look and behaviour for every window in the app. */
abstract class BaseFrame extends JFrame {

    static final Color PANEL_BLUE = new Color(173, 216, 230);
    static final Color HEADING_BLUE = new Color(72, 139, 190);
    static final int YEARS_OF_HISTORY = 5;

    BaseFrame(String title) {
        this(title, 730, 550);
    }

    BaseFrame(String title, int width, int height) {
        super(title);
        // Closing a window releases it; the app exits once the last window is closed.
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setBounds(400, 200, width, height);
        getContentPane().setBackground(Color.WHITE);
    }

    /**
     * Runs a database task off the Swing event thread so the window never
     * freezes, then hands the result to {@code onSuccess} back on the event
     * thread. The given components are disabled while the task runs, which
     * also stops double submits. Failures are shown in a dialog.
     */
    <T> void runAsync(Callable<T> task, Consumer<T> onSuccess, Component... busy) {
        for (Component component : busy) {
            component.setEnabled(false);
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                for (Component component : busy) {
                    component.setEnabled(true);
                }
                setCursor(Cursor.getDefaultCursor());
                T result;
                try {
                    result = get();
                } catch (ExecutionException e) {
                    showError(e.getCause());
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                onSuccess.accept(result);
            }
        }.execute();
    }

    void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, getTitle(), JOptionPane.INFORMATION_MESSAGE);
    }

    void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, getTitle(), JOptionPane.WARNING_MESSAGE);
    }

    void showError(Throwable error) {
        if (error instanceof AppException) {
            showWarning(error.getMessage());
            return;
        }
        error.printStackTrace();
        JOptionPane.showMessageDialog(this,
                "Something went wrong:\n" + error.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    static JButton darkButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(Color.BLACK);
        button.setForeground(Color.WHITE);
        return button;
    }

    /** Months January..December, with the current month selected. */
    static Choice monthChoice() {
        Choice choice = new Choice();
        Months.NAMES.forEach(choice::add);
        choice.select(Months.current());
        return choice;
    }

    /** The current year and the few before it, newest first. */
    static Choice yearChoice() {
        Choice choice = new Choice();
        int now = Year.now().getValue();
        for (int year = now; year >= now - YEARS_OF_HISTORY; year--) {
            choice.add(String.valueOf(year));
        }
        return choice;
    }

    static ImageIcon icon(String file) {
        URL url = BaseFrame.class.getResource("/icon/" + file);
        return url == null ? new ImageIcon() : new ImageIcon(url);
    }

    static ImageIcon icon(String file, int width, int height) {
        return new ImageIcon(icon(file).getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }
}
