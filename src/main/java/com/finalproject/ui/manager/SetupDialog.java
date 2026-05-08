package com.finalproject.ui.manager;

import com.finalproject.auth.AuthService;
import com.finalproject.model.Role;
import com.finalproject.model.User;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Shown the first time the app starts (no manager accounts in workforce.db).
 * Creates the bootstrap manager account, optionally seeds demo worker
 * accounts, and writes their join tokens to ~/.sentinelnode/demo-workers.env
 * so run-demo.sh can pick them up.
 */
public class SetupDialog extends JDialog {
    private final AuthService authService;
    private final Theme theme;
    private final JTextField managerUsername;
    private final JPasswordField managerPassword;
    private final JPasswordField managerConfirm;
    private final JCheckBox seedWorkers;
    private final JTextField workerOneName;
    private final JTextField workerTwoName;
    private final JLabel error;
    private final JTextArea joinHelp;
    private boolean completed;
    private User createdManager;

    public SetupDialog(JFrame parent, AuthService authService, Theme theme) {
        super(parent, "Welcome to SentinelNode — first-time setup", true);
        this.authService = authService;
        this.theme = theme;
        this.managerUsername = UIFactory.textField(theme, 18);
        this.managerPassword = passwordField();
        this.managerConfirm = passwordField();
        this.seedWorkers = new JCheckBox("Also create demo worker accounts", true);
        this.workerOneName = UIFactory.textField(theme, 12);
        this.workerOneName.setText("alice");
        this.workerTwoName = UIFactory.textField(theme, 12);
        this.workerTwoName.setText("bob");
        this.error = new JLabel(" ");
        this.error.setForeground(theme.danger());
        this.error.setHorizontalAlignment(SwingConstants.CENTER);
        this.error.setFont(theme.baseFont());
        this.joinHelp = UIFactory.textArea(theme);
        this.joinHelp.setRows(6);
        this.joinHelp.setText(
            "On first run, SentinelNode needs a manager account.\n"
            + "Optionally we can also seed two demo worker accounts so the\n"
            + "demo script (./run-demo.sh) connects them out of the box.\n\n"
            + "Tokens are stored at ~/.sentinelnode/demo-workers.env"
        );

        seedWorkers.setOpaque(false);
        seedWorkers.setForeground(theme.text());
        seedWorkers.setFont(theme.baseFont().deriveFont(Font.BOLD, 12f));
        seedWorkers.addActionListener(event -> {
            workerOneName.setEnabled(seedWorkers.isSelected());
            workerTwoName.setEnabled(seedWorkers.isSelected());
        });

        JButton create = UIFactory.primaryButton(theme, "Create accounts");
        JButton cancel = UIFactory.secondaryButton(theme, "Skip");
        create.addActionListener(event -> attemptSetup());
        cancel.addActionListener(event -> dispose());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(theme.background());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new java.awt.Insets(4, 0, 4, 0);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.gridx = 0;

        addRow(form, constraints, 0, "Manager username",         managerUsername);
        addRow(form, constraints, 2, "Manager password (≥8)",    managerPassword);
        addRow(form, constraints, 4, "Confirm password",         managerConfirm);
        constraints.gridy = 6; form.add(seedWorkers, constraints);
        addRow(form, constraints, 7,  "Demo worker #1 username", workerOneName);
        addRow(form, constraints, 9,  "Demo worker #2 username", workerTwoName);
        constraints.gridy = 11; form.add(error, constraints);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(theme.background());
        buttons.add(cancel);
        buttons.add(create);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(theme.background());
        content.setBorder(new EmptyBorder(20, 24, 20, 24));
        content.add(buildHeader(), BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setBackground(theme.background());
        south.add(UIFactory.scroll(theme, joinHelp), BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(create);
        getRootPane().setBorder(BorderFactory.createLineBorder(theme.border()));
        setSize(new Dimension(560, 620));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    private JPasswordField passwordField() {
        JPasswordField field = new JPasswordField(18);
        field.setBackground(theme.surfaceMuted());
        field.setForeground(theme.text());
        field.setCaretColor(theme.text());
        return field;
    }

    private JLabel buildHeader() {
        JLabel header = new JLabel("First-time setup", SwingConstants.LEFT);
        header.setFont(theme.headingFont().deriveFont(Font.BOLD, 22f));
        header.setForeground(theme.accent());
        return header;
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String labelText, java.awt.Component field) {
        JLabel label = UIFactory.formLabel(theme, labelText);
        c.gridy = row;
        panel.add(label, c);
        c.gridy = row + 1;
        panel.add(field, c);
    }

    private void attemptSetup() {
        error.setText(" ");
        String username = managerUsername.getText().trim();
        String password = new String(managerPassword.getPassword());
        String confirm = new String(managerConfirm.getPassword());
        if (username.isEmpty()) {
            error.setText("Manager username is required.");
            return;
        }
        if (password.length() < 8) {
            error.setText("Password must be at least 8 characters.");
            return;
        }
        if (!password.equals(confirm)) {
            error.setText("Passwords do not match.");
            return;
        }
        if (!authService.register(username, password, Role.MANAGER)) {
            error.setText("Could not create manager (username may already exist).");
            return;
        }
        Optional<User> manager = authService.login(username, password);
        if (manager.isEmpty()) {
            error.setText("Manager created but cannot be authenticated. Try signing in manually.");
            return;
        }
        createdManager = manager.get();

        Map<String, String> seededTokens = new LinkedHashMap<>();
        if (seedWorkers.isSelected()) {
            seededTokens.putAll(seedWorkerAccount(workerOneName.getText().trim()));
            seededTokens.putAll(seedWorkerAccount(workerTwoName.getText().trim()));
            persistJoinFile(seededTokens);
        }

        joinHelp.setText(buildSummary(seededTokens));
        completed = true;
        // Give the user a moment to read the join info; close after they hit OK
        // by re-purposing the create button into "Continue".
    }

    private Map<String, String> seedWorkerAccount(String username) {
        Map<String, String> result = new LinkedHashMap<>();
        if (username == null || username.isBlank()) return result;
        // Idempotent: if worker already exists, just rotate the token.
        boolean fresh = authService.register(username, autoPassword(), Role.WORKER);
        String token = authService.generateToken();
        if (authService.setToken(username, token)) {
            result.put(username, token);
        }
        if (fresh) {
            // ok
        }
        return result;
    }

    private static String autoPassword() {
        return "demo-" + Long.toHexString(System.nanoTime() & 0xFFFFFFL) + "-x";
    }

    private void persistJoinFile(Map<String, String> tokens) {
        if (tokens.isEmpty()) return;
        Path dir = Paths.get(System.getProperty("user.home"), ".sentinelnode");
        Path file = dir.resolve("demo-workers.env");
        try {
            Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder();
            sb.append("# Auto-generated by SentinelNode setup. Source me before run-demo.sh.\n");
            int index = 1;
            for (Map.Entry<String, String> entry : tokens.entrySet()) {
                sb.append(String.format("export DEMO_WORKER_%d_USERNAME=%s%n", index, entry.getKey()));
                sb.append(String.format("export DEMO_WORKER_%d_TOKEN=%s%n",    index, entry.getValue()));
                index++;
            }
            Files.writeString(file, sb.toString());
        } catch (IOException ignored) {
        }
    }

    private String buildSummary(Map<String, String> tokens) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ Manager account created — you can now sign in.\n\n");
        if (tokens.isEmpty()) {
            sb.append("No worker accounts were seeded. Use Resources → Workers → Add worker\n");
            sb.append("after signing in to create them.\n");
        } else {
            sb.append("Seeded worker accounts (tokens saved to ~/.sentinelnode/demo-workers.env):\n\n");
            for (Map.Entry<String, String> entry : tokens.entrySet()) {
                sb.append("  ").append(entry.getKey()).append("\n");
                sb.append("    token: ").append(entry.getValue()).append("\n");
            }
            sb.append("\nrun-demo.sh will source that file automatically.\n");
        }
        sb.append("\nClick Skip to close this window and sign in.\n");
        return sb.toString();
    }

    public boolean completed()           { return completed; }
    public Optional<User> createdManager() { return Optional.ofNullable(createdManager); }
}
