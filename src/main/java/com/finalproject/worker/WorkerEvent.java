package com.finalproject.worker;

import com.finalproject.net.Message;

public sealed interface WorkerEvent {
    record Connected(String host, int port)                 implements WorkerEvent {}
    record Disconnected(String reason)                       implements WorkerEvent {}
    record TaskStarted(String taskId, String taskType, String payload) implements WorkerEvent {}
    record TaskProgress(String taskId, String taskType, int progress)  implements WorkerEvent {}
    record TaskFinished(String taskId, String taskType, String result, boolean success) implements WorkerEvent {}
    record MetricSampled(double cpu, double memory, double heapMB, int threads, double procCpuMs) implements WorkerEvent {}
    record NoteReceived(long noteId, String fromUser, String body, String ts) implements WorkerEvent {}
    record AuthFailed(String reason) implements WorkerEvent {}
    record QuotaChanged(int credits, int budget) implements WorkerEvent {}
    record QuotaExhausted(String taskId, String taskType, int needed, int have) implements WorkerEvent {}
    record QuotaGranted(int amount, int credits) implements WorkerEvent {}
    record Raw(Message message) implements WorkerEvent {}
}
