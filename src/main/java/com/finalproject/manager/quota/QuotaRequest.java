package com.finalproject.manager.quota;

import java.time.Instant;

public record QuotaRequest(
    long id,
    Instant timestamp,
    String workerId,
    String taskId,
    String taskType,
    String payload,
    int requested,
    int have,
    Integer granted,
    Instant grantedAt,
    String grantedBy
) {
    public boolean isOpen() {
        return granted == null;
    }

    public boolean canReplay() {
        return taskType != null && !taskType.isBlank();
    }
}
