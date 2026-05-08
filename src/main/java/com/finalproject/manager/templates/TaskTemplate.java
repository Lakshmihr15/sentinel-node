package com.finalproject.manager.templates;

public record TaskTemplate(long id, String name, String taskType, String payload, String createdBy) {
    public String describe() {
        return name + " [" + taskType + "] " + (payload == null ? "" : payload);
    }
}
