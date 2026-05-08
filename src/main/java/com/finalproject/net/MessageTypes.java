package com.finalproject.net;

public final class MessageTypes {
    public static final String HELLO = "HELLO";
    public static final String PING = "PING";
    public static final String PONG = "PONG";
    public static final String METRIC = "METRIC";
    public static final String TASK = "TASK";
    public static final String TASK_ACCEPTED = "TASK_ACCEPTED";
    public static final String TASK_PROGRESS = "TASK_PROGRESS";
    public static final String TASK_DONE = "TASK_DONE";
    public static final String TASK_FAILED = "TASK_FAILED";
    public static final String NOTE = "NOTE";
    public static final String NOTE_ACK = "NOTE_ACK";
    public static final String KICK = "KICK";
    public static final String AUTH_FAILED = "AUTH_FAILED";
    public static final String QUOTA_REQUEST = "QUOTA_REQUEST";
    public static final String QUOTA_GRANT = "QUOTA_GRANT";
    public static final String REGISTER = "REGISTER";
    public static final String REGISTER_OK = "REGISTER_OK";
    public static final String REGISTER_FAILED = "REGISTER_FAILED";

    private MessageTypes() {}
}
