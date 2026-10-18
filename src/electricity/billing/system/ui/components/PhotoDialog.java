package electricity.billing.system.ui.components;

import electricity.billing.system.common.Api;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.Base64;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Shows the meter photo a bill was read from. */
public final class PhotoDialog {

    private PhotoDialog() {
    }

    public static void show(Component owner, String title, Api.MeterPhoto photo) {
        BufferedImage image = Photos.decode(Base64.getDecoder().decode(photo.image()));
        Window window = SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(window, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel content = Ui.themed(new BorderLayout(0, 12), Theme::bg);
        content.setBorder(new EmptyBorder(18, 18, 18, 18));
        content.add(new JLabel(Photos.icon(image, 860, 600)), BorderLayout.CENTER);
        content.add(Ui.vbox(4,
                Txt.h3("Display read as " + photo.displayText() + " kWh"),
                Txt.small("Uploaded by " + photo.uploadedBy() + " on " + Fmt.dateTime(photo.uploadedAt())
                        + ". The reading was taken from this photo by the server, not typed in.")), BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(window);
        dialog.setVisible(true);
    }
}
