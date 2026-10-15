package electricity.billing.system.ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.Insets;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** A labelled form input with an optional leading icon and an inline error message. */
public class Field extends JPanel {

    public static final int INPUT_HEIGHT = 44;

    private final JComponent input;
    private final Txt error = new Txt(" ", Theme.regular(12), () -> Theme.DANGER);

    private Field(String label, JComponent input) {
        this.input = input;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        Txt caption = new Txt(label, Theme.semibold(13), Theme::text);
        add(Ui.left(caption));
        add(javax.swing.Box.createVerticalStrut(7));
        add(Ui.left(Ui.fixedHeight(input)));
        add(javax.swing.Box.createVerticalStrut(4));
        error.setVisible(false);
        add(Ui.left(error));
    }

    public static Field text(String label, String placeholder, Glyph glyph) {
        return new Field(label, style(new JTextField() {
            @Override
            public java.awt.Dimension getPreferredSize() {
                return tall(super.getPreferredSize());
            }
        }, placeholder, glyph));
    }

    public static Field password(String label, String placeholder) {
        return new Field(label, style(new JPasswordField() {
            @Override
            public java.awt.Dimension getPreferredSize() {
                return tall(super.getPreferredSize());
            }
        }, placeholder, Glyph.LOCK));
    }

    public static Field of(String label, JComponent input) {
        input.putClientProperty(FlatClientProperties.MINIMUM_HEIGHT, INPUT_HEIGHT);
        return new Field(label, input);
    }

    public static <T> JComboBox<T> combo() {
        JComboBox<T> box = new JComboBox<>() {
            @Override
            public java.awt.Dimension getPreferredSize() {
                return tall(super.getPreferredSize());
            }
        };
        box.putClientProperty(FlatClientProperties.MINIMUM_HEIGHT, INPUT_HEIGHT);
        return box;
    }

    private static JTextField style(JTextField field, String placeholder, Glyph glyph) {
        field.putClientProperty(FlatClientProperties.MINIMUM_HEIGHT, INPUT_HEIGHT);
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_PADDING, new Insets(0, 6, 0, 6));
        if (glyph != null) {
            field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.of(glyph, 18, Theme::muted));
        }
        return field;
    }

    static java.awt.Dimension tall(java.awt.Dimension d) {
        return new java.awt.Dimension(d.width, Math.max(d.height, INPUT_HEIGHT));
    }

    public JComponent input() {
        return input;
    }

    public JTextField textField() {
        return (JTextField) input;
    }

    public String value() {
        return textField().getText().trim();
    }

    public char[] password() {
        return ((JPasswordField) input).getPassword();
    }

    public void setValue(String value) {
        textField().setText(value == null ? "" : value);
    }

    /** Shows an error under the field and outlines it in red; null clears it. */
    public void setError(String message) {
        boolean show = message != null && !message.isBlank();
        error.setText(show ? message : " ");
        error.setVisible(show);
        input.putClientProperty(FlatClientProperties.OUTLINE, show ? FlatClientProperties.OUTLINE_ERROR : null);
        revalidate();
    }
}
