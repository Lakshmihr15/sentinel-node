package com.finalproject.ui.manager;

import com.finalproject.manager.ManagerController;
import com.finalproject.model.WorkerSnapshot;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.function.Consumer;

public class WorkerTablePanel extends JPanel {
    private final WorkerTableModel model;
    private final JTable table;
    private final JTextField filterField;
    private Consumer<WorkerSnapshot> selectionListener = snapshot -> {};

    public WorkerTablePanel(ManagerController controller, Theme theme) {
        super(new BorderLayout(0, 8));
        setBackground(theme.surface());

        this.model = new WorkerTableModel(controller);
        this.table = new JTable(model);
        this.filterField = UIFactory.textField(theme, 18);

        configureTable(theme);

        JPanel filterRow = UIFactory.rowFlow(theme,
            UIFactory.sectionLabel(theme, "Filter"),
            filterField);
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { model.setFilter(filterField.getText()); }
            @Override public void removeUpdate(DocumentEvent e)  { model.setFilter(filterField.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { model.setFilter(filterField.getText()); }
        });

        add(filterRow, BorderLayout.NORTH);
        add(UIFactory.scroll(theme, table), BorderLayout.CENTER);
    }

    private void configureTable(Theme theme) {
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setIntercellSpacing(new java.awt.Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setBackground(theme.surface());
        table.setForeground(theme.text());
        table.setSelectionBackground(theme.accentSoft());
        table.setSelectionForeground(theme.text());
        table.setFont(theme.baseFont());
        table.getTableHeader().setBackground(theme.surfaceMuted());
        table.getTableHeader().setForeground(theme.textMuted());
        table.getTableHeader().setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
        table.getTableHeader().setReorderingAllowed(false);

        table.getColumnModel().getColumn(WorkerTableModel.COL_PROGRESS).setCellRenderer(new ProgressCellRenderer(theme));
        table.getColumnModel().getColumn(WorkerTableModel.COL_STATUS).setCellRenderer(new StatusPillRenderer(theme));
        table.getColumnModel().getColumn(WorkerTableModel.COL_CPU).setCellRenderer(new PercentCellRenderer(theme));
        table.getColumnModel().getColumn(WorkerTableModel.COL_MEMORY).setCellRenderer(new PercentCellRenderer(theme));

        table.getColumnModel().getColumn(WorkerTableModel.COL_USER).setPreferredWidth(120);
        table.getColumnModel().getColumn(WorkerTableModel.COL_HOST).setPreferredWidth(120);
        table.getColumnModel().getColumn(WorkerTableModel.COL_STATUS).setPreferredWidth(80);
        table.getColumnModel().getColumn(WorkerTableModel.COL_PROGRESS).setPreferredWidth(110);
        table.getColumnModel().getColumn(WorkerTableModel.COL_TAGS).setPreferredWidth(140);

        table.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            if (row < 0) {
                selectionListener.accept(null);
            } else {
                selectionListener.accept(model.snapshotAt(row));
            }
        });
    }

    public void onSelectionChanged(Consumer<WorkerSnapshot> listener) {
        this.selectionListener = listener == null ? snapshot -> {} : listener;
    }

    public void refresh() {
        String previousId = selectedWorkerId();
        model.refresh();
        if (previousId != null) {
            for (int i = 0; i < model.getRowCount(); i++) {
                if (previousId.equals(model.workerIdAt(i))) {
                    table.getSelectionModel().setSelectionInterval(i, i);
                    return;
                }
            }
        }
    }

    public String selectedWorkerId() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.workerIdAt(row);
    }

    public WorkerSnapshot selectedSnapshot() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.snapshotAt(row);
    }

    private static class ProgressCellRenderer extends JProgressBar implements TableCellRenderer {
        private final Theme theme;

        ProgressCellRenderer(Theme theme) {
            super(0, 100);
            this.theme = theme;
            setStringPainted(true);
            setBorderPainted(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            int pct = value instanceof Number n ? n.intValue() : 0;
            setValue(Math.max(0, Math.min(100, pct)));
            setString(pct + "%");
            if (pct >= 100)        setForeground(theme.success());
            else if (pct > 0)      setForeground(theme.accent());
            else                   setForeground(theme.textMuted());
            setBackground(isSelected ? theme.accentSoft() : theme.surfaceMuted());
            return this;
        }
    }

    private static class StatusPillRenderer extends DefaultTableCellRenderer {
        private final Theme theme;

        StatusPillRenderer(Theme theme) {
            this.theme = theme;
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String state = String.valueOf(value);
            Color color = switch (state) {
                case "ONLINE"  -> theme.success();
                case "BUSY"    -> theme.warning();
                case "OFFLINE" -> theme.danger();
                default        -> theme.textMuted();
            };
            label.setText("● " + state);
            label.setForeground(color);
            label.setBackground(isSelected ? theme.accentSoft() : theme.surface());
            label.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
            return label;
        }
    }

    private static class PercentCellRenderer extends DefaultTableCellRenderer {
        private final Theme theme;

        PercentCellRenderer(Theme theme) {
            this.theme = theme;
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            double pct = value instanceof Number n ? n.doubleValue() : 0;
            label.setText(String.format("%.1f%%", pct));
            label.setForeground(pct >= 80 ? theme.warning() : theme.text());
            label.setBackground(isSelected ? theme.accentSoft() : theme.surface());
            return label;
        }
    }
}
