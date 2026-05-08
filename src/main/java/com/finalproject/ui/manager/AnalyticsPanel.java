package com.finalproject.ui.manager;

import com.finalproject.manager.ManagerController;
import com.finalproject.model.WorkerMetricRecord;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class AnalyticsPanel extends JPanel {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final ManagerController controller;
    private final Theme theme;
    private final JTextField filter;
    private final JComboBox<String> kind = new JComboBox<>(new String[]{"Worker events", "Task events", "Worker metrics"});
    private final JTextArea output;
    private Consumer<String> toastSink = message -> {};

    public AnalyticsPanel(ManagerController controller, Theme theme) {
        super(new BorderLayout(0, 8));
        this.controller = controller;
        this.theme = theme;
        this.filter = UIFactory.textField(theme, 16);
        this.output = UIFactory.textArea(theme);
        setBackground(theme.surface());

        kind.setBackground(theme.surfaceMuted());
        kind.setForeground(theme.text());

        JButton run = UIFactory.primaryButton(theme, "Run query");
        JButton exportCsv = UIFactory.secondaryButton(theme, "Export metrics CSV");
        run.addActionListener(event -> runQuery());
        exportCsv.addActionListener(event -> exportCsv());
        kind.addActionListener(event -> runQuery());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setBackground(theme.surface());
        controls.add(UIFactory.formLabel(theme, "Filter (worker contains):"));
        controls.add(filter);
        controls.add(kind);
        controls.add(run);
        controls.add(exportCsv);

        add(controls, BorderLayout.NORTH);
        add(UIFactory.scroll(theme, output), BorderLayout.CENTER);

        runQuery();
    }

    public void setToastSink(Consumer<String> sink) {
        this.toastSink = sink == null ? message -> {} : sink;
    }

    private void runQuery() {
        String filterText = filter.getText().trim();
        StringBuilder sb = new StringBuilder();
        switch (kind.getSelectedIndex()) {
            case 0 -> controller.database().recentWorkerEvents(200).stream()
                .filter(event -> filterText.isEmpty() || event.workerId().contains(filterText))
                .forEach(event -> sb.append(String.format("%s  %-12s  %-22s  %s%n",
                    ISO.format(event.timestamp()), event.workerId(), event.eventType(), event.details())));
            case 1 -> controller.database().recentTaskEvents(200).stream()
                .filter(event -> filterText.isEmpty() || event.workerId().contains(filterText))
                .forEach(event -> sb.append(String.format("%s  %-12s  %-8s  %-9s  %s%n",
                    ISO.format(event.timestamp()), event.workerId(), event.taskType(), event.status(), event.details())));
            default -> controller.database().recentWorkerMetrics(200).stream()
                .filter(event -> filterText.isEmpty() || event.workerId().contains(filterText))
                .forEach(event -> sb.append(String.format(Locale.ROOT,
                    "%s  %-12s  cpu=%5.1f%%  mem=%5.1f%%  heap=%5.1fMB  threads=%2d  task=%-8s  p=%3d%n",
                    ISO.format(event.timestamp()), event.workerId(), event.cpu(), event.memory(),
                    event.heapUsed() / 1024.0 / 1024.0, event.threadCount(),
                    event.taskType(), event.progress())));
        }
        output.setText(sb.toString());
        output.setCaretPosition(0);
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("worker_metrics_" + System.currentTimeMillis() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write("timestamp,worker_id,cpu,memory,proc_cpu_ns,heap_used_bytes,thread_count,task_type,task_id,progress");
            writer.newLine();
            List<WorkerMetricRecord> rows = controller.database().recentWorkerMetrics(5000);
            for (WorkerMetricRecord row : rows) {
                writer.write(String.format(Locale.ROOT, "%s,%s,%.2f,%.2f,%d,%.0f,%d,%s,%s,%d",
                    ISO.format(row.timestamp()), row.workerId(),
                    row.cpu(), row.memory(), row.procCpuNs(), row.heapUsed(), row.threadCount(),
                    row.taskType(), row.taskId(), row.progress()));
                writer.newLine();
            }
            toastSink.accept("Exported " + rows.size() + " rows to " + file.getName());
        } catch (IOException e) {
            toastSink.accept("Export failed: " + e.getMessage());
        }
    }
}
