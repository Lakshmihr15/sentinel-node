package com.finalproject.ui.manager.resources;

import com.finalproject.manager.ManagerController;
import com.finalproject.manager.QuotaListener;
import com.finalproject.manager.quota.QuotaRequest;
import com.finalproject.model.User;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class QuotaTab extends JPanel implements QuotaListener {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ManagerController controller;
    private final Theme theme;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final JTextField amountField;
    private User currentUser;
    private Consumer<String> toastSink = message -> {};

    public QuotaTab(ManagerController controller, Theme theme) {
        super(new BorderLayout(0, 8));
        this.controller = controller;
        this.theme = theme;
        this.amountField = UIFactory.textField(theme, 5);
        amountField.setText("5");

        setBackground(theme.surface());

        table.setBackground(theme.surface());
        table.setForeground(theme.text());
        table.setSelectionBackground(theme.accentSoft());
        table.setSelectionForeground(theme.text());
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton grantExact = UIFactory.primaryButton(theme, "Grant requested");
        JButton grantAmount = UIFactory.primaryButton(theme, "Grant amount →");
        JButton refresh = UIFactory.secondaryButton(theme, "Refresh");

        grantExact.addActionListener(event -> grantSelected(true));
        grantAmount.addActionListener(event -> grantSelected(false));
        refresh.addActionListener(event -> refresh());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setBackground(theme.surface());
        actions.add(grantExact);
        actions.add(grantAmount);
        actions.add(amountField);
        actions.add(refresh);

        add(UIFactory.scroll(theme, table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        controller.addQuotaListener(this);
    }

    public void setCurrentUser(User user) { this.currentUser = user; }
    public void setToastSink(Consumer<String> sink) {
        this.toastSink = sink == null ? message -> {} : sink;
    }

    public void refresh() {
        model.reload();
    }

    private void grantSelected(boolean grantExact) {
        Optional<QuotaRequest> selection = selected();
        if (selection.isEmpty()) {
            toastSink.accept("Select a quota request first.");
            return;
        }
        QuotaRequest request = selection.get();
        if (!request.isOpen()) {
            toastSink.accept("Already granted.");
            return;
        }
        int amount;
        if (grantExact) {
            amount = request.requested();
        } else {
            try {
                amount = Math.max(1, Integer.parseInt(amountField.getText().trim()));
            } catch (NumberFormatException e) {
                toastSink.accept("Amount must be a positive integer.");
                return;
            }
        }
        boolean granted = controller.grantQuota(request.id(),
            amount, currentUser == null ? "manager" : currentUser.username());
        if (granted) {
            toastSink.accept("Granted +" + amount + " credits to " + request.workerId());
        } else {
            toastSink.accept("Could not grant — request may already be served.");
        }
        refresh();
    }

    private Optional<QuotaRequest> selected() {
        int row = table.getSelectedRow();
        if (row < 0) return Optional.empty();
        return Optional.of(model.rows.get(row));
    }

    @Override
    public void onQuotaEvent(QuotaRequest request, String kind) {
        SwingUtilities.invokeLater(() -> {
            refresh();
            if ("REQUESTED".equals(kind)) {
                toastSink.accept("Quota request from " + request.workerId()
                    + " (need " + request.requested() + ", have " + request.have() + ")");
            }
        });
    }

    private class Model extends AbstractTableModel {
        private final String[] columns = {"When", "Worker", "Task", "Need", "Have", "Granted", "By"};
        private List<QuotaRequest> rows = List.of();

        void reload() {
            rows = controller.quotas().recent(100);
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            QuotaRequest request = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> TIME.format(request.timestamp().atZone(java.time.ZoneId.systemDefault()));
                case 1 -> request.workerId();
                case 2 -> request.taskId() == null || request.taskId().isBlank() ? "—" : request.taskId();
                case 3 -> request.requested();
                case 4 -> request.have();
                case 5 -> request.granted() == null ? "(open)" : "+" + request.granted();
                case 6 -> request.grantedBy() == null ? "" : request.grantedBy();
                default -> "";
            };
        }
    }
}
