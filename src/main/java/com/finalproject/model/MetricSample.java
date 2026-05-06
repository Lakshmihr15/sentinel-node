package com.finalproject.model;

import java.time.Instant;

public record MetricSample(Instant timestamp, double cpu, double memory, double heapUsed, int threads, double procCpuMs) {
}