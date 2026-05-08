package com.finalproject.ui;

import com.finalproject.auth.AuthService;
import com.finalproject.manager.ManagerController;
import com.finalproject.model.Role;
import com.finalproject.model.User;
import com.finalproject.model.WorkerSnapshot;
import com.finalproject.ui.manager.AboutDialog;
import com.finalproject.ui.manager.AnalyticsPanel;
import com.finalproject.ui.manager.EventLogPanel;
import com.finalproject.ui.manager.LoginDialog;
import com.finalproject.ui.manager.NotesPanel;
import com.finalproject.ui.manager.RegisterDialog;
import com.finalproject.ui.manager.SetupDialog;
import com.finalproject.ui.manager.StatusBar;
import com.finalproject.ui.manager.TaskDispatchPanel;
import com.finalproject.ui.manager.ToastOverlay;
import com.finalproject.ui.manager.WorkerTablePanel;
import com.finalproject.ui.manager.resources.ResourcePanel;
import com.finalproject.ui.theme.ManagerTheme;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class ManagerFrame extends JFrame {
    private static final Theme THEME = ManagerTheme.INSTANCE;
    private static final String SESSION_FILE = "manager_session.properties";

    private final ManagerController controller;
    private final AuthService authService;
    private final ToastOverlay toast;
    private final WorkerTablePanel workerTablePanel;
    private final TaskDispatchPanel dispatchPanel;
    private final EventLogPanel eventLogPanel;
    private final NotesPanel notesPanel;
    private final ResourcePanel resourcePanel;
    private final AnalyticsPanel analyticsPanel;
    private final StatusBar statusBar;
    private final MetricChartPanel chartPanel;
    private final JTabbedPane tabs;

    private User currentUser;

    public ManagerFrame(ManagerController controller, AuthService authService) {
        super("SentinelNode Manager");
        this.controller = controller;
        this.authService = authService;
        this.toast = new ToastOverlay(this, THEME);
        this.workerTablePanel = new WorkerTablePanel(controller, THEME);
        this.dispatchPanel = new TaskDispatchPanel(controller, THEME);
        this.eventLogPanel = new EventLogPanel(controller, THEME);
        this.notesPanel = new NotesPanel(controller, THEME);
        this.resourcePanel = new ResourcePanel(controller, THEME);
        this.analyticsPanel = new AnalyticsPanel(controller, THEME);
        this.statusBar = new StatusBar(controller, THEME);
        this.chartPanel = new MetricChartPanel(controller.registry(), THEME);
        this.tabs = new JTabbedPane();

        UIFactory.applyGlobalLookAndFeel(THEME);

        if (!ensureSignedIn()) {
            dispose();
            return;
        }

        applyCurrentUserView();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { saveSession(); }
        });

        Timer refreshTimer = new Timer(1000, event -> refreshAll());
        refreshTimer.start();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1320, 860);
        setLocationRelativeTo(null);
    }

    private boolean ensureSignedIn() {
        currentUser = restoreSession();
        if (currentUser != null && currentUser.role() == Role.MANAGER) {
            return true;
        }
        currentUser = null;
        if (needsFirstRunSetup()) {
            SetupDialog setup = new SetupDialog(this, authService, THEME);
            setup.setVisible(true);
            if (setup.completed() && setup.createdManager().isPresent()
                && setup.createdManager().get().role() == Role.MANAGER) {
                currentUser = setup.createdManager().get();
                saveSession();
                return true;
            }
        }
        return showSignInLoop();
    }

    private boolean needsFirstRunSetup() {
        return authService.listUsers().stream().noneMatch(user -> user.role() == Role.MANAGER);
    }

    private boolean showSignInLoop() {
        while (true) {
            LoginDialog dialog = new LoginDialog(this, authService, THEME);
            dialog.setVisible(true);
            switch (dialog.result()) {
                case LOGGED_IN -> {
                    currentUser = dialog.loggedInUser();
                    saveSession();
                    return true;
                }
                case REGISTER_REQUESTED -> {
                    RegisterDialog register = new RegisterDialog(this, authService, THEME);
                    register.setVisible(true);
                    if (register.succeeded() && register.role() == Role.MANAGER) {
                        toast.show("Account created — please sign in.");
                    }
                }
                case CANCELLED -> { return false; }
            }
        }
    }

    private void applyCurrentUserView() {
        getContentPane().removeAll();
        getContentPane().setBackground(THEME.background());
        controller.startServer(controller.config().managerPort());
        statusBar.setCurrentUser(currentUser);
        notesPanel.setCurrentUser(currentUser);
        notesPanel.setToastSink(toast::show);
        notesPanel.refreshRecipients();
        notesPanel.refreshTimeline();
        resourcePanel.setCurrentUser(currentUser);
        resourcePanel.setToastSink(toast::show);
        resourcePanel.refresh();
        dispatchPanel.onToast(toast::show);
        analyticsPanel.setToastSink(toast::show);

        workerTablePanel.onSelectionChanged(snapshot -> {
            dispatchPanel.setSelectedWorker(snapshot);
            chartPanel.setSelectedWorker(snapshot == null ? null : snapshot.workerId());
            chartPanel.setShowSecondaryHelp(snapshot != null);
        });

        installMenu();
        add(buildHeader(), BorderLayout.NORTH);
        add(buildMainTabs(), BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        installShortcuts();
        revalidate();
        repaint();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(THEME.background());
        header.setBorder(new EmptyBorder(14, 18, 10, 18));

        JLabel title = new JLabel("SentinelNode");
        title.setFont(THEME.headingFont().deriveFont(Font.BOLD, 24f));
        title.setForeground(THEME.accent());

        JLabel subtitle = new JLabel("Distributed System Monitor & Remote Task Orchestrator");
        subtitle.setFont(THEME.baseFont());
        subtitle.setForeground(THEME.textMuted());

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(title, BorderLayout.NORTH);
        left.add(subtitle, BorderLayout.SOUTH);

        JLabel rolePill = UIFactory.statusPill(THEME, "MANAGER", THEME.accent());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(rolePill);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JTabbedPane buildMainTabs() {
        tabs.setBackground(THEME.surface());
        tabs.setForeground(THEME.text());
        tabs.addTab("Dashboard", buildDashboardTab());
        tabs.addTab("Notes",     notesPanel);
        tabs.addTab("Resources", resourcePanel);
        tabs.addTab("Analytics", analyticsPanel);
        tabs.addTab("Activity",  eventLogPanel);
        return tabs;
    }

    private JPanel buildDashboardTab() {
        JPanel container = new JPanel(new BorderLayout(12, 12));
        container.setBackground(THEME.background());
        container.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);
        left.add(UIFactory.card(THEME, "Workers", workerTablePanel), BorderLayout.CENTER);
        left.add(UIFactory.card(THEME, "Dispatch", dispatchPanel), BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.setOpaque(false);
        right.add(UIFactory.card(THEME, "Live metrics", chartPanel), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setBackground(THEME.background());
        split.setBorder(null);
        split.setResizeWeight(0.55);
        split.setDividerSize(6);
        split.setPreferredSize(new Dimension(1000, 600));

        container.add(split, BorderLayout.CENTER);
        return container;
    }

    private void installMenu() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(THEME.surface());

        JMenu accountMenu = new JMenu("Account");
        accountMenu.setForeground(THEME.text());
        JMenuItem logout = new JMenuItem("Sign out");
        logout.addActionListener(event -> signOut());
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(event -> new AboutDialog(this, THEME).setVisible(true));
        accountMenu.add(logout);
        accountMenu.addSeparator();
        accountMenu.add(about);

        JMenu viewMenu = new JMenu("View");
        viewMenu.setForeground(THEME.text());
        addJump(viewMenu, "Dashboard", 0, KeyEvent.VK_1);
        addJump(viewMenu, "Notes",     1, KeyEvent.VK_2);
        addJump(viewMenu, "Resources", 2, KeyEvent.VK_3);
        addJump(viewMenu, "Analytics", 3, KeyEvent.VK_4);
        addJump(viewMenu, "Activity",  4, KeyEvent.VK_5);

        menuBar.add(accountMenu);
        menuBar.add(viewMenu);
        setJMenuBar(menuBar);
    }

    private void addJump(JMenu menu, String label, int tabIndex, int key) {
        JMenuItem item = new JMenuItem(label);
        item.setAccelerator(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK));
        item.addActionListener(event -> tabs.setSelectedIndex(tabIndex));
        menu.add(item);
    }

    private void installShortcuts() {
        getRootPane().registerKeyboardAction(
            event -> tabs.setSelectedIndex(1),
            KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK),
            JPanel.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(
            event -> tabs.setSelectedIndex(2),
            KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK),
            JPanel.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(
            event -> tabs.setSelectedIndex(0),
            KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK),
            JPanel.WHEN_IN_FOCUSED_WINDOW);
    }

    private void signOut() {
        clearSession();
        currentUser = null;
        toast.show("Signed out");
        if (showSignInLoop()) {
            applyCurrentUserView();
        } else {
            dispose();
        }
    }

    private void refreshAll() {
        workerTablePanel.refresh();
        eventLogPanel.refresh();
        statusBar.refresh();
        dispatchPanel.refreshTags();
        WorkerSnapshot selected = workerTablePanel.selectedSnapshot();
        if (selected != null) {
            dispatchPanel.setSelectedWorker(selected);
            chartPanel.setSelectedWorker(selected.workerId());
        }
        SwingUtilities.invokeLater(notesPanel::refreshRecipients);
    }

    private User restoreSession() {
        Properties properties = new Properties();
        java.io.File file = new java.io.File(System.getProperty("user.dir"), SESSION_FILE);
        if (!file.exists()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
            String savedUsername = properties.getProperty("username");
            if (savedUsername == null || savedUsername.isBlank()) return null;
            return authService.listUsers().stream()
                .filter(user -> user.username().equals(savedUsername))
                .filter(user -> user.role() == Role.MANAGER)
                .findFirst().orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void saveSession() {
        if (currentUser == null) return;
        Properties properties = new Properties();
        properties.setProperty("username", currentUser.username());
        properties.setProperty("role", currentUser.role().name());
        java.io.File file = new java.io.File(System.getProperty("user.dir"), SESSION_FILE);
        try (FileOutputStream output = new FileOutputStream(file)) {
            properties.store(output, "SentinelNode manager session");
        } catch (IOException ignored) {
        }
    }

    private void clearSession() {
        java.io.File file = new java.io.File(System.getProperty("user.dir"), SESSION_FILE);
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
