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
import java.util.Locale;

public class MetricChartPanel extends JPanel {
    private static final int PADDING_X = 18;
    private static final int HEADER_HEIGHT = 80;
    private static final int FOOTER_PADDING = 16;
    private static final int GRAPH_GAP = 28;
    private static final int LABEL_GUTTER = 18;

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
        // Repaint at 2 Hz so changes appear faster than the 1 Hz dashboard tick.
        new javax.swing.Timer(500, event -> repaint()) {{ start(); }};
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

        int width = getWidth();
        int height = getHeight();

        WorkerSnapshot snapshot = (selectedWorker != null && registry != null)
            ? registry.snapshotFor(selectedWorker) : null;

        drawHeader(g2d, snapshot, width);

        if (snapshot != null) {
            int chartTop = HEADER_HEIGHT;
            int chartBottom = height - FOOTER_PADDING;
            int chartHeight = Math.max(60, chartBottom - chartTop);
            int slotHeight = (chartHeight - 2 * GRAPH_GAP) / 3;
            if (slotHeight < 40) slotHeight = 40;

            List<MetricSample> history = snapshot.history();
            drawPercentLine(g2d, history, true,  "CPU %",    cpuColor,    chartTop);
            drawPercentLine(g2d, history, false, "Memory %", memoryColor, chartTop + (slotHeight + GRAPH_GAP));
            drawProcCpu(g2d, history, "Proc CPU ms",         procColor,   chartTop + 2 * (slotHeight + GRAPH_GAP),
                slotHeight - 8);
        }
        g2d.dispose();
    }

    private void drawHeader(Graphics2D g2d, WorkerSnapshot snapshot, int width) {
        g2d.setColor(theme.text());
        g2d.setFont(theme.headingFont().deriveFont(Font.BOLD, 14f));
        String title = snapshot == null
            ? (showSecondaryHelp ? "Live system telemetry"
                                 : "Select a worker to view live metrics")
            : snapshot.username() + " • " + snapshot.workerId();
        g2d.drawString(title, PADDING_X, 22);

        // Legend top-right (uses g2d FontMetrics for layout — won't collide with text on the left).
        if (snapshot != null) {
            drawLegend(g2d, width);
            g2d.setFont(theme.baseFont());
            g2d.setColor(theme.textMuted());
            String row1 = String.format(Locale.ROOT,
                "CPU %.1f%%   Mem %.1f%%   Progress %d%%",
                snapshot.cpu(), snapshot.memory(), snapshot.progress());
            String row2 = String.format(Locale.ROOT,
                "Heap %.1f MB   Threads %d   Credits %d / %d",
                snapshot.heapUsed() / 1024.0 / 1024.0,
                snapshot.threads(),
                Math.max(0, snapshot.credits()),
                Math.max(0, snapshot.budget()));
            g2d.drawString(row1, PADDING_X, 44);
            g2d.drawString(row2, PADDING_X, 62);
        }
    }

    private void drawLegend(Graphics2D g2d, int width) {
        g2d.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
        java.awt.FontMetrics fm = g2d.getFontMetrics();
        int chipWidth = 12;
        int chipPad = 4;
        int gap = 16;
        int textCpu = fm.stringWidth("CPU");
        int textMem = fm.stringWidth("Memory");
        int textProc = fm.stringWidth("Proc");
        int totalWidth = (chipWidth + chipPad + textCpu) + gap
                       + (chipWidth + chipPad + textMem) + gap
                       + (chipWidth + chipPad + textProc);
        int x = width - PADDING_X - totalWidth;
        if (x < 280) return; // don't draw if it would overlap with the title text
        int y = 18;
        x = chip(g2d, fm, "CPU",    cpuColor,    x, y, chipWidth, chipPad) + gap;
        x = chip(g2d, fm, "Memory", memoryColor, x, y, chipWidth, chipPad) + gap;
            chip(g2d, fm, "Proc",   procColor,   x, y, chipWidth, chipPad);
    }

    private int chip(Graphics2D g2d, java.awt.FontMetrics fm, String label,
                     Color color, int x, int y, int chipWidth, int chipPad) {
        g2d.setColor(color);
        g2d.fillRoundRect(x, y - chipWidth + 2, chipWidth, chipWidth, 4, 4);
        g2d.setColor(theme.text());
        g2d.drawString(label, x + chipWidth + chipPad, y);
        return x + chipWidth + chipPad + fm.stringWidth(label);
    }

    private void drawPercentLine(Graphics2D g2d, List<MetricSample> history,
                                 boolean cpu, String label, Color color, int top) {
        int slotBottom = top + slotHeight();
        drawSlotLabel(g2d, label, top);
        int graphTop = top + LABEL_GUTTER;
        int graphHeight = slotBottom - graphTop;
        drawSlotFrame(g2d, graphTop, graphHeight);
        int left = PADDING_X;
        int right = getWidth() - PADDING_X;
        int width = Math.max(1, right - left);

        // Auto-scale: find the actual range in the visible window so even small
        // fluctuations are visible. Floor is 0; ceiling is max(latest * 1.4, 10).
        double dataMax = 0.0;
        for (MetricSample sample : history) {
            double value = cpu ? sample.cpu() : sample.memory();
            if (value > dataMax) dataMax = value;
        }
        double scale = Math.max(10.0, Math.min(100.0, Math.ceil(dataMax * 1.4 / 5.0) * 5.0));

        drawTicks(g2d, graphTop, graphHeight, left, right,
            String.format(Locale.ROOT, "%.0f%%", scale),
            String.format(Locale.ROOT, "%.0f%%", scale / 2),
            "0%");
        drawCurrentValue(g2d, history, cpu ? Mode.CPU : Mode.MEMORY, color,
            graphTop, graphHeight, right);

        if (history.size() < 2) return;
        Graphics2D clipped = (Graphics2D) g2d.create();
        try {
            clipped.setClip(left, graphTop, right - left, graphHeight);
            int n = history.size();
            int[] xs = new int[n];
            int[] ys = new int[n];
            for (int i = 0; i < n; i++) {
                MetricSample sample = history.get(i);
                double value = cpu ? sample.cpu() : sample.memory();
                xs[i] = left + (int) (i * (width / (double) Math.max(1, n - 1)));
                ys[i] = (int) (graphTop + graphHeight - (value / scale) * graphHeight);
            }
            int[] fillX = new int[n + 2];
            int[] fillY = new int[n + 2];
            System.arraycopy(xs, 0, fillX, 0, n);
            System.arraycopy(ys, 0, fillY, 0, n);
            fillX[n] = xs[n - 1];
            fillY[n] = graphTop + graphHeight;
            fillX[n + 1] = xs[0];
            fillY[n + 1] = graphTop + graphHeight;
            clipped.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
            clipped.fillPolygon(fillX, fillY, n + 2);
            clipped.setColor(color);
            clipped.setStroke(new BasicStroke(2.5f));
            clipped.drawPolyline(xs, ys, n);
            clipped.fillOval(xs[n - 1] - 4, ys[n - 1] - 4, 8, 8);
        } finally {
            clipped.dispose();
        }
    }

    private void drawProcCpu(Graphics2D g2d, List<MetricSample> history, String label,
                             Color color, int top, int customSlotHeight) {
        int slotBottom = top + customSlotHeight;
        drawSlotLabel(g2d, label, top);
        int graphTop = top + LABEL_GUTTER;
        int graphHeight = slotBottom - graphTop;
        drawSlotFrame(g2d, graphTop, graphHeight);
        int left = PADDING_X;
        int right = getWidth() - PADDING_X;
        int width = Math.max(1, right - left);
        double max = history.stream().mapToDouble(MetricSample::procCpuMs).max().orElse(1.0);
        if (max <= 0.0) max = 1.0;
        drawTicks(g2d, graphTop, graphHeight, left, right,
            String.format(Locale.ROOT, "%.0f", max),
            String.format(Locale.ROOT, "%.0f", max / 2),
            "0");
        drawCurrentValue(g2d, history, Mode.PROC, color, graphTop, graphHeight, right);

        if (history.size() < 2) return;
        Graphics2D clipped = (Graphics2D) g2d.create();
        try {
            clipped.setClip(left, graphTop, right - left, graphHeight);
            int n = history.size();
            int[] xs = new int[n];
            int[] ys = new int[n];
            for (int i = 0; i < n; i++) {
                MetricSample sample = history.get(i);
                xs[i] = left + (int) (i * (width / (double) Math.max(1, n - 1)));
                ys[i] = (int) (graphTop + graphHeight - (sample.procCpuMs() / max) * graphHeight);
            }
            int[] fillX = new int[n + 2];
            int[] fillY = new int[n + 2];
            System.arraycopy(xs, 0, fillX, 0, n);
            System.arraycopy(ys, 0, fillY, 0, n);
            fillX[n] = xs[n - 1];
            fillY[n] = graphTop + graphHeight;
            fillX[n + 1] = xs[0];
            fillY[n + 1] = graphTop + graphHeight;
            clipped.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
            clipped.fillPolygon(fillX, fillY, n + 2);
            clipped.setColor(color);
            clipped.setStroke(new BasicStroke(2.5f));
            clipped.drawPolyline(xs, ys, n);
            clipped.fillOval(xs[n - 1] - 4, ys[n - 1] - 4, 8, 8);
        } finally {
            clipped.dispose();
        }
    }

    private enum Mode { CPU, MEMORY, PROC }

    private void drawCurrentValue(Graphics2D g2d, List<MetricSample> history, Mode mode,
                                  Color color, int graphTop, int graphHeight, int right) {
        if (history.isEmpty()) return;
        MetricSample latest = history.get(history.size() - 1);
        String text = switch (mode) {
            case CPU    -> String.format(Locale.ROOT, "%.1f%%", latest.cpu());
            case MEMORY -> String.format(Locale.ROOT, "%.1f%%", latest.memory());
            case PROC   -> String.format(Locale.ROOT, "%.0f ms", latest.procCpuMs());
        };
        // Big bold readout in the top-right of the slot — easy to see from across the room
        g2d.setFont(theme.headingFont().deriveFont(Font.BOLD, 18f));
        java.awt.FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int x = right - textWidth - 10;
        int y = graphTop + 22;
        g2d.setColor(color);
        g2d.drawString(text, x, y);
    }

    private void drawTicks(Graphics2D g2d, int graphTop, int graphHeight,
                           int left, int right, String topLabel, String midLabel,
                           String bottomLabel) {
        g2d.setFont(theme.baseFont().deriveFont(Font.PLAIN, 10f));
        g2d.setColor(theme.textMuted());
        int textX = left + 4;
        g2d.drawString(topLabel,    textX, graphTop + 10);
        g2d.drawString(midLabel,    textX, graphTop + graphHeight / 2 + 4);
        g2d.drawString(bottomLabel, textX, graphTop + graphHeight - 4);
    }

    private void drawSlotLabel(Graphics2D g2d, String label, int top) {
        g2d.setColor(theme.textMuted());
        g2d.setFont(theme.baseFont().deriveFont(Font.BOLD, 11f));
        g2d.drawString(label, PADDING_X, top + 12);
    }

    private void drawSlotFrame(Graphics2D g2d, int graphTop, int graphHeight) {
        int left = PADDING_X;
        int right = getWidth() - PADDING_X;
        // Subtle solid-fill panel so each graph reads as its own card.
        g2d.setColor(theme.surfaceMuted());
        g2d.fillRoundRect(left, graphTop, right - left, graphHeight, 10, 10);
        // Horizontal grid lines (4 divisions).
        g2d.setColor(theme.border());
        g2d.setStroke(new BasicStroke(1.0f));
        int gridStep = Math.max(20, graphHeight / 4);
        for (int y = graphTop + gridStep; y < graphTop + graphHeight; y += gridStep) {
            g2d.drawLine(left, y, right, y);
        }
        // Outer frame on top of the fill.
        g2d.drawRoundRect(left, graphTop, right - left, graphHeight, 10, 10);
    }

    private int slotHeight() {
        int chartTop = HEADER_HEIGHT;
        int chartBottom = getHeight() - FOOTER_PADDING;
        int chartHeight = Math.max(60, chartBottom - chartTop);
        int h = (chartHeight - 2 * GRAPH_GAP) / 3;
        return Math.max(40, h);
    }
}
