package com.finalproject.ui.worker;

import com.finalproject.ui.theme.Theme;
import com.finalproject.ui.theme.UIFactory;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public class WorkerStatusPanel extends JPanel {
    private final Theme theme;
    private final Instant startTime = Instant.now();
    private final JLabel cpu;
    private final JLabel memory;
    private final JLabel heap;
    private final JLabel threads;
    private final JLabel uptime;
    private final JLabel proc;
    private final JLabel creditsLabel;

    public WorkerStatusPanel(Theme theme) {
        super(new BorderLayout(0, 6));
        this.theme = theme;
        this.cpu = readout("0.0%", theme.accent());
        this.memory = readout("0.0%", theme.warning());
        this.heap = readout("0.0 MB", theme.text());
        this.threads = readout("0", theme.text());
        this.uptime = readout("0s", theme.textMuted());
        this.proc = readout("0 ms", theme.success());
        this.creditsLabel = readout("— / —", theme.info());
        setOpaque(false);

        JPanel grid = new JPanel(new GridLayout(2, 4, 14, 14));
        grid.setOpaque(false);
        grid.add(metricCard("CPU", cpu));
        grid.add(metricCard("Memory", memory));
        grid.add(metricCard("Heap used", heap));
        grid.add(metricCard("Credits", creditsLabel));
        grid.add(metricCard("Threads", threads));
        grid.add(metricCard("Process CPU", proc));
        grid.add(metricCard("Uptime", uptime));
        add(grid, BorderLayout.CENTER);
    }

    public void updateCredits(int credits, int budget) {
        creditsLabel.setText(credits + " / " + budget);
        if (budget <= 0) {
            creditsLabel.setForeground(theme.info());
            return;
        }
        double ratio = credits / (double) budget;
        if (ratio < 0.2)      creditsLabel.setForeground(theme.danger());
        else if (ratio < 0.5) creditsLabel.setForeground(theme.warning());
        else                  creditsLabel.setForeground(theme.success());
    }

    private JLabel readout(String text, java.awt.Color color) {
        JLabel label = new JLabel(text);
        label.setFont(theme.monoFont().deriveFont(Font.BOLD, 28f));
        label.setForeground(color);
        return label;
    }

    private JPanel metricCard(String title, JLabel value) {
        JPanel inner = new JPanel(new BorderLayout(0, 4));
        inner.setOpaque(false);
        JLabel header = UIFactory.sectionLabel(theme, title);
        inner.add(header, BorderLayout.NORTH);
        inner.add(value, BorderLayout.CENTER);
        return UIFactory.card(theme, null, inner);
    }

    public void update(double cpuPct, double memoryPct, double heapMB, int threadCount, double procCpuMs) {
        cpu.setText(String.format(Locale.ROOT, "%.1f%%", cpuPct));
        memory.setText(String.format(Locale.ROOT, "%.1f%%", memoryPct));
        heap.setText(String.format(Locale.ROOT, "%.0f MB", heapMB));
        threads.setText(String.valueOf(threadCount));
        proc.setText(String.format(Locale.ROOT, "%.0f ms", procCpuMs));
        long secs = Duration.between(startTime, Instant.now()).getSeconds();
        long h = secs / 3600;
        long m = (secs % 3600) / 60;
        long s = secs % 60;
        uptime.setText(h > 0 ? String.format("%dh %dm", h, m) : String.format("%dm %02ds", m, s));
    }
}
