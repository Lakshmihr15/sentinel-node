package com.finalproject.ui.manager.resources;

import com.finalproject.manager.ManagerController;
import com.finalproject.model.Role;
import com.finalproject.model.User;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class WorkerAccountsTab extends JPanel {
    private final ManagerController controller;
    private final Theme theme;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private User currentUser;
    private Consumer<String> toastSink = message -> {};

    public WorkerAccountsTab(ManagerController controller, Theme theme) {
        super(new BorderLayout(0, 8));
        this.controller = controller;
        this.theme = theme;
        setBackground(theme.surface());

        table.setBackground(theme.surface());
        table.setForeground(theme.text());
        table.setSelectionBackground(theme.accentSoft());
        table.setSelectionForeground(theme.text());
        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton add = UIFactory.primaryButton(theme, "Add worker");
        JButton revoke = UIFactory.dangerButton(theme, "Revoke");
        JButton restore = UIFactory.secondaryButton(theme, "Restore");
        JButton copyJoin = UIFactory.secondaryButton(theme, "Copy join command");
        JButton resetToken = UIFactory.secondaryButton(theme, "New token");

        add.addActionListener(event -> addAccount());
        revoke.addActionListener(event -> revokeSelected());
        restore.addActionListener(event -> restoreSelected());
        copyJoin.addActionListener(event -> copyJoinCommand());
        resetToken.addActionListener(event -> resetToken());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setBackground(theme.surface());
        actions.add(add);
        actions.add(copyJoin);
        actions.add(resetToken);
        actions.add(revoke);
        actions.add(restore);

        add(UIFactory.scroll(theme, table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public void setToastSink(Consumer<String> sink) {
        this.toastSink = sink == null ? message -> {} : sink;
    }

    public void refresh() {
        model.reload();
    }

    private void addAccount() {
        JTextField username = UIFactory.textField(theme, 18);
        JTextField password = UIFactory.textField(theme, 18);
        password.setText("worker-" + Long.toHexString(System.nanoTime() & 0xFFFFFF));
        JPanel form = new JPanel();
        form.setBackground(theme.surface());
        form.setLayout(new java.awt.GridLayout(0, 1, 6, 6));
        form.add(UIFactory.formLabel(theme, "Username:"));
        form.add(username);
        form.add(UIFactory.formLabel(theme, "Initial password (≥8 chars, can be changed later):"));
        form.add(password);

        int result = JOptionPane.showConfirmDialog(this, form, "Add worker account",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String name = username.getText().trim();
        String pass = password.getText().trim();
        if (name.isEmpty() || pass.length() < 8) {
            toastSink.accept("Need username + 8+ char password.");
            return;
        }
        boolean created = controller.auth().register(name, pass, Role.WORKER);
        if (!created) {
            toastSink.accept("Could not create account (username taken?).");
            return;
        }
        String token = controller.auth().generateToken();
        controller.auth().setToken(name, token);
        copyToClipboard(joinCommandFor(name, token));
        toastSink.accept("Worker " + name + " created — join command copied.");
        refresh();
    }

    private void revokeSelected() {
        Optional<Row> row = selected();
        if (row.isEmpty()) return;
        if (row.get().role() == Role.MANAGER) {
            toastSink.accept("Use the User menu to revoke managers.");
            return;
        }
        if (currentUser != null && currentUser.username().equals(row.get().username())) {
            toastSink.accept("You cannot revoke your own account.");
            return;
        }
        boolean revoked = controller.auth().revokeUser(row.get().username());
        if (revoked) {
            for (var snapshot : controller.registry().snapshotsForUsername(row.get().username())) {
                controller.forceDisconnect(snapshot.workerId());
            }
            toastSink.accept("Revoked " + row.get().username());
            refresh();
        }
    }

    private void restoreSelected() {
        Optional<Row> row = selected();
        if (row.isEmpty()) return;
        if (controller.auth().restoreUser(row.get().username())) {
            toastSink.accept("Restored " + row.get().username());
            refresh();
        }
    }

    private void resetToken() {
        Optional<Row> row = selected();
        if (row.isEmpty()) return;
        String token = controller.auth().generateToken();
        controller.auth().setToken(row.get().username(), token);
        copyToClipboard(joinCommandFor(row.get().username(), token));
        toastSink.accept("New token issued for " + row.get().username() + " — join command copied.");
        refresh();
    }

    private void copyJoinCommand() {
        Optional<Row> row = selected();
        if (row.isEmpty()) return;
        String token = controller.auth().tokenFor(row.get().username()).orElse("");
        if (token.isEmpty()) {
            token = controller.auth().generateToken();
            controller.auth().setToken(row.get().username(), token);
        }
        copyToClipboard(joinCommandFor(row.get().username(), token));
        toastSink.accept("Join command copied for " + row.get().username());
        refresh();
    }

    private String joinCommandFor(String username, String token) {
        return "WORKER_USERNAME=" + username
            + " WORKER_TOKEN=" + token
            + " mvn -q exec:java -Dexec.args='worker-ui'";
    }

    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private Optional<Row> selected() {
        int row = table.getSelectedRow();
        if (row < 0) return Optional.empty();
        return Optional.of(model.rows.get(row));
    }

    private record Row(String username, Role role, String tokenStatus, boolean revoked) {}

    private class Model extends AbstractTableModel {
        private final String[] columns = {"User", "Role", "Token", "Status"};
        private List<Row> rows = new ArrayList<>();

        void reload() {
            List<Row> next = new ArrayList<>();
            for (User user : controller.auth().listUsers()) {
                String token = controller.auth().tokenFor(user.username()).orElse("");
                String tokenStatus = token.isBlank() ? "—" : maskToken(token);
                next.add(new Row(user.username(), user.role(), tokenStatus, false));
            }
            rows = next;
            fireTableDataChanged();
        }

        private String maskToken(String token) {
            if (token.length() <= 6) return "•••";
            return token.substring(0, 4) + "••••" + token.substring(token.length() - 2);
        }

        @Override public int getRowCount()    { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.username();
                case 1 -> row.role().name();
                case 2 -> row.tokenStatus();
                case 3 -> row.revoked() ? "REVOKED" : "ACTIVE";
                default -> "";
            };
        }
    }
}
