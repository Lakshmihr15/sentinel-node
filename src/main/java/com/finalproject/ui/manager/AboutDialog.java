package com.finalproject.ui.manager;

import com.finalproject.app.AppConfig;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

public class AboutDialog extends JDialog {
    public AboutDialog(JFrame parent, Theme theme) {
        super(parent, "About SentinelNode", true);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(theme.background());
        content.setBorder(new EmptyBorder(20, 24, 20, 24));

        JLabel title = new JLabel("SentinelNode", SwingConstants.LEFT);
        title.setFont(theme.headingFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(theme.accent());

        JLabel subtitle = new JLabel("Distributed System Monitor & Remote Task Orchestrator");
        subtitle.setForeground(theme.textMuted());
        subtitle.setFont(theme.baseFont());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);

        JPanel body = new JPanel(new GridLayout(0, 2, 12, 6));
        body.setOpaque(false);
        addRow(body, theme, "Version", AppConfig.VERSION);
        addRow(body, theme, "Authors", "Lakshmi Hukunda Raju (lh4140)");
        addRow(body, theme, "",        "Harshith Kori Raj (hk4488)");
        addRow(body, theme, "Course",  "CS6103 — Java Final Project");
        addRow(body, theme, "Stack",   "Java 17 · Swing · SQLite · TCP");
        addRow(body, theme, "License", "Academic project — internal use only");

        JButton close = UIFactory.primaryButton(theme, "Close");
        close.addActionListener(event -> dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(close);

        content.add(header, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(close);
        getRootPane().setBorder(BorderFactory.createLineBorder(theme.border()));

        setSize(new Dimension(500, 300));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    private void addRow(JPanel panel, Theme theme, String labelText, String valueText) {
        JLabel label = new JLabel(labelText);
        label.setForeground(theme.textMuted());
        label.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
        JLabel value = new JLabel(valueText);
        value.setForeground(theme.text());
        value.setFont(theme.baseFont());
        panel.add(label);
        panel.add(value);
    }
}
