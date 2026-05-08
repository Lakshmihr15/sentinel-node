package com.finalproject.ui;

import com.finalproject.manager.WorkerRegistry;
import com.finalproject.model.MetricSample;
import com.finalproject.model.WorkerSnapshot;
import com.finalproject.ui.theme.ManagerTheme;
import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;

public class MetricChartPanel extends JPanel {
    private final WorkerRegistry registry;
    private final Theme theme;
    private String selectedWorker;
    private final Color cpuColor;
    private final Color memoryColor;
    private final Color procColor;
    private boolean showSecondaryHelp;

    public MetricChartPanel(com.finalproject.manager.ManagerController controller) {
        this(controller.registry(), ManagerTheme.INSTANCE);
    }

    public MetricChartPanel(WorkerRegistry registry, Theme theme) {
        this.registry = registry;
        this.theme = theme;
        this.cpuColor = theme.accent();
        this.memoryColor = new Color(0xF97316);
        this.procColor = theme.success();
        setBackground(theme.surface());
    }

    public void setSelectedWorker(String selectedWorker) {
        this.selectedWorker = selectedWorker;
        repaint();
    }

    public void setShowSecondaryHelp(boolean show) {
        this.showSecondaryHelp = show;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2d = (Graphics2D) graphics.create();
        UIFactory.enableAntiAlias(g2d);

        g2d.setColor(theme.surface());
        g2d.fillRect(0, 0, getWidth(), getHeight());
        drawGrid(g2d);

        g2d.setColor(theme.text());
        g2d.setFont(theme.headingFont().deriveFont(Font.BOLD, 14f));
        String title = showSecondaryHelp
            ? "Live system telemetry"
            : "Select a worker to view live metrics";
        if (selectedWorker != null && registry != null) {
            WorkerSnapshot snapshot = registry.snapshotFor(selectedWorker);
            title = snapshot.username() + " • " + snapshot.workerId();
        }
        g2d.drawString(title, 16, 22);

        if (selectedWorker != null && registry != null) {
            WorkerSnapshot snapshot = registry.snapshotFor(selectedWorker);
            List<MetricSample> history = snapshot.history();
            g2d.setFont(theme.baseFont());
            g2d.setColor(theme.textMuted());
            g2d.drawString(String.format("CPU %.1f%%   Mem %.1f%%   Progress %d%%",
                snapshot.cpu(), snapshot.memory(), snapshot.progress()), 16, 42);
            g2d.drawString(String.format("Heap %.1f MB   Threads %d   Proc CPU %.1f ms",
                snapshot.heapUsed() / 1024.0 / 1024.0, snapshot.threads(),
                history.isEmpty() ? 0.0 : history.get(history.size() - 1).procCpuMs()),
                16, 58);
            drawLegend(g2d);
            drawLine(g2d, history, true,  cpuColor,    72);
            drawLine(g2d, history, false, memoryColor, 152);
            drawProcCpu(g2d, history, procColor,      232);
        }
        g2d.dispose();
    }

    private void drawGrid(Graphics2D g2d) {
        g2d.setColor(theme.border());
        for (int x = 20; x < getWidth(); x += 80) {
            g2d.drawLine(x, 60, x, getHeight() - 20);
        }
        for (int y = 60; y < getHeight() - 20; y += 40) {
            g2d.drawLine(20, y, getWidth() - 20, y);
        }
    }

    private void drawLegend(Graphics2D g2d) {
        int lx = Math.max(300, getWidth() - 220);
        int ly = 40;
        chip(g2d, "CPU",    lx,        ly, cpuColor);
        chip(g2d, "Memory", lx + 70,   ly, memoryColor);
        chip(g2d, "Proc",   lx + 150,  ly, procColor);
    }

    private void chip(Graphics2D g2d, String label, int x, int y, Color color) {
        g2d.setColor(color);
        g2d.fillRoundRect(x, y, 12, 12, 6, 6);
        g2d.setColor(theme.text());
        g2d.drawString(label, x + 16, y + 11);
    }

    private void drawLine(Graphics2D g2d, List<MetricSample> history, boolean cpu, Color color, int topOffset) {
        if (history.size() < 2) return;
        int left = 20;
        int width = Math.max(1, getWidth() - 40);
        int height = 70;
        g2d.setColor(theme.textMuted());
        g2d.setFont(theme.baseFont());
        g2d.drawString(cpu ? "CPU %" : "Memory %", left, topOffset - 6);
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2.0f));
        for (int i = 1; i < history.size(); i++) {
            MetricSample previous = history.get(i - 1);
            MetricSample current = history.get(i);
            double prevValue = cpu ? previous.cpu() : previous.memory();
            double currentValue = cpu ? current.cpu() : current.memory();
            int x1 = left + (int) ((i - 1) * (width / (double) (history.size() - 1)));
            int x2 = left + (int) (i * (width / (double) (history.size() - 1)));
            int y1 = topOffset + height - (int) ((prevValue / 100.0) * height);
            int y2 = topOffset + height - (int) ((currentValue / 100.0) * height);
            g2d.drawLine(x1, y1, x2, y2);
        }
        g2d.setColor(theme.border());
        g2d.drawRoundRect(left, topOffset - 2, width - 20, height + 12, 12, 12);
    }

    private void drawProcCpu(Graphics2D g2d, List<MetricSample> history, Color color, int topOffset) {
        if (history.size() < 2) return;
        int left = 20;
        int width = Math.max(1, getWidth() - 40);
        int height = 50;
        g2d.setColor(theme.textMuted());
        g2d.setFont(theme.baseFont());
        g2d.drawString("Proc CPU ms", left, topOffset - 6);
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2.0f));
        double max = history.stream().mapToDouble(MetricSample::procCpuMs).max().orElse(1.0);
        if (max <= 0.0) max = 1.0;
        for (int i = 1; i < history.size(); i++) {
            MetricSample previous = history.get(i - 1);
            MetricSample current = history.get(i);
            int x1 = left + (int) ((i - 1) * (width / (double) (history.size() - 1)));
            int x2 = left + (int) (i * (width / (double) (history.size() - 1)));
            int y1 = topOffset + height - (int) ((previous.procCpuMs() / max) * height);
            int y2 = topOffset + height - (int) ((current.procCpuMs() / max) * height);
            g2d.drawLine(x1, y1, x2, y2);
        }
        g2d.setColor(theme.border());
        g2d.drawRoundRect(left, topOffset - 2, width - 20, height + 12, 12, 12);
    }
}
