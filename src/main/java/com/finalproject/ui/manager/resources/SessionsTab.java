package com.finalproject.ui.manager.resources;

import com.finalproject.manager.ManagerController;
import com.finalproject.manager.bans.BanService;
import com.finalproject.model.User;
import com.finalproject.model.WorkerSnapshot;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class SessionsTab extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ManagerController controller;
    private final Theme theme;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private User currentUser;
    private Consumer<String> toastSink = message -> {};

    public SessionsTab(ManagerController controller, Theme theme) {
        super(new BorderLayout(0, 8));
        this.controller = controller;
        this.theme = theme;
        setBackground(theme.surface());

        table.setBackground(theme.surface());
        table.setForeground(theme.text());
        table.setSelectionBackground(theme.accentSoft());
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton kick = UIFactory.dangerButton(theme, "Kick");
        JButton ban = UIFactory.dangerButton(theme, "Ban user");
        JButton unban = UIFactory.secondaryButton(theme, "Unban");

        kick.addActionListener(event -> kickSelected());
        ban.addActionListener(event -> banSelected());
        unban.addActionListener(event -> unbanSelected());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setBackground(theme.surface());
        actions.add(kick);
        actions.add(ban);
        actions.add(unban);

        add(UIFactory.scroll(theme, table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    public void setCurrentUser(User user) { this.currentUser = user; }
    public void setToastSink(Consumer<String> sink) {
        this.toastSink = sink == null ? message -> {} : sink;
    }

    public void refresh() {
        model.reload();
    }

    private Optional<Row> selected() {
        int row = table.getSelectedRow();
        if (row < 0) return Optional.empty();
        return Optional.of(model.rows.get(row));
    }

    private void kickSelected() {
        Optional<Row> row = selected();
        if (row.isEmpty()) return;
        boolean ok = controller.forceDisconnect(row.get().workerId());
        toastSink.accept(ok ? "Kicked " + row.get().username() : "Kick failed");
        refresh();
    }

    private void banSelected() {
        Optional<Row> row = selected();
        if (row.isEmpty()) return;
        if (row.get().username() == null || row.get().username().isBlank()) {
            toastSink.accept("Cannot ban — no username on session.");
            return;
        }
        String reason = JOptionPane.showInputDialog(this, "Reason for ban:", "");
        if (reason == null) return;
        boolean banned = controller.bans().ban(row.get().username(),
            currentUser == null ? "manager" : currentUser.username(), reason);
        if (banned) {
            controller.forceDisconnect(row.get().workerId());
            toastSink.accept("Banned " + row.get().username());
        }
        refresh();
    }

    private void unbanSelected() {
        Optional<Row> row = selected();
        if (row.isEmpty()) return;
        boolean unbanned = controller.bans().unban(row.get().username());
        toastSink.accept(unbanned ? "Unbanned " + row.get().username() : "Not banned");
        refresh();
    }

    private record Row(String workerId, String username, String host, String state, String lastSeen, boolean banned) {}

    private class Model extends AbstractTableModel {
        private final String[] columns = {"Worker", "User", "Host", "State", "Banned", "Last seen"};
        private List<Row> rows = new ArrayList<>();

        void reload() {
            List<Row> next = new ArrayList<>();
            BanService bans = controller.bans();
            for (WorkerSnapshot snapshot : controller.registry().snapshots()) {
                String state = !snapshot.connected() ? "OFFLINE"
                    : snapshot.busy() ? "BUSY" : "ONLINE";
                next.add(new Row(
                    snapshot.workerId(),
                    snapshot.username(),
                    snapshot.host(),
                    state,
                    TIME.format(snapshot.lastSeen().atZone(java.time.ZoneId.systemDefault())),
                    bans.isBanned(snapshot.username())
                ));
            }
            rows = next;
            fireTableDataChanged();
        }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.workerId();
                case 1 -> row.username();
                case 2 -> row.host();
                case 3 -> row.state();
                case 4 -> row.banned() ? "YES" : "no";
                case 5 -> row.lastSeen();
                default -> "";
            };
        }
    }
}
