package com.finalproject.ui.worker;

import com.finalproject.app.AppConfig;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;
import com.finalproject.ui.theme.WorkerTheme;
import com.finalproject.worker.WorkerClient;
import com.finalproject.worker.WorkerEvent;
import com.finalproject.worker.WorkerListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;

public class WorkerFrame extends JFrame implements WorkerListener {
    private static final Theme THEME = WorkerTheme.INSTANCE;

    private final WorkerClient worker;
    private final WorkerStatusPanel statusPanel = new WorkerStatusPanel(THEME);
    private final WorkerTaskPanel taskPanel = new WorkerTaskPanel(THEME);
    private final WorkerInboxPanel inboxPanel;
    private final JLabel connectionLabel = new JLabel("● connecting…");
    private final JLabel managerLabel = new JLabel();

    public WorkerFrame(WorkerClient worker, AppConfig config) {
        super("SentinelNode Worker — " + display(worker));
        this.worker = worker;
        this.inboxPanel = new WorkerInboxPanel(THEME, worker);
        UIFactory.applyGlobalLookAndFeel(THEME);
        setIconImage(buildIcon());

        configureFrame();
        worker.addListener(this);
    }

    private static String display(WorkerClient worker) {
        String username = worker.username();
        return (username == null || username.isBlank() ? worker.workerId() : username);
    }

    private void configureFrame() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(THEME.background());
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(),    BorderLayout.CENTER);
        add(buildFooter(),  BorderLayout.SOUTH);

        setSize(new Dimension(960, 620));
        setLocationByPlatform(true);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(THEME.surface());
        header.setBorder(new EmptyBorder(14, 18, 12, 18));

        JLabel title = new JLabel("SentinelNode Worker");
        title.setFont(THEME.headingFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(THEME.accent());

        JLabel subtitle = new JLabel(display(worker) + "  •  worker id " + worker.workerId());
        subtitle.setFont(THEME.baseFont());
        subtitle.setForeground(THEME.textMuted());

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(title, BorderLayout.NORTH);
        left.add(subtitle, BorderLayout.SOUTH);

        connectionLabel.setForeground(THEME.warning());
        connectionLabel.setFont(THEME.baseFont().deriveFont(Font.BOLD, 12f));
        managerLabel.setForeground(THEME.textMuted());
        managerLabel.setFont(THEME.baseFont());
        managerLabel.setText(worker.managerHost() + ":" + worker.managerPort());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(connectionLabel);
        right.add(separator());
        right.add(managerLabel);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(THEME.background());
        container.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);
        left.add(UIFactory.card(THEME, "Live system", statusPanel), BorderLayout.CENTER);
        left.add(UIFactory.card(THEME, "Current task", taskPanel), BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(UIFactory.card(THEME, "Manager messages", inboxPanel), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setBackground(THEME.background());
        split.setBorder(null);
        split.setResizeWeight(0.62);
        split.setDividerSize(6);

        container.add(split, BorderLayout.CENTER);
        return container;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(THEME.surface());
        footer.setBorder(new EmptyBorder(8, 18, 8, 18));

        JLabel version = new JLabel("SentinelNode " + AppConfig.VERSION);
        version.setForeground(THEME.textMuted());
        version.setFont(THEME.baseFont());

        JButton disconnect = UIFactory.dangerButton(THEME, "Disconnect");
        disconnect.addActionListener(event -> {
            worker.shutdown();
            dispose();
        });

        footer.add(version, BorderLayout.WEST);
        footer.add(disconnect, BorderLayout.EAST);
        return footer;
    }

    private JPanel separator() {
        JPanel separator = UIFactory.separator(THEME);
        separator.setPreferredSize(new Dimension(1, 14));
        return separator;
    }

    private Image buildIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        UIFactory.enableAntiAlias(g2d);
        g2d.setColor(new Color(0x10302A));
        g2d.fillRoundRect(0, 0, 64, 64, 16, 16);
        g2d.setColor(THEME.accent());
        g2d.setFont(new Font("SansSerif", Font.BOLD, 38));
        g2d.drawString("W", 18, 46);
        g2d.dispose();
        return image;
    }

    @Override
    public void onEvent(WorkerEvent event) {
        SwingUtilities.invokeLater(() -> handle(event));
    }

    private void handle(WorkerEvent event) {
        if (event instanceof WorkerEvent.Connected connected) {
            connectionLabel.setText("● connected");
            connectionLabel.setForeground(THEME.success());
            managerLabel.setText(connected.host() + ":" + connected.port());
        } else if (event instanceof WorkerEvent.Disconnected disconnected) {
            connectionLabel.setText("● disconnected");
            connectionLabel.setForeground(THEME.danger());
            taskPanel.onIdle();
        } else if (event instanceof WorkerEvent.MetricSampled metric) {
            statusPanel.update(metric.cpu(), metric.memory(), metric.heapMB(),
                metric.threads(), metric.procCpuMs());
        } else if (event instanceof WorkerEvent.TaskStarted started) {
            taskPanel.onStarted(started.taskId(), started.taskType(), started.payload());
        } else if (event instanceof WorkerEvent.TaskProgress progress) {
            taskPanel.onProgress(progress.progress());
        } else if (event instanceof WorkerEvent.TaskFinished finished) {
            taskPanel.onFinished(finished.result(), finished.success());
        } else if (event instanceof WorkerEvent.NoteReceived note) {
            inboxPanel.onNoteReceived(note.noteId(), note.fromUser(), note.body(), note.ts());
        } else if (event instanceof WorkerEvent.AuthFailed authFailed) {
            connectionLabel.setText("● auth failed");
            connectionLabel.setForeground(THEME.danger());
            managerLabel.setText(authFailed.reason());
        }
    }
}
