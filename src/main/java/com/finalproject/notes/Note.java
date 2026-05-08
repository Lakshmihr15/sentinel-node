package com.finalproject.notes;

import java.time.Instant;

public record Note(
    long id,
    Instant timestamp,
    String senderUsername,
    String recipientWorkerId,
    String recipientTag,
    String body,
    Instant deliveredAt,
    Instant ackAt
) {
    public boolean isBroadcast() {
        return (recipientWorkerId == null || recipientWorkerId.isBlank())
            && (recipientTag == null || recipientTag.isBlank());
    }

    public boolean isAcked() {
        return ackAt != null;
    }

    public boolean isDelivered() {
        return deliveredAt != null;
    }
}
