package com.finalproject.worker;

import com.finalproject.net.Message;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public final class WorkerTelemetry {
    private WorkerTelemetry() {
    }

    public static Message sample(WorkerStatus status) {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double load = osBean.getSystemLoadAverage();
        double cpu = load < 0 ? 0.0 : Math.min(100.0, load * 25.0);
        Runtime runtime = Runtime.getRuntime();
        double memory = (runtime.totalMemory() - runtime.freeMemory()) * 100.0 / runtime.totalMemory();
        return Message.of("METRIC")
            .with("cpu", String.format("%.2f", cpu))
            .with("memory", String.format("%.2f", memory))
            .with("taskType", status.taskType())
            .with("taskId", status.taskId())
            .with("progress", Integer.toString(status.progress()));
    }
}
