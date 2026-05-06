package com.finalproject.model;

import java.time.Instant;

public record WorkerMetricRecord(
    Instant timestamp,
    String workerId,
    double cpu,
    double memory,
    long procCpuNs,
    double heapUsed,
    int threadCount,
    String taskType,
    String taskId,
    int progress
) {
}