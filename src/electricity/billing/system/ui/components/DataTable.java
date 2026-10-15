package electricity.billing.system.ui.components;

import electricity.billing.system.model.BillStatus;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Theme;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

/** A roomy table with row hover, badge status cells, money cells and avatar name cells. */
public class DataTable extends JTable {

    private int hoverRow = -1;

    public DataTable(String... columns) {
        super(new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        });
        setFillsViewportHeight(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setShowVerticalLines(false);
        setAutoCreateRowSorter(true);
        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setDefaultRenderer(new HeaderRenderer());
        setDefaultRenderer(Object.class, new TextRenderer(SwingConstants.LEFT, false));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                if (row != hoverRow) {
                    hoverRow = row;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public DefaultTableModel model() {
        return (DefaultTableModel) getModel();
    }

    public void clear() {
        model().setRowCount(0);
    }

    public void addRow(Object... values) {
        model().addRow(values);
    }

    public void column(int index, TableCellRenderer renderer, int preferredWidth) {
        getColumnModel().getColumn(index).setCellRenderer(renderer);
        if (preferredWidth > 0) {
            getColumnModel().getColumn(index).setPreferredWidth(preferredWidth);
        }
    }

    /** The selected row's value in the given model column, or null. */
    public Object selected(int modelColumn) {
        int row = getSelectedRow();
        return row < 0 ? null : model().getValueAt(convertRowIndexToModel(row), modelColumn);
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        if (!isRowSelected(row)) {
            Color base = row % 2 == 0 ? UIManager.getColor("Table.background") : UIManager.getColor("Table.alternateRowColor");
            c.setBackground(row == hoverRow ? Ui.surfaceHover() : base);
        } else {
            c.setBackground(UIManager.getColor("Table.selectionBackground"));
        }
        if (c instanceof JComponent j) {
            j.setOpaque(true);
        }
        return c;
    }

    /** Wraps the table in a borderless scroll pane for use inside a card. */
    public JScrollPane scroll() {
        JScrollPane scroll = new JScrollPane(this);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UIManager.getColor("Table.background"));
        return scroll;
    }

    // ---- Renderers ----------------------------------------------------------

    /** Left-aligned, small, muted column titles on a tinted strip. */
    public static class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            boolean money = table.getColumnModel().getColumn(column).getCellRenderer() instanceof MoneyRenderer;
            setHorizontalAlignment(money ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setFont(Theme.semibold(12));
            setForeground(Theme.muted());
            setBackground(Theme.surfaceAlt());
            setOpaque(true);
            setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()),
                    new EmptyBorder(0, 16, 0, 16)));
            return this;
        }
    }

    public static class TextRenderer extends DefaultTableCellRenderer {
        private final boolean strong;

        public TextRenderer(int alignment, boolean strong) {
            this.strong = strong;
            setHorizontalAlignment(alignment);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, false, row, column);
            setBorder(new EmptyBorder(0, 16, 0, 16));
            setFont(strong ? Theme.semibold(14) : Theme.regular(14));
            setForeground(Theme.text());
            return this;
        }
    }

    public static class MoneyRenderer extends TextRenderer {
        public MoneyRenderer() {
            super(SwingConstants.RIGHT, true);
        }

        @Override
        protected void setValue(Object value) {
            super.setValue(value instanceof BigDecimal b ? Fmt.money(b) : value);
        }
    }

    public static class MutedRenderer extends TextRenderer {
        public MutedRenderer() {
            super(SwingConstants.LEFT, false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setForeground(Theme.muted());
            return this;
        }
    }

    public static class StatusRenderer implements TableCellRenderer {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 11));
        private final Badge badge = new Badge("", Theme.SUCCESS);

        public StatusRenderer() {
            panel.add(badge);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            if (value instanceof BillStatus status) {
                badge.set(status);
            }
            return panel;
        }
    }

    /** Shows an avatar next to the name. */
    public static class NameRenderer implements TableCellRenderer {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 9));
        private final Avatar avatar = new Avatar("", 30);
        private final JLabel name = new JLabel();

        public NameRenderer() {
            name.setFont(Theme.semibold(14));
            name.setBorder(new EmptyBorder(0, 0, 0, 6));
            panel.add(avatar);
            panel.add(name);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            String text = value == null ? "" : value.toString();
            avatar.setName(text);
            name.setText(text);
            name.setForeground(Theme.text());
            return panel;
        }
    }
}
