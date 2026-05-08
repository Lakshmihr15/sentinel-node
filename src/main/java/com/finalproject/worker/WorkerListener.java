package com.finalproject.worker;

@FunctionalInterface
public interface WorkerListener {
    void onEvent(WorkerEvent event);
}
