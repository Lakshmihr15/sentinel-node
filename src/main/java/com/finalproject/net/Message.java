package com.finalproject.net;

import java.util.LinkedHashMap;
import java.util.Map;

public record Message(String type, Map<String, String> fields) {
    public Message {
        fields = Map.copyOf(new LinkedHashMap<>(fields));
    }

    public static Message of(String type) {
        return new Message(type, Map.of());
    }

    public Message with(String key, String value) {
        Map<String, String> copy = new LinkedHashMap<>(fields);
        copy.put(key, value);
        return new Message(type, copy);
    }
}