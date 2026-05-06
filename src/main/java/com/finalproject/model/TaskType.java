package com.finalproject.model;

public enum TaskType {
    CALC,
    SEARCH,
    SLEEP;

    public static TaskType fromString(String value) {
        return TaskType.valueOf(value.trim().toUpperCase());
    }
}