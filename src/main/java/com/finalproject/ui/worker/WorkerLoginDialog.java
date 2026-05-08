package com.finalproject.ui.worker;

import com.finalproject.app.AppConfig;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;
import com.finalproject.worker.WorkerRegistrar;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
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

public class WorkerLoginDialog extends JDialog {
    private final Theme theme;
    private boolean confirmed;
    private String username;
    private String token;
    private String host;
    private int port;

    public WorkerLoginDialog(JFrame parent, Theme theme, AppConfig config,
                             String prefilledUsername, String prefilledToken) {
        super(parent, "Connect to SentinelNode manager", true);
        this.theme = theme;

        JTextField usernameField = UIFactory.textField(theme, 18);
        usernameField.setText(prefilledUsername == null ? "" : prefilledUsername);
        JPasswordField tokenField = new JPasswordField(18);
        tokenField.setText(prefilledToken == null ? "" : prefilledToken);
        styleField(tokenField);
        JTextField hostField = UIFactory.textField(theme, 18);
        hostField.setText(config.managerHost());
        JTextField portField = UIFactory.textField(theme, 6);
        portField.setText(String.valueOf(config.managerPort()));
        JCheckBox showToken = new JCheckBox("Show token");
        showToken.setOpaque(false);
        showToken.setForeground(theme.textMuted());
        showToken.addActionListener(event -> tokenField.setEchoChar(showToken.isSelected() ? (char) 0 : '•'));

        JLabel error = new JLabel(" ");
        error.setForeground(theme.danger());
        error.setHorizontalAlignment(SwingConstants.CENTER);

        JButton connect = UIFactory.primaryButton(theme, "Connect");
        JButton cancel = UIFactory.secondaryButton(theme, "Cancel");
        JButton register = UIFactory.secondaryButton(theme, "Create new account");
        connect.addActionListener(event -> {
            if (usernameField.getText().trim().isEmpty()) {
                error.setText("Username is required.");
                return;
            }
            try {
                int chosenPort = Integer.parseInt(portField.getText().trim());
                if (chosenPort <= 0) throw new NumberFormatException();
                this.username = usernameField.getText().trim();
                this.token = new String(tokenField.getPassword()).trim();
                this.host = hostField.getText().trim();
                this.port = chosenPort;
                this.confirmed = true;
                dispose();
            } catch (NumberFormatException e) {
                error.setText("Port must be a positive integer.");
            }
        });
        cancel.addActionListener(event -> dispose());
        register.addActionListener(event ->
            openRegisterDialog(hostField, portField, error, usernameField, tokenField));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(theme.background());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new java.awt.Insets(6, 0, 6, 0);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.gridx = 0;
        addRow(form, constraints, 0, "Username",          usernameField);
        addRow(form, constraints, 2, "Token (optional)",  tokenField);
        constraints.gridy = 4; form.add(showToken, constraints);
        addRow(form, constraints, 5, "Manager host",      hostField);
        addRow(form, constraints, 7, "Manager port",      portField);
        constraints.gridy = 9; form.add(error, constraints);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(theme.background());
        buttons.add(register);
        buttons.add(cancel);
        buttons.add(connect);

        JLabel title = new JLabel("SentinelNode Worker", SwingConstants.CENTER);
        title.setFont(theme.headingFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(theme.accent());

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(theme.background());
        content.setBorder(new EmptyBorder(20, 24, 20, 24));
        content.add(title, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(connect);
        getRootPane().setBorder(BorderFactory.createLineBorder(theme.border()));
        setSize(new Dimension(520, 460));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    private void styleField(JPasswordField field) {
        field.setBackground(theme.surfaceMuted());
        field.setForeground(theme.text());
        field.setCaretColor(theme.text());
    }

    private void openRegisterDialog(JTextField hostField, JTextField portField,
                                    JLabel error, JTextField mainUsername,
                                    JPasswordField mainToken) {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            error.setText("Set the manager host/port first, then create the account.");
            return;
        }

        JTextField nameField = UIFactory.textField(theme, 16);
        JPasswordField passwordField = new JPasswordField(16);
        styleField(passwordField);
        JPasswordField confirmField = new JPasswordField(16);
        styleField(confirmField);

        JPanel form = new JPanel(new java.awt.GridLayout(0, 1, 6, 6));
        form.setBackground(theme.background());
        form.add(UIFactory.formLabel(theme, "Username (worker accounts only):"));
        form.add(nameField);
        form.add(UIFactory.formLabel(theme, "Password (≥8 chars):"));
        form.add(passwordField);
        form.add(UIFactory.formLabel(theme, "Confirm password:"));
        form.add(confirmField);

        int result = javax.swing.JOptionPane.showConfirmDialog(
            this, form, "Create worker account on " + host + ":" + port,
            javax.swing.JOptionPane.OK_CANCEL_OPTION,
            javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (result != javax.swing.JOptionPane.OK_OPTION) return;

        String name = nameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirm = new String(confirmField.getPassword());
        if (name.isEmpty() || password.length() < 8) {
            error.setText("Username and 8+ char password required.");
            return;
        }
        if (!password.equals(confirm)) {
            error.setText("Passwords do not match.");
            return;
        }

        try {
            WorkerRegistrar.Result registered = WorkerRegistrar.register(host, port, name, password);
            mainUsername.setText(registered.username());
            mainToken.setText(registered.token());
            error.setForeground(theme.success());
            error.setText("Account created. Click Connect to sign in.");
        } catch (WorkerRegistrar.RegisterFailedException ex) {
            error.setForeground(theme.danger());
            error.setText("Manager rejected: " + ex.getMessage());
        } catch (java.io.IOException ex) {
            error.setForeground(theme.danger());
            error.setText("Could not reach manager: " + ex.getMessage());
        }
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String labelText, java.awt.Component field) {
        JLabel label = new JLabel(labelText);
        label.setForeground(theme.textMuted());
        label.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
        c.gridy = row;
        panel.add(label, c);
        c.gridy = row + 1;
        panel.add(field, c);
    }

    public boolean confirmed() { return confirmed; }
    public String username()   { return username; }
    public String token()      { return token; }
    public String host()       { return host; }
    public int port()          { return port; }
}
