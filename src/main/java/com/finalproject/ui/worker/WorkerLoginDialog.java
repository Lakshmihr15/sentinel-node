package com.finalproject.ui.worker;

import com.finalproject.app.AppConfig;
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
        setSize(new Dimension(440, 420));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    private void styleField(JPasswordField field) {
        field.setBackground(theme.surfaceMuted());
        field.setForeground(theme.text());
        field.setCaretColor(theme.text());
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
