package com.finalproject.ui.manager;

import com.finalproject.manager.ManagerController;
import com.finalproject.model.TaskEventRecord;
import com.finalproject.model.WorkerEventRecord;
import com.finalproject.model.WorkerMetricRecord;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class EventLogPanel extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ManagerController controller;
    private final JTextArea workerArea;
    private final JTextArea taskArea;
    private final JTextArea metricArea;

    public EventLogPanel(ManagerController controller, Theme theme) {
        super(new BorderLayout());
        this.controller = controller;
        this.workerArea = UIFactory.textArea(theme);
        this.taskArea = UIFactory.textArea(theme);
        this.metricArea = UIFactory.textArea(theme);
        setBackground(theme.surface());

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(theme.surface());
        tabs.setForeground(theme.text());
        tabs.addTab("Worker", UIFactory.scroll(theme, workerArea));
        tabs.addTab("Tasks",  UIFactory.scroll(theme, taskArea));
        tabs.addTab("Metrics",UIFactory.scroll(theme, metricArea));
        add(tabs, BorderLayout.CENTER);
    }

    public void refresh() {
        StringBuilder workerOut = new StringBuilder();
        for (WorkerEventRecord event : controller.database().recentWorkerEvents(40)) {
            workerOut.append(String.format("%s  %-10s  %s%n",
                TIME.format(event.timestamp().atZone(java.time.ZoneId.systemDefault())),
                event.workerId(),
                event.eventType() + " — " + event.details()));
        }
        workerArea.setText(workerOut.toString());

        StringBuilder taskOut = new StringBuilder();
        for (TaskEventRecord event : controller.database().recentTaskEvents(40)) {
            taskOut.append(String.format("%s  %-10s  %-8s  %-8s  %s%n",
                TIME.format(event.timestamp().atZone(java.time.ZoneId.systemDefault())),
                event.workerId(), event.taskType(), event.status(), event.details()));
        }
        taskArea.setText(taskOut.toString());

        StringBuilder metricOut = new StringBuilder();
        List<WorkerMetricRecord> metrics = controller.database().recentWorkerMetrics(40);
        for (WorkerMetricRecord metric : metrics) {
            metricOut.append(String.format("%s  %-10s  cpu=%.1f%%  mem=%.1f%%  heap=%.1fMB  threads=%d  task=%s p=%d%n",
                TIME.format(metric.timestamp().atZone(java.time.ZoneId.systemDefault())),
                metric.workerId(), metric.cpu(), metric.memory(),
                metric.heapUsed() / 1024.0 / 1024.0, metric.threadCount(),
                metric.taskType(), metric.progress()));
        }
        metricArea.setText(metricOut.toString());
    }
}
