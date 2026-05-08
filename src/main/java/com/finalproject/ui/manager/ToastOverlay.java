package com.finalproject.ui.manager;

import com.finalproject.ui.theme.Theme;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Color;
import java.awt.Font;

public class ToastOverlay {
    private final JFrame parent;
    private final Theme theme;

    public ToastOverlay(JFrame parent, Theme theme) {
        this.parent = parent;
        this.theme = theme;
    }

    public void show(String message) {
        SwingUtilities.invokeLater(() -> {
            if (parent == null || message == null || message.isBlank()) return;
            JLabel label = new JLabel(message, SwingConstants.LEFT);
            label.setOpaque(true);
            label.setBackground(theme.surface());
            label.setForeground(theme.text());
            label.setFont(theme.baseFont().deriveFont(Font.BOLD, 12f));
            label.setBorder(new CompoundBorder(
                new LineBorder(theme.accent(), 1, true),
                new EmptyBorder(8, 14, 8, 14)));

            int width = Math.min(420, label.getPreferredSize().width + 32);
            label.setSize(width, label.getPreferredSize().height + 12);

            int x = parent.getWidth() - width - 24;
            int y = 28;
            label.setLocation(x, y);

            parent.getLayeredPane().add(label, javax.swing.JLayeredPane.POPUP_LAYER);
            parent.getLayeredPane().repaint();

            new Timer(3500, event -> {
                parent.getLayeredPane().remove(label);
                parent.getLayeredPane().repaint();
                ((Timer) event.getSource()).stop();
            }) {{ setRepeats(false); }}.start();
        });
    }

    public Color accent() { return theme.accent(); }
}
