package com.finalproject.model;

import java.time.Instant;

public record TaskEventRecord(Instant timestamp, String workerId, String taskId, String taskType, String status, String details) {
}