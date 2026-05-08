package com.finalproject.ui.worker;

import com.finalproject.ui.theme.Theme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;

public class WorkerTaskPanel extends JPanel {
    private final Theme theme;
    private final JLabel taskTypeLabel;
    private final JLabel taskIdLabel;
    private final JLabel payloadLabel;
    private final JLabel resultLabel;
    private final JProgressBar progress;

    public WorkerTaskPanel(Theme theme) {
        super(new BorderLayout(0, 8));
        this.theme = theme;
        setOpaque(false);
        setBorder(new EmptyBorder(0, 0, 0, 0));

        this.taskTypeLabel = bigLabel("IDLE", theme.accent());
        this.taskIdLabel = mutedLabel("—");
        this.payloadLabel = mutedLabel("waiting for work…");
        this.resultLabel = new JLabel(" ");
        this.resultLabel.setForeground(theme.textMuted());
        this.resultLabel.setFont(theme.baseFont());

        this.progress = new JProgressBar(0, 100);
        this.progress.setStringPainted(true);
        this.progress.setBackground(theme.surfaceMuted());
        this.progress.setForeground(theme.accent());
        this.progress.setValue(0);
        this.progress.setString("idle");
        this.progress.setBorderPainted(false);
        this.progress.setPreferredSize(new java.awt.Dimension(0, 26));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(taskTypeLabel, BorderLayout.NORTH);
        header.add(taskIdLabel, BorderLayout.SOUTH);

        JPanel meta = new JPanel(new GridLayout(0, 1, 0, 4));
        meta.setOpaque(false);
        meta.add(payloadLabel);
        meta.add(resultLabel);

        add(header, BorderLayout.NORTH);
        add(progress, BorderLayout.CENTER);
        add(meta, BorderLayout.SOUTH);
    }

    private JLabel bigLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(theme.headingFont().deriveFont(Font.BOLD, 22f));
        return label;
    }

    private JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(theme.textMuted());
        label.setFont(theme.baseFont());
        return label;
    }

    public void onStarted(String taskId, String taskType, String payload) {
        taskTypeLabel.setText(taskType);
        taskTypeLabel.setForeground(theme.warning());
        taskIdLabel.setText(taskId);
        payloadLabel.setText(payload == null || payload.isEmpty() ? "(no payload)" : "payload: " + payload);
        resultLabel.setText(" ");
        progress.setValue(0);
        progress.setString("starting");
        progress.setForeground(theme.accent());
    }

    public void onProgress(int value) {
        progress.setValue(value);
        progress.setString(value + "%");
    }

    public void onFinished(String result, boolean success) {
        progress.setValue(100);
        progress.setString(success ? "done" : "failed");
        progress.setForeground(success ? theme.success() : theme.danger());
        taskTypeLabel.setForeground(success ? theme.success() : theme.danger());
        resultLabel.setText(success ? "result: " + result : "error: " + result);
    }

    public void onIdle() {
        taskTypeLabel.setText("IDLE");
        taskTypeLabel.setForeground(theme.accent());
        taskIdLabel.setText("—");
        payloadLabel.setText("waiting for work…");
        progress.setValue(0);
        progress.setString("idle");
        progress.setForeground(theme.accent());
    }
}
