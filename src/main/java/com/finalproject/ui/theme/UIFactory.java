package com.finalproject.ui.theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class UIFactory {
    private UIFactory() {}

    public static JPanel card(Theme theme, String title, Component body) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(theme.surface());
        panel.setBorder(new CompoundBorder(
            new LineBorder(theme.border(), 1, true),
            new EmptyBorder(UIInsets.CARD)
        ));
        if (title != null && !title.isBlank()) {
            JLabel header = new JLabel(title);
            header.setForeground(theme.textMuted());
            header.setFont(theme.headingFont());
            panel.add(header, BorderLayout.NORTH);
        }
        if (body != null) {
            panel.add(body, BorderLayout.CENTER);
        }
        return panel;
    }

    public static JPanel surface(Theme theme) {
        JPanel panel = new JPanel();
        panel.setBackground(theme.background());
        return panel;
    }

    public static JButton primaryButton(Theme theme, String text) {
        JButton button = baseButton(theme, text);
        button.setBackground(theme.accent().darker());
        button.setForeground(Color.WHITE);
        button.setFont(theme.headingFont().deriveFont(java.awt.Font.BOLD, 12f));
        return button;
    }

    public static JButton secondaryButton(Theme theme, String text) {
        JButton button = baseButton(theme, text);
        button.setBackground(theme.surfaceMuted());
        button.setForeground(Color.WHITE);
        return button;
    }

    public static JButton dangerButton(Theme theme, String text) {
        JButton button = baseButton(theme, text);
        button.setBackground(theme.danger().darker());
        button.setForeground(Color.WHITE);
        return button;
    }

    private static JButton baseButton(Theme theme, String text) {
        JButton button = new JButton(text);
        button.setBorder(new CompoundBorder(
            new LineBorder(theme.border(), 1, true),
            new EmptyBorder(6, 14, 6, 14)
        ));
        button.setFocusPainted(false);
        // On macOS Aqua, JButton ignores setBackground unless these are set:
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        return button;
    }

    public static JLabel heading(Theme theme, String text) {
        JLabel label = new JLabel(text);
        label.setFont(theme.headingFont().deriveFont(Font.BOLD, 22f));
        label.setForeground(theme.text());
        return label;
    }

    public static JLabel sectionLabel(Theme theme, String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(theme.textMuted());
        return label;
    }

    public static JLabel mutedLabel(Theme theme, String text) {
        JLabel label = new JLabel(text);
        label.setForeground(theme.textMuted());
        label.setFont(theme.baseFont());
        return label;
    }

    public static JLabel formLabel(Theme theme, String text) {
        JLabel label = new JLabel(text);
        label.setForeground(theme.text());
        label.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

    public static JTextField textField(Theme theme, int columns) {
        JTextField field = new JTextField(columns);
        field.setBackground(theme.surfaceMuted());
        field.setForeground(theme.text());
        field.setCaretColor(theme.text());
        field.setBorder(new CompoundBorder(
            new LineBorder(theme.border(), 1, true),
            new EmptyBorder(4, 8, 4, 8)
        ));
        return field;
    }

    public static JTextArea textArea(Theme theme) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setBackground(theme.surfaceMuted());
        area.setForeground(theme.text());
        area.setCaretColor(theme.text());
        area.setFont(theme.monoFont());
        area.setBorder(new EmptyBorder(6, 8, 6, 8));
        return area;
    }

    public static JScrollPane scroll(Theme theme, Component content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBackground(theme.surface());
        scroll.getViewport().setBackground(theme.surface());
        scroll.setBorder(new LineBorder(theme.border(), 1));
        return scroll;
    }

    public static JLabel statusPill(Theme theme, String state, Color color) {
        JLabel pill = new JLabel("● " + state);
        pill.setOpaque(true);
        pill.setBackground(blend(color, theme.surface(), 0.18f));
        pill.setForeground(color);
        pill.setBorder(new EmptyBorder(2, 10, 2, 10));
        pill.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
        return pill;
    }

    public static JPanel separator(Theme theme) {
        JPanel sep = new JPanel();
        sep.setPreferredSize(new Dimension(1, 1));
        sep.setBackground(theme.border());
        return sep;
    }

    public static JPanel formRow(Theme theme, String labelText, Component editor) {
        JPanel row = new JPanel(new BorderLayout(8, 4));
        row.setBackground(theme.surface());
        JLabel label = new JLabel(labelText);
        label.setForeground(theme.textMuted());
        label.setFont(theme.baseFont());
        label.setPreferredSize(new Dimension(120, 24));
        row.add(label, BorderLayout.WEST);
        row.add(editor, BorderLayout.CENTER);
        row.setBorder(new EmptyBorder(UIInsets.ROW));
        return row;
    }

    public static JPanel rowFlow(Theme theme, Component... children) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setBackground(theme.surface());
        for (Component child : children) {
            panel.add(child);
        }
        return panel;
    }

    public static Border titledLine(Theme theme, String title) {
        TitledBorder titled = BorderFactory.createTitledBorder(
            new LineBorder(theme.border(), 1, true), title);
        titled.setTitleColor(theme.textMuted());
        titled.setTitleFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
        return new CompoundBorder(titled, new EmptyBorder(6, 8, 6, 8));
    }

    public static Border bottomDivider(Theme theme) {
        return new MatteBorder(0, 0, 1, 0, theme.border());
    }

    public static Color blend(Color foreground, Color background, float alpha) {
        float beta = 1f - alpha;
        int r = (int) (foreground.getRed() * alpha + background.getRed() * beta);
        int g = (int) (foreground.getGreen() * alpha + background.getGreen() * beta);
        int b = (int) (foreground.getBlue() * alpha + background.getBlue() * beta);
        return new Color(clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static void applyGlobalLookAndFeel(Theme theme) {
        try {
            UIManager.put("OptionPane.background", theme.surface());
            UIManager.put("Panel.background", theme.surface());
            UIManager.put("OptionPane.messageForeground", theme.text());
            UIManager.put("Label.foreground", theme.text());
            UIManager.put("ToolTip.background", theme.surface());
            UIManager.put("ToolTip.foreground", theme.text());
            UIManager.put("Table.gridColor", theme.border());
            UIManager.put("Table.background", theme.surface());
            UIManager.put("Table.foreground", theme.text());
            UIManager.put("Table.selectionBackground", theme.accentSoft());
            UIManager.put("Table.selectionForeground", theme.text());
            UIManager.put("TableHeader.background", theme.surfaceMuted());
            UIManager.put("TableHeader.foreground", theme.textMuted());
            UIManager.put("TabbedPane.background", theme.surface());
            UIManager.put("TabbedPane.foreground", theme.text());
            UIManager.put("TabbedPane.selected", theme.accentSoft());
            UIManager.put("TabbedPane.contentAreaColor", theme.surface());
            UIManager.put("MenuBar.background", theme.surface());
            UIManager.put("MenuBar.foreground", theme.text());
            UIManager.put("Menu.background", theme.surface());
            UIManager.put("Menu.foreground", theme.text());
            UIManager.put("MenuItem.background", theme.surface());
            UIManager.put("MenuItem.foreground", theme.text());
            UIManager.put("ComboBox.background", theme.surfaceMuted());
            UIManager.put("ComboBox.foreground", theme.text());
            UIManager.put("ComboBox.selectionBackground", theme.accentSoft());
            UIManager.put("ComboBox.selectionForeground", theme.text());
            UIManager.put("TextField.background", theme.surfaceMuted());
            UIManager.put("TextField.foreground", theme.text());
            UIManager.put("TextField.caretForeground", theme.text());
            UIManager.put("PasswordField.background", theme.surfaceMuted());
            UIManager.put("PasswordField.foreground", theme.text());
            UIManager.put("PasswordField.caretForeground", theme.text());
            UIManager.put("TextArea.background", theme.surfaceMuted());
            UIManager.put("TextArea.foreground", theme.text());
            UIManager.put("TextArea.caretForeground", theme.text());
            UIManager.put("List.background", theme.surfaceMuted());
            UIManager.put("List.foreground", theme.text());
            UIManager.put("List.selectionBackground", theme.accentSoft());
            UIManager.put("List.selectionForeground", theme.text());
            UIManager.put("ScrollPane.background", theme.surface());
            UIManager.put("Viewport.background", theme.surface());
            UIManager.put("Button.background", theme.surfaceMuted());
            UIManager.put("Button.foreground", theme.text());
            UIManager.put("CheckBox.background", theme.surface());
            UIManager.put("CheckBox.foreground", theme.text());
            UIManager.put("RadioButton.background", theme.surface());
            UIManager.put("RadioButton.foreground", theme.text());
            UIManager.put("ProgressBar.background", theme.surfaceMuted());
            UIManager.put("ProgressBar.foreground", theme.accent());
            UIManager.put("ProgressBar.selectionBackground", theme.text());
            UIManager.put("ProgressBar.selectionForeground", theme.text());
        } catch (Exception ignored) {
        }
    }

    public static void enableAntiAlias(Graphics graphics) {
        if (graphics instanceof Graphics2D g2) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
    }
}
