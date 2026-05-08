package com.finalproject.ui.manager;

import com.finalproject.auth.AuthService;
import com.finalproject.model.Role;
import com.finalproject.model.User;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyEvent;
import java.util.Optional;

public class LoginDialog extends JDialog {
    public enum Result { LOGGED_IN, REGISTER_REQUESTED, CANCELLED }

    private final AuthService authService;
    private final Theme theme;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JLabel errorLabel;
    private Result result = Result.CANCELLED;
    private User loggedInUser;

    public LoginDialog(JFrame parent, AuthService authService, Theme theme) {
        super(parent, "Sign in to SentinelNode", true);
        this.authService = authService;
        this.theme = theme;
        this.usernameField = UIFactory.textField(theme, 18);
        this.passwordField = new JPasswordField(18);
        this.passwordField.setBackground(theme.surfaceMuted());
        this.passwordField.setForeground(theme.text());
        this.passwordField.setCaretColor(theme.text());
        this.errorLabel = new JLabel(" ");
        this.errorLabel.setForeground(theme.danger());
        this.errorLabel.setFont(theme.baseFont());
        this.errorLabel.setHorizontalAlignment(SwingConstants.CENTER);

        buildUi();

        setSize(new Dimension(420, 300));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(
            event -> { result = Result.CANCELLED; dispose(); },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JPanel.WHEN_IN_FOCUSED_WINDOW);
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(theme.background());
        content.setBorder(new EmptyBorder(20, 24, 20, 24));

        JLabel title = new JLabel("SentinelNode", SwingConstants.CENTER);
        title.setFont(theme.headingFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(theme.accent());
        JLabel subtitle = new JLabel("Manager Console", SwingConstants.CENTER);
        subtitle.setFont(theme.baseFont());
        subtitle.setForeground(theme.textMuted());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(theme.background());
        header.add(title, BorderLayout.CENTER);
        header.add(subtitle, BorderLayout.SOUTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(theme.background());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new java.awt.Insets(6, 0, 6, 0);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.weightx = 1.0;
        constraints.gridy = 0;
        form.add(label("Username"), constraints);
        constraints.gridy = 1;
        form.add(usernameField, constraints);
        constraints.gridy = 2;
        form.add(label("Password"), constraints);
        constraints.gridy = 3;
        form.add(passwordField, constraints);
        constraints.gridy = 4;
        form.add(errorLabel, constraints);

        JButton signIn = UIFactory.primaryButton(theme, "Sign in");
        JButton register = UIFactory.secondaryButton(theme, "Create account");
        JButton cancel = UIFactory.secondaryButton(theme, "Cancel");

        signIn.addActionListener(event -> attemptLogin());
        register.addActionListener(event -> { result = Result.REGISTER_REQUESTED; dispose(); });
        cancel.addActionListener(event -> { result = Result.CANCELLED; dispose(); });

        passwordField.addActionListener(event -> attemptLogin());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(theme.background());
        buttons.add(register);
        buttons.add(cancel);
        buttons.add(signIn);

        content.add(header, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(signIn);
        getRootPane().setBorder(BorderFactory.createLineBorder(theme.border()));
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(theme.textMuted());
        label.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

    private void attemptLogin() {
        errorLabel.setText(" ");
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Username and password are required.");
            return;
        }
        Optional<User> user = authService.login(username, password);
        if (user.isEmpty()) {
            errorLabel.setText("Invalid credentials. Try again or create an account.");
            return;
        }
        if (user.get().role() != Role.MANAGER) {
            errorLabel.setText("This account is a worker. Use the Worker app to sign in.");
            return;
        }
        loggedInUser = user.get();
        result = Result.LOGGED_IN;
        dispose();
    }

    public Result result() { return result; }
    public User loggedInUser() { return loggedInUser; }
}
