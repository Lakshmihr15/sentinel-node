package com.finalproject.ui.manager;

import com.finalproject.auth.AuthService;
import com.finalproject.model.Role;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
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

public class RegisterDialog extends JDialog {
    private final AuthService authService;
    private final Theme theme;
    private boolean succeeded;
    private String username;
    private Role role;

    public RegisterDialog(JFrame parent, AuthService authService, Theme theme) {
        super(parent, "Create SentinelNode account", true);
        this.authService = authService;
        this.theme = theme;

        JTextField usernameField = UIFactory.textField(theme, 18);
        JPasswordField passwordField = passwordField();
        JPasswordField confirmField = passwordField();
        JComboBox<Role> roleBox = new JComboBox<>(Role.values());
        roleBox.setBackground(theme.surfaceMuted());
        roleBox.setForeground(theme.text());

        JLabel error = new JLabel(" ");
        error.setForeground(theme.danger());
        error.setFont(theme.baseFont());
        error.setHorizontalAlignment(SwingConstants.CENTER);

        JButton create = UIFactory.primaryButton(theme, "Create account");
        JButton cancel = UIFactory.secondaryButton(theme, "Cancel");

        create.addActionListener(event -> {
            error.setText(" ");
            String name = usernameField.getText().trim();
            String pass = new String(passwordField.getPassword());
            String conf = new String(confirmField.getPassword());
            Role chosenRole = (Role) roleBox.getSelectedItem();
            if (name.isEmpty() || pass.isEmpty()) {
                error.setText("All fields are required.");
                return;
            }
            if (pass.length() < 8) {
                error.setText("Password must be at least 8 characters.");
                return;
            }
            if (!pass.equals(conf)) {
                error.setText("Passwords do not match.");
                return;
            }
            boolean ok = authService.register(name, pass, chosenRole == null ? Role.MANAGER : chosenRole);
            if (!ok) {
                error.setText("Username already exists or password rejected.");
                return;
            }
            this.username = name;
            this.role = chosenRole == null ? Role.MANAGER : chosenRole;
            this.succeeded = true;
            dispose();
        });
        cancel.addActionListener(event -> dispose());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(theme.background());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new java.awt.Insets(6, 0, 6, 0);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.weightx = 1.0;
        addRow(form, constraints, 0, "Username",         usernameField);
        addRow(form, constraints, 2, "Password (≥8)",    passwordField);
        addRow(form, constraints, 4, "Confirm password", confirmField);
        addRow(form, constraints, 6, "Role",             roleBox);
        constraints.gridy = 8;
        form.add(error, constraints);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(theme.background());
        buttons.add(cancel);
        buttons.add(create);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(theme.background());
        content.setBorder(new EmptyBorder(18, 22, 18, 22));
        content.add(headerLabel(), BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(create);
        getRootPane().setBorder(BorderFactory.createLineBorder(theme.border()));
        setSize(new Dimension(440, 360));
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

    private JLabel headerLabel() {
        JLabel label = new JLabel("Create account", SwingConstants.CENTER);
        label.setFont(theme.headingFont().deriveFont(Font.BOLD, 18f));
        label.setForeground(theme.accent());
        return label;
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

    public boolean succeeded() { return succeeded; }
    public String username()   { return username; }
    public Role role()         { return role; }
}
