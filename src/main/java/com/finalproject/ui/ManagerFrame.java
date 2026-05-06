package com.finalproject.ui;

import com.finalproject.manager.ManagerController;
import com.finalproject.auth.AuthService;
import com.finalproject.auth.PasswordService;
import com.finalproject.repository.UserRepository;
import com.finalproject.db.DatabaseManager;
import com.finalproject.model.User;
import com.finalproject.model.Role;
import com.finalproject.model.TaskType;
import com.finalproject.model.WorkerEventRecord;
import com.finalproject.model.WorkerSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JList;
import javax.swing.DefaultListModel;
import javax.swing.JButton;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ManagerFrame extends JFrame {
    private static final Color BG = new Color(0x0F172A);
    private static final Color PANEL = new Color(0x111827);
    private static final Color PANEL_LIGHT = new Color(0x1F2937);
    private static final Color ACCENT = new Color(0x38BDF8);
    private static final Color TEXT = new Color(0xE5E7EB);

    private final ManagerController controller;
    private final AuthService authService;
    private User currentUser = null;
    private final WorkerTableModel workerTableModel;
    private JMenuItem whoamiMenuItem;
    private final JTextArea workerEventsArea;
    private final JTextArea taskEventsArea;
    private final JTextArea metricEventsArea;
    private final JTextField payloadField;
    private final JComboBox<TaskType> taskTypeBox;
    private final JButton dispatchButtonField;
    private final JLabel serverStatus;
    private final JLabel summaryLabel;
    private final MetricChartPanel chartPanel;
    private final JTable workerTable;

    public ManagerFrame(ManagerController controller, com.finalproject.auth.AuthService authService) {
        super("SentinelNode Manager");
        this.controller = controller;
        this.authService = authService;
        this.workerTableModel = new WorkerTableModel(controller);
        this.workerEventsArea = new JTextArea();
        this.taskEventsArea = new JTextArea();
        this.metricEventsArea = new JTextArea();
        this.payloadField = new JTextField("5000000", 18);
        this.taskTypeBox = new JComboBox<>(TaskType.values());
        this.dispatchButtonField = new JButton("Send to Selected Worker");
        this.serverStatus = new JLabel("Server: starting on port 6000");
        this.summaryLabel = new JLabel("Workers: 0   |   Active: 0   |   Busy: 0   |   Tasks: 0");
        this.chartPanel = new MetricChartPanel(controller);
        this.workerTable = new JTable(workerTableModel);

        boolean restored = restoreSession();
        if (restored) {
            int choice = JOptionPane.showOptionDialog(this,
                "Continue as " + currentUser.username() + " (" + currentUser.role() + ")?",
                "Restore Session",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[]{"Continue", "Switch User", "Cancel"},
                "Continue");
            if (choice == 1) {
                clearSession();
                currentUser = null;
            } else if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
                dispose();
                return;
            }
        }

        if (currentUser == null) {
            boolean ok = showLoginDialog();
            if (!ok) {
                dispose();
                return;
            }
        }

        applyCurrentUserView();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveSession();
            }
        });

        Timer timer = new Timer(1000, event -> refreshAll());
        timer.start();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        getContentPane().setBackground(BG);
        JPanel top = new JPanel(new BorderLayout(12, 12));
        top.setBorder(new EmptyBorder(12, 12, 12, 12));
        top.setBackground(BG);

        JLabel title = new JLabel("SentinelNode Distributed System Monitor", SwingConstants.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        title.setForeground(TEXT);
        top.add(title, BorderLayout.WEST);
        summaryLabel.setForeground(new Color(0xCBD5E1));
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD, 14f));
        top.add(summaryLabel, BorderLayout.CENTER);
        top.add(serverStatus, BorderLayout.EAST);
        styleLabel(serverStatus);

        JPanel taskPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        taskPanel.setBorder(BorderFactory.createTitledBorder("Dispatch Task"));
        taskPanel.setBackground(PANEL);
        taskPanel.add(new JLabel("Task Type"));
        taskPanel.add(taskTypeBox);
        taskPanel.add(new JLabel("Payload"));
        taskPanel.add(payloadField);
        dispatchButtonField.addActionListener(event -> dispatchTask());
        taskPanel.add(dispatchButtonField);

        workerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        workerTable.getSelectionModel().addListSelectionListener(this::selectionChanged);
        workerTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int selected = workerTable.getSelectedRow();
                    if (selected >= 0) {
                        openDispatchDialog();
                    }
                }
            }
        });
        workerTable.setRowHeight(26);
        workerTable.setShowGrid(false);
        workerTable.setFillsViewportHeight(true);
        workerTable.setBackground(Color.WHITE);
        workerTable.getTableHeader().setReorderingAllowed(false);
        workerTable.getColumnModel().getColumn(6).setCellRenderer(new ProgressBarRenderer());
        JScrollPane workerScroll = new JScrollPane(workerTable);
        workerScroll.setPreferredSize(new Dimension(780, 240));
        workerScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel eventsPanel = new JPanel(new GridLayout(1, 3, 12, 12));
        eventsPanel.setBackground(BG);
        workerEventsArea.setEditable(false);
        taskEventsArea.setEditable(false);
        metricEventsArea.setEditable(false);
        workerEventsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        taskEventsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        metricEventsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        eventsPanel.add(wrapTextArea("Recent Worker Events", workerEventsArea));
        eventsPanel.add(wrapTextArea("Recent Task Events", taskEventsArea));
        eventsPanel.add(wrapTextArea("Recent Metrics", metricEventsArea));

        JPanel chartWrap = new JPanel(new BorderLayout());
        chartWrap.setBorder(BorderFactory.createTitledBorder("Live CPU / Memory Chart"));
        chartWrap.setBackground(PANEL);
        chartWrap.add(chartPanel, BorderLayout.CENTER);

        JSplitPane middle = new JSplitPane(JSplitPane.VERTICAL_SPLIT, workerScroll, chartWrap);
        middle.setResizeWeight(0.48);
        middle.setBorder(BorderFactory.createEmptyBorder());

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setBorder(new EmptyBorder(0, 12, 12, 12));
        center.setBackground(BG);
        center.add(middle, BorderLayout.CENTER);
        center.add(taskPanel, BorderLayout.NORTH);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(eventsPanel, BorderLayout.SOUTH);

        applyPanelTheme(taskPanel);
    }

    private JPanel wrapTextArea(String title, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout());
        var tb = BorderFactory.createTitledBorder(title);
        tb.setTitleColor(TEXT);
        panel.setBorder(tb);
        panel.setBackground(PANEL);
        area.setBackground(PANEL_LIGHT);
        area.setForeground(TEXT);
        area.setCaretColor(TEXT);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        return panel;
    }

    private void dispatchTask() {
        int selected = workerTable.getSelectedRow();
        if (selected < 0) {
            JOptionPane.showMessageDialog(this, "Select a worker first.");
            return;
        }
        String workerId = workerTableModel.getWorkerIdAt(selected);
        TaskType taskType = (TaskType) taskTypeBox.getSelectedItem();
        if (taskType == null) {
            return;
        }
        boolean sent = controller.sendTask(workerId, taskType, payloadField.getText().trim());
        if (!sent) {
            JOptionPane.showMessageDialog(this, "Worker is busy or disconnected.");
        } else {
            JOptionPane.showMessageDialog(this, "Task dispatched to " + workerId + "!\nWatch the Progress bar.", "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void openDispatchDialog() {
        int selected = workerTable.getSelectedRow();
        if (selected < 0) return;
        String workerId = workerTableModel.getWorkerIdAt(selected);
        TaskType taskType = (TaskType) taskTypeBox.getSelectedItem();
        String payload = payloadField.getText().trim();
        JPanel p = new JPanel(new GridLayout(0, 1));
        JComboBox<TaskType> typeBox = new JComboBox<>(TaskType.values());
        typeBox.setSelectedItem(taskType);
        JTextField payloadInput = new JTextField(payload);
        p.add(new JLabel("Worker: " + workerId));
        p.add(new JLabel("Task Type:"));
        p.add(typeBox);
        p.add(new JLabel("Payload:"));
        p.add(payloadInput);
        int res = JOptionPane.showConfirmDialog(this, p, "Dispatch Task", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            boolean sent = controller.sendTask(workerId, (TaskType) typeBox.getSelectedItem(), payloadInput.getText().trim());
            if (!sent) JOptionPane.showMessageDialog(this, "Worker is busy or disconnected.");
        }
    }

    private void selectionChanged(ListSelectionEvent event) {
        if (!event.getValueIsAdjusting()) {
            refreshChart();
            updateDispatchButton();
        }
    }

    private void updateDispatchButton() {
        int selected = workerTable.getSelectedRow();
        if (selected < 0) {
            dispatchButtonField.setEnabled(false);
            return;
        }
        String workerId = workerTableModel.getWorkerIdAt(selected);
        WorkerSnapshot snapshot = controller.registry().snapshotFor(workerId);
        boolean enabled = snapshot.connected() && !snapshot.busy();
        dispatchButtonField.setEnabled(enabled);
    }

    private void refreshAll() {
        String selectedWorkerId = null;
        int selectedRow = workerTable.getSelectedRow();
        if (selectedRow >= 0) {
            selectedWorkerId = workerTableModel.getWorkerIdAt(selectedRow);
        }
        workerTableModel.refresh();

        if (selectedWorkerId != null) {
            for (int i = 0; i < workerTableModel.getRowCount(); i++) {
                if (selectedWorkerId.equals(workerTableModel.getWorkerIdAt(i))) {
                    workerTable.getSelectionModel().setSelectionInterval(i, i);
                    break;
                }
            }
        }

        refreshLogs();
        refreshChart();
        updateSummary();
        updateDispatchButton();
        serverStatus.setText("Server: listening on port 6000");
    }

    private void updateSummary() {
        List<WorkerSnapshot> workers = controller.registry().snapshots();
        long active = workers.stream().filter(WorkerSnapshot::connected).count();
        long busy = workers.stream().filter(WorkerSnapshot::busy).count();
        long taskEvents = controller.database().recentTaskEvents(100).size();
        summaryLabel.setText(String.format(Locale.ROOT,
            "Workers: %d   |   Active: %d   |   Busy: %d   |   Tasks Logged: %d",
            workers.size(), active, busy, taskEvents));
    }

    private void refreshLogs() {
        List<WorkerEventRecord> workerEvents = controller.database().recentWorkerEvents(12);
        StringBuilder workerBuilder = new StringBuilder();
        for (WorkerEventRecord event : workerEvents) {
            workerBuilder.append(DateTimeFormatter.ISO_INSTANT.format(event.timestamp())).append(" | ")
                .append(event.workerId()).append(" | ")
                .append(event.eventType()).append(" | ")
                .append(event.details()).append('\n');
        }
        workerEventsArea.setText(workerBuilder.toString());

        StringBuilder taskBuilder = new StringBuilder();
        controller.database().recentTaskEvents(12).forEach(event -> taskBuilder.append(event.timestamp()).append(" | ")
            .append(event.workerId()).append(" | ")
            .append(event.taskType()).append(" | ")
            .append(event.status()).append(" | ")
            .append(event.details()).append('\n'));
        taskEventsArea.setText(taskBuilder.toString());

        StringBuilder metricBuilder = new StringBuilder();
        controller.database().recentWorkerMetrics(12).forEach(metric -> metricBuilder.append(metric.timestamp()).append(" | ")
            .append(metric.workerId()).append(" | cpu=").append(metric.cpu())
            .append(" | mem=").append(metric.memory())
            .append(" | heap=").append(String.format(Locale.ROOT, "%.1fMB", metric.heapUsed() / 1024.0 / 1024.0))
            .append(" | threads=").append(metric.threadCount())
            .append(" | task=").append(metric.taskType())
            .append(" | progress=").append(metric.progress())
            .append('\n'));
        metricEventsArea.setText(metricBuilder.toString());
    }

    private void refreshChart() {
        int selected = workerTable.getSelectedRow();
        if (selected >= 0) {
            String workerId = workerTableModel.getWorkerIdAt(selected);
            chartPanel.setSelectedWorker(workerId);
        } else {
            chartPanel.setSelectedWorker(null);
        }
    }

    private static class WorkerTableModel extends AbstractTableModel {
        private final ManagerController controller;
        private final String[] columns = {"User", "Host", "Connected", "Busy", "Task", "Task Id", "Progress", "CPU %", "Memory %", "Last Seen", "Uptime"};
        private List<WorkerSnapshot> rows = List.of();

        private WorkerTableModel(ManagerController controller) {
            this.controller = controller;
        }

        public void refresh() {
            rows = controller.registry().snapshots();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            WorkerSnapshot snapshot = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> snapshot.username();
                case 1 -> snapshot.host();
                case 2 -> snapshot.connected();
                case 3 -> snapshot.busy();
                case 4 -> snapshot.taskType();
                case 5 -> snapshot.taskId();
                case 6 -> snapshot.progress();
                case 7 -> snapshot.cpu();
                case 8 -> snapshot.memory();
                case 9 -> snapshot.lastSeen();
                case 10 -> {
                    long secs = snapshot.uptimeSeconds();
                    if (secs < 0) yield "--";
                    long h = secs / 3600;
                    long m = (secs % 3600) / 60;
                    long s = secs % 60;
                    yield h > 0 ? String.format("%dh %dm", h, m) : String.format("%dm %ds", m, s);
                }
                default -> "";
            };
        }

        public String getWorkerIdAt(int rowIndex) {
            return rows.get(rowIndex).workerId();
        }
    }

    private void styleLabel(JComponent component) {
        component.setForeground(new Color(0xCBD5E1));
        component.setFont(component.getFont().deriveFont(Font.BOLD, 12f));
    }

    private void applyPanelTheme(JPanel panel) {
        panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PANEL_LIGHT, 1),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)));
    }

    private boolean showLoginDialog() {
        while (true) {
            String[] options = {"Login", "Register", "Cancel"};
            int choice = JOptionPane.showOptionDialog(this,
                "Please login or register to continue",
                "Authentication",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]);

            if (choice == 0) { // Login
                JTextField username = new JTextField();
                JPasswordField password = new JPasswordField();
                JPanel panel = new JPanel(new GridLayout(0, 1));
                panel.add(new JLabel("Username:"));
                panel.add(username);
                panel.add(new JLabel("Password:"));
                panel.add(password);
                int res = JOptionPane.showConfirmDialog(this, panel, "Login", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (res != JOptionPane.OK_OPTION) {
                    continue;
                }
                String user = username.getText().trim();
                String pass = new String(password.getPassword());
                var maybe = authService.login(user, pass);
                if (maybe.isPresent()) {
                    currentUser = maybe.get();
                    saveSession();
                    JOptionPane.showMessageDialog(this, "Logged in as " + currentUser.username());
                    return true;
                } else {
                    JOptionPane.showMessageDialog(this, "Login failed. Check username/password.");
                }

            } else if (choice == 1) { // Register
                JTextField username = new JTextField();
                JPasswordField password = new JPasswordField();
                JPasswordField confirm = new JPasswordField();
                JComboBox<Role> roleBox = new JComboBox<>(Role.values());
                JPanel panel = new JPanel(new GridLayout(0, 1));
                panel.add(new JLabel("Username:"));
                panel.add(username);
                panel.add(new JLabel("Password (min 8 chars):"));
                panel.add(password);
                panel.add(new JLabel("Confirm Password:"));
                panel.add(confirm);
                panel.add(new JLabel("Role:"));
                panel.add(roleBox);
                int res = JOptionPane.showConfirmDialog(this, panel, "Register", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (res != JOptionPane.OK_OPTION) {
                    continue;
                }
                String user = username.getText().trim();
                String pass = new String(password.getPassword());
                String conf = new String(confirm.getPassword());
                Role role = (Role) roleBox.getSelectedItem();
                if (!pass.equals(conf)) {
                    JOptionPane.showMessageDialog(this, "Passwords do not match.");
                    continue;
                }
                boolean created = authService.register(user, pass, role == null ? Role.MANAGER : role);
                if (created) {
                    JOptionPane.showMessageDialog(this, "User created. You can now login.");
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to create user. Username may already exist or password invalid.");
                }

            } else {
                return false;
            }
        }
    }

    private void installMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu account = new JMenu("Account");
        whoamiMenuItem = new JMenuItem(currentUser == null ? "Not logged in" : "User: " + currentUser.username());
        JMenuItem workerMgmt = new JMenuItem("Worker Management");
        JMenuItem manage = new JMenuItem("User Management");
        manage.addActionListener(e -> showUserManagementDialog());
        JMenuItem analytics = new JMenuItem("Analytics");
        analytics.addActionListener(e -> showAnalyticsDialog());
        // Disable manager-only menu items for non-manager users
        boolean isManager = currentUser != null && currentUser.role() == Role.MANAGER;
        manage.setEnabled(isManager);
        analytics.setEnabled(isManager);
        workerMgmt.setEnabled(isManager);
        workerMgmt.addActionListener(e -> showWorkerManagementDialog());
        JMenuItem logout = new JMenuItem("Logout");
        logout.addActionListener(e -> {
            clearSession();
            currentUser = null;
            updateWhoami();
            JOptionPane.showMessageDialog(this, "Logged out.");
            boolean ok = showLoginDialog();
            if (!ok) {
                dispose();
                return;
            }
            applyCurrentUserView();
        });
        JMenuItem demo = new JMenuItem("Demo Instructions");
        demo.addActionListener(e -> showDemoInstructions());
        JMenuItem about = new JMenuItem("About SentinelNode");
        about.addActionListener(e -> showAboutDialog());
        account.add(whoamiMenuItem);
        account.add(workerMgmt);
        account.add(manage);
        account.add(analytics);
        account.add(logout);
        account.add(demo);
        account.addSeparator();
        account.add(about);
        menuBar.add(account);
        setJMenuBar(menuBar);
        updateWhoami();
    }

    private void updateWhoami() {
        if (whoamiMenuItem == null) return;
        whoamiMenuItem.setText(currentUser == null ? "Not logged in" : "User: " + currentUser.username());
    }

    private boolean restoreSession() {
        Properties props = new Properties();
        String path = System.getProperty("user.dir") + "/manager_session.properties";
        try (FileInputStream in = new FileInputStream(path)) {
            props.load(in);
            String username = props.getProperty("username");
            if (username != null && !username.isBlank()) {
                var userOpt = authService.listUsers().stream().filter(u -> u.username().equals(username)).findFirst();
                if (userOpt.isPresent()) {
                    currentUser = userOpt.get();
                    String savedRole = props.getProperty("role");
                    if (savedRole != null && !savedRole.isBlank()) {
                        try {
                            currentUser = new User(currentUser.id(), currentUser.username(), Role.valueOf(savedRole), currentUser.passwordHash());
                        } catch (IllegalArgumentException ignored) {
                            // keep DB role if the saved role is invalid
                        }
                    }
                    return true;
                }
            }
        } catch (IOException e) {
            // ignore - no session
        }
        return false;
    }

    private void saveSession() {
        if (currentUser == null) return;
        Properties props = new Properties();
        props.setProperty("username", currentUser.username());
        props.setProperty("role", currentUser.role().name());
        String path = System.getProperty("user.dir") + "/manager_session.properties";
        try (FileOutputStream out = new FileOutputStream(path)) {
            props.store(out, "SentinelNode manager session");
        } catch (IOException e) {
            // ignore
        }
    }

    private void clearSession() {
        String path = System.getProperty("user.dir") + "/manager_session.properties";
        java.io.File f = new java.io.File(path);
        if (f.exists()) f.delete();
    }

    private void showUserManagementDialog() {
        DefaultListModel<String> model = new DefaultListModel<>();
        authService.listUsers().forEach(u -> model.addElement(u.username()));
        JList<String> list = new JList<>(model);
        list.setBackground(PANEL_LIGHT);
        list.setForeground(TEXT);
        JScrollPane scroll = new JScrollPane(list);
        scroll.getViewport().setBackground(PANEL_LIGHT);
        JButton create = new JButton("Create");
        JButton delete = new JButton("Delete");
        JButton setToken = new JButton("Set Token");
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout());
        controls.add(create);
        controls.add(delete);
        controls.add(setToken);
        panel.add(controls, BorderLayout.SOUTH);
        create.addActionListener(e -> {
            JTextField username = new JTextField();
            JPasswordField password = new JPasswordField();
            JComboBox<Role> roleBox = new JComboBox<>(Role.values());
            JPanel p = new JPanel(new GridLayout(0, 1));
            p.add(new JLabel("Username:")); p.add(username);
            p.add(new JLabel("Password:")); p.add(password);
            p.add(new JLabel("Role:")); p.add(roleBox);
            int res = JOptionPane.showConfirmDialog(this, p, "Create User", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res == JOptionPane.OK_OPTION) {
                boolean ok = authService.register(username.getText().trim(), new String(password.getPassword()), (Role) roleBox.getSelectedItem());
                if (ok) model.addElement(username.getText().trim());
                else JOptionPane.showMessageDialog(this, "Failed to create user.");
            }
        });
        delete.addActionListener(e -> {
            String sel = list.getSelectedValue();
            if (sel == null) return;
            int r = JOptionPane.showConfirmDialog(this, "Delete user " + sel + "?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                boolean ok = authService.deleteUser(sel);
                if (ok) model.removeElement(sel);
                else JOptionPane.showMessageDialog(this, "Failed to delete user.");
            }
        });
        setToken.addActionListener(e -> {
            String sel = list.getSelectedValue();
            if (sel == null) {
                JOptionPane.showMessageDialog(this, "Select a user first.");
                return;
            }
            String token = JOptionPane.showInputDialog(this,
                "Enter authentication token for \"" + sel + "\":\n(Workers can use this token to authenticate automatically)");
            if (token != null && !token.isBlank()) {
                boolean ok = authService.setToken(sel, token.trim());
                if (ok) JOptionPane.showMessageDialog(this, "Token set for \"" + sel + "\".");
                else JOptionPane.showMessageDialog(this, "Failed to set token.");
            }
        });
        JOptionPane.showMessageDialog(this, panel, "User Management", JOptionPane.PLAIN_MESSAGE);
    }

    private void showAnalyticsDialog() {
        JTextField filter = new JTextField(16);
        String[] options = {"Worker Events", "Task Events", "Worker Metrics"};
        JComboBox<String> kind = new JComboBox<>(options);
        JTextArea out = new JTextArea(22, 80);
        out.setEditable(false);
        out.setBackground(PANEL_LIGHT);
        out.setForeground(TEXT);
        out.setCaretColor(TEXT);
        out.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JButton refresh = new JButton("Refresh");

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.setBackground(PANEL);
        controls.add(new JLabel("Worker filter:"));
        controls.add(filter);
        controls.add(kind);
        controls.add(refresh);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(PANEL);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(out), BorderLayout.CENTER);

        Runnable runQuery = () -> {
            out.setText("");
            String f = filter.getText().trim();
            if (kind.getSelectedIndex() == 0) {
                controller.database().recentWorkerEvents(200).stream()
                    .filter(ev -> f.isEmpty() || ev.workerId().contains(f))
                    .forEach(ev -> out.append(ev.timestamp() + " | " + ev.workerId() + " | " + ev.eventType() + " | " + ev.details() + "\n"));
            } else if (kind.getSelectedIndex() == 1) {
                controller.database().recentTaskEvents(200).stream()
                    .filter(ev -> f.isEmpty() || ev.workerId().contains(f))
                    .forEach(ev -> out.append(ev.timestamp() + " | " + ev.workerId() + " | " + ev.taskType() + " | " + ev.status() + " | " + ev.details() + "\n"));
            } else {
                controller.database().recentWorkerMetrics(200).stream()
                    .filter(ev -> f.isEmpty() || ev.workerId().contains(f))
                    .forEach(ev -> out.append(ev.timestamp() + " | " + ev.workerId()
                        + " | cpu=" + ev.cpu() + " | mem=" + ev.memory()
                        + " | heap=" + String.format(Locale.ROOT, "%.1fMB", ev.heapUsed() / 1024.0 / 1024.0)
                        + " | threads=" + ev.threadCount()
                        + " | task=" + ev.taskType() + " | progress=" + ev.progress() + "\n"));
            }
            out.setCaretPosition(0);
        };

        refresh.addActionListener(e -> runQuery.run());
        kind.addActionListener(e -> runQuery.run());
        runQuery.run();

        JOptionPane.showMessageDialog(this, panel, "Analytics", JOptionPane.PLAIN_MESSAGE);
    }

    private void showDemoInstructions() {
        String msg = "Demo: start manager, then start 2 workers.\n\n" +
            "Start manager (GUI): mvn exec:java\n" +
            "Start worker: mvn exec:java -Dexec.args='worker worker-1 127.0.0.1 6000'\n" +
            "Start worker 2: mvn exec:java -Dexec.args='worker worker-2 127.0.0.1 6000'\n\n" +
            "Use Account -> User Management to create users. Use the dashboard to dispatch tasks.";
        JOptionPane.showMessageDialog(this, msg, "Demo Instructions", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAboutDialog() {
        String msg = "SentinelNode — Distributed System Monitor & Remote Task Orchestrator\n\n" +
            "CS6103 - Java Final Project Proposal\n" +
            "Authors:\n" +
            "• Lakshmi Hukunda Raju (Net ID: lh4140)\n" +
            "• Harshith Kori Raj (Net ID: hk4488)\n\n" +
            "Version: 1.0.0\n" +
            "Spring 2026";
        JOptionPane.showMessageDialog(this, msg, "About SentinelNode", JOptionPane.INFORMATION_MESSAGE);
    }

    private void applyCurrentUserView() {
        getContentPane().removeAll();
        if (currentUser != null && currentUser.role() == Role.MANAGER) {
            controller.startServer(6000);
            buildUi();
            installMenu();
            refreshAll();
        } else {
            showWorkerView();
            installMenu();
        }
        revalidate();
        repaint();
    }

    private void showWorkerManagementDialog() {
        DefaultListModel<String> model = new DefaultListModel<>();
        java.util.List<WorkerSnapshot> snaps = controller.registry().snapshots();
        for (WorkerSnapshot s : snaps) {
            String status = s.connected() ? "ONLINE" : "OFFLINE";
            String line = String.format("%s (%s) - %s - lastSeen=%s", s.username(), s.workerId(), status, s.lastSeen());
            model.addElement(line);
        }
        JList<String> list = new JList<>(model);
        list.setBackground(PANEL_LIGHT);
        list.setForeground(TEXT);
        JScrollPane scroll = new JScrollPane(list);
        scroll.getViewport().setBackground(PANEL_LIGHT);
        JButton refresh = new JButton("Refresh");
        JButton disconnect = new JButton("Disconnect Selected");
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout());
        controls.add(refresh);
        controls.add(disconnect);
        panel.add(controls, BorderLayout.SOUTH);

        refresh.addActionListener(e -> {
            model.clear();
            controller.registry().snapshots().forEach(s -> {
                String status = s.connected() ? "ONLINE" : "OFFLINE";
                String line = String.format("%s (%s) - %s - lastSeen=%s", s.username(), s.workerId(), status, s.lastSeen());
                model.addElement(line);
            });
        });

        disconnect.addActionListener(e -> {
            String sel = list.getSelectedValue();
            if (sel == null) return;
            int p1 = sel.indexOf('(');
            int p2 = sel.indexOf(')');
            if (p1 < 0 || p2 < 0 || p2 <= p1) return;
            String workerId = sel.substring(p1 + 1, p2);
            int r = JOptionPane.showConfirmDialog(this, "Disconnect " + workerId + "?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (r == JOptionPane.YES_OPTION) {
                boolean ok = controller.forceDisconnect(workerId);
                if (ok) JOptionPane.showMessageDialog(this, "Disconnected " + workerId);
                else JOptionPane.showMessageDialog(this, "Failed to disconnect " + workerId);
                refresh.doClick();
            }
        });

        JOptionPane.showMessageDialog(this, panel, "Worker Management", JOptionPane.PLAIN_MESSAGE);
    }

    private void showWorkerView() {
        getContentPane().removeAll();
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        StringBuilder sb = new StringBuilder();
        sb.append("<html><div style='color:#E5E7EB;font-family:Sans-Serif;padding:12px;'>");
        sb.append("<h2>Worker Account</h2>");
        sb.append("<p>This application is the Manager dashboard. Worker accounts do not see the dashboard UI.</p>");
        sb.append("<p>To run a Worker process (headless) on this machine, open a terminal and run:</p>");
        sb.append("<pre style='background:#0B1220;padding:8px;color:#D1D5DB;'>mvn exec:java -Dexec.args='worker worker-1 127.0.0.1 6000'</pre>");
        sb.append("<p>Workers connect to the Manager and stream metrics; the Manager displays them here for Manager users.</p>");
        sb.append("</div></html>");
        JLabel label = new JLabel(sb.toString());
        panel.add(label, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(BG);
        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> {
            clearSession();
            currentUser = null;
            updateWhoami();
            boolean ok = showLoginDialog();
            if (!ok) dispose();
            else {
                // if a manager logged in after logout, rebuild full UI
                if (currentUser != null && currentUser.role() == Role.MANAGER) {
                    getContentPane().removeAll();
                    buildUi();
                    installMenu();
                    controller.startServer(6000);
                    refreshAll();
                } else {
                    showWorkerView();
                }
            }
        });
        bottom.add(logout);
        panel.add(bottom, BorderLayout.SOUTH);
        add(panel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * Renders the Progress column as a live JProgressBar instead of a raw integer,
     * making task progress immediately visible in the worker table.
     */
    private static class ProgressBarRenderer extends javax.swing.JProgressBar implements TableCellRenderer {
        ProgressBarRenderer() {
            super(0, 100);
            setStringPainted(true);
            setBorderPainted(false);
        }

        @Override
        public java.awt.Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int pct = 0;
            if (value instanceof Integer i) {
                pct = i;
            } else if (value instanceof Number n) {
                pct = n.intValue();
            }
            setValue(pct);
            setString(pct + "%");
            if (pct > 0 && pct < 100) {
                setForeground(new Color(0x38BDF8)); // blue — in progress
            } else if (pct >= 100) {
                setForeground(new Color(0x34D399)); // green — done
            } else {
                setForeground(new Color(0x6B7280)); // grey — idle
            }
            setBackground(isSelected ? table.getSelectionBackground() : new Color(0x1F2937));
            return this;
        }
    }
}