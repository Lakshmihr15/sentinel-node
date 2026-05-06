package com.finalproject.model;

import java.time.Instant;

public record WorkerEventRecord(Instant timestamp, String workerId, String eventType, String details) {
}