package com.finalproject.ui.manager;

import com.finalproject.manager.ManagerController;
import com.finalproject.model.User;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class StatusBar extends JPanel {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ManagerController controller;
    private final Theme theme;
    private final JLabel connectionsLabel = new JLabel();
    private final JLabel listeningLabel = new JLabel();
    private final JLabel dbLabel = new JLabel();
    private final JLabel userLabel = new JLabel();
    private final JLabel clockLabel = new JLabel();
    private User currentUser;

    public StatusBar(ManagerController controller, Theme theme) {
        super(new BorderLayout());
        this.controller = controller;
        this.theme = theme;
        setBackground(theme.surface());
        setBorder(new EmptyBorder(6, 12, 6, 12));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        left.setOpaque(false);
        left.add(connectionsLabel);
        left.add(separator());
        left.add(listeningLabel);
        left.add(separator());
        left.add(dbLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        right.setOpaque(false);
        right.add(userLabel);
        right.add(separator());
        right.add(clockLabel);

        for (JLabel label : new JLabel[]{connectionsLabel, listeningLabel, dbLabel, userLabel, clockLabel}) {
            label.setForeground(theme.textMuted());
            label.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
        }
        connectionsLabel.setForeground(theme.success());

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);

        Timer timer = new Timer(1000, event -> refresh());
        timer.start();
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        refresh();
    }

    public void refresh() {
        long connected = controller.registry().snapshots().stream()
            .filter(snapshot -> snapshot.connected()).count();
        connectionsLabel.setText("● " + connected + " connected");
        connectionsLabel.setForeground(connected > 0 ? theme.success() : theme.textMuted());

        listeningLabel.setText(":" + controller.boundPort());

        String dbPath = controller.database().jdbcUrl().replace("jdbc:sqlite:", "");
        File dbFile = new File(dbPath);
        if (dbFile.exists()) {
            double sizeMb = dbFile.length() / 1024.0 / 1024.0;
            dbLabel.setText(String.format(Locale.ROOT, "DB %.1f MB", sizeMb));
        } else {
            dbLabel.setText("DB —");
        }

        userLabel.setText(currentUser == null
            ? "not signed in"
            : currentUser.username() + " (" + currentUser.role() + ")");

        clockLabel.setText(LocalDateTime.now().format(CLOCK));
    }

    private JPanel separator() {
        JPanel separator = UIFactory.separator(theme);
        separator.setPreferredSize(new java.awt.Dimension(1, 14));
        return separator;
    }
}
