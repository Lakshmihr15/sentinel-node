package com.finalproject.ui;

import com.finalproject.manager.ManagerController;
import com.finalproject.model.MetricSample;
import com.finalproject.model.WorkerSnapshot;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

public class MetricChartPanel extends JPanel {
    private static final Color BACKGROUND = new Color(0x0B1220);
    private static final Color GRID = new Color(0x243244);
    private static final Color TEXT = new Color(0xE5E7EB);
    private static final Color CPU_COLOR = new Color(0x38BDF8);
    private static final Color MEMORY_COLOR = new Color(0xF97316);

    private final ManagerController controller;
    private String selectedWorker;

    public MetricChartPanel(ManagerController controller) {
        this.controller = controller;
        setBackground(BACKGROUND);
    }

    public void setSelectedWorker(String selectedWorker) {
        this.selectedWorker = selectedWorker;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2d = (Graphics2D) graphics.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(BACKGROUND);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        drawGrid(g2d);

        g2d.setColor(TEXT);
        g2d.setFont(g2d.getFont().deriveFont(Font.BOLD, 14f));
        String title = "Select a worker to view charts";
        if (selectedWorker != null) {
            WorkerSnapshot snapshot = controller.registry().snapshotFor(selectedWorker);
            title = "Worker: " + snapshot.username() + " (" + snapshot.workerId() + ")";
        }
        g2d.drawString(title, 16, 20);

        if (selectedWorker != null) {
            WorkerSnapshot snapshot = controller.registry().snapshotFor(selectedWorker);
            List<MetricSample> history = snapshot.history();
            g2d.setFont(g2d.getFont().deriveFont(Font.PLAIN, 12f));
            g2d.drawString(String.format("CPU %.1f%%   Memory %.1f%%   Progress %d%%", snapshot.cpu(), snapshot.memory(), snapshot.progress()), 16, 40);
            g2d.drawString(String.format("Heap %.1f MB   Threads %d   ProcCPU %.1f ms", snapshot.heapUsed() / 1024.0 / 1024.0, snapshot.threads(), snapshot.history().isEmpty() ? 0.0 : snapshot.history().get(snapshot.history().size()-1).procCpuMs()), 16, 56);
            drawLegend(g2d);
            drawLine(g2d, history, true, CPU_COLOR, 62);
            drawLine(g2d, history, false, MEMORY_COLOR, 142);
            drawLineProcCpu(g2d, history, new Color(0x34D399), 222);
        }

        g2d.dispose();
    }

    private void drawGrid(Graphics2D g2d) {
        g2d.setColor(GRID);
        for (int x = 20; x < getWidth(); x += 80) {
            g2d.drawLine(x, 50, x, getHeight() - 20);
        }
        for (int y = 50; y < getHeight() - 20; y += 40) {
            g2d.drawLine(20, y, getWidth() - 20, y);
        }
    }

    private void drawLegend(Graphics2D g2d) {
        int lx = 300;
        int ly = 40;
        g2d.setColor(CPU_COLOR);
        g2d.fillRoundRect(lx, ly, 12, 12, 6, 6);
        g2d.setColor(TEXT);
        g2d.drawString("CPU", lx + 18, ly + 11);
        g2d.setColor(MEMORY_COLOR);
        g2d.fillRoundRect(lx + 60, ly, 12, 12, 6, 6);
        g2d.setColor(TEXT);
        g2d.drawString("Memory", lx + 78, ly + 11);
    }

    private void drawLine(Graphics2D g2d, List<MetricSample> history, boolean cpu, Color color, int topOffset) {
        if (history.size() < 2) {
            return;
        }
        int left = 20;
        int width = Math.max(1, getWidth() - 40);
        int height = 90;

        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawString(cpu ? "CPU %" : "Memory %", left, topOffset - 12);
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

        g2d.setColor(new Color(255, 255, 255, 80));
        g2d.drawRoundRect(left, topOffset - 2, width - 20, height + 12, 12, 12);
    }

    private void drawLineProcCpu(Graphics2D g2d, List<MetricSample> history, Color color, int topOffset) {
        if (history.size() < 2) {
            return;
        }
        int left = 20;
        int width = Math.max(1, getWidth() - 40);
        int height = 60;

        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawString("Proc CPU ms", left, topOffset - 12);
        double max = history.stream().mapToDouble(MetricSample::procCpuMs).max().orElse(1.0);
        for (int i = 1; i < history.size(); i++) {
            MetricSample previous = history.get(i - 1);
            MetricSample current = history.get(i);
            double prevValue = previous.procCpuMs();
            double currentValue = current.procCpuMs();
            int x1 = left + (int) ((i - 1) * (width / (double) (history.size() - 1)));
            int x2 = left + (int) (i * (width / (double) (history.size() - 1)));
            int y1 = topOffset + height - (int) ((prevValue / max) * height);
            int y2 = topOffset + height - (int) ((currentValue / max) * height);
            g2d.drawLine(x1, y1, x2, y2);
        }

        g2d.setColor(new Color(255, 255, 255, 80));
        g2d.drawRoundRect(left, topOffset - 2, width - 20, height + 12, 12, 12);
    }
}