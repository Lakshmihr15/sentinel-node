package com.finalproject.worker;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;

public class SystemMetrics {
    private final OperatingSystemMXBean osBean;

    public SystemMetrics() {
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    public double cpuPercent() {
        double load = osBean.getProcessCpuLoad();
        if (load < 0) {
            load = osBean.getSystemCpuLoad();
        }
        return load < 0 ? 0 : round(load * 100.0);
    }

    public double memoryPercent() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        return max <= 0 ? 0 : round((used * 100.0) / max);
    }

    private double round(double value) {
        return Math.max(0, Math.min(100, Math.round(value * 10.0) / 10.0));
    }

    public long processCpuTimeNs() {
        try {
            return osBean.getProcessCpuTime();
        } catch (Throwable t) {
            return 0L;
        }
    }

    public double heapUsedBytes() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            return (double) used;
        } catch (Throwable t) {
            return 0.0;
        }
    }

    public int threadCount() {
        try {
            return java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount();
        } catch (Throwable t) {
            return 0;
        }
    }
}