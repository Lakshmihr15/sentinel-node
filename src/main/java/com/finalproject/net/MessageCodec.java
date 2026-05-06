package com.finalproject.net;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public final class MessageCodec {
    private MessageCodec() {
    }

    public static String encode(Message message) {
        StringJoiner joiner = new StringJoiner(";");
        for (Map.Entry<String, String> entry : message.fields().entrySet()) {
            joiner.add(entry.getKey() + "=" + urlEncode(entry.getValue()));
        }
        String suffix = joiner.length() == 0 ? "" : "|" + joiner;
        return message.type() + suffix;
    }

    public static Message decode(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Message line cannot be blank");
        }

        String[] parts = line.split("\\|", 2);
        String type = parts[0].trim();
        Map<String, String> fields = new LinkedHashMap<>();
        if (parts.length > 1 && !parts[1].isBlank()) {
            String[] entries = parts[1].split(";");
            for (String entry : entries) {
                if (entry.isBlank()) {
                    continue;
                }
                String[] pair = entry.split("=", 2);
                String key = pair[0].trim();
                String value = pair.length > 1 ? urlDecode(pair[1]) : "";
                fields.put(key, value);
            }
        }
        return new Message(type, fields);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}