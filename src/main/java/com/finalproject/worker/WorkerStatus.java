package com.finalproject.worker;

public record WorkerStatus(boolean busy, String taskType, String taskId, int progress) {
    public static WorkerStatus idle() {
        return new WorkerStatus(false, "IDLE", "", 0);
    }
}
