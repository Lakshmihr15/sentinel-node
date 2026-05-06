package com.finalproject.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTest {

    @Test
    void roundTripPreservesFields() {
        Message original = Message.of("TASK")
            .with("workerId", "worker-1")
            .with("payload", "a=b;c|d");

        Message decoded = MessageCodec.decode(MessageCodec.encode(original));

        assertEquals(original.type(), decoded.type());
        assertEquals(original.fields().get("workerId"), decoded.fields().get("workerId"));
        assertEquals(original.fields().get("payload"), decoded.fields().get("payload"));
    }

    @Test
    void encodeAndDecodeNoFields() {
        Message original = Message.of("PING");
        String encoded = MessageCodec.encode(original);
        Message decoded = MessageCodec.decode(encoded);

        assertEquals("PING", decoded.type());
        assertTrue(decoded.fields().isEmpty());
    }

    @Test
    void fieldWithBlankValue() {
        Message original = Message.of("HELLO").with("token", "");
        Message decoded = MessageCodec.decode(MessageCodec.encode(original));

        assertEquals("HELLO", decoded.type());
        assertEquals("", decoded.fields().get("token"));
    }

    @Test
    void specialCharactersInPayload() {
        String special = "hello world & foo=bar;baz|qux";
        Message original = Message.of("METRIC").with("details", special);
        Message decoded = MessageCodec.decode(MessageCodec.encode(original));

        assertEquals(special, decoded.fields().get("details"));
    }

    @Test
    void multipleFieldsPreserveAllValues() {
        Message original = Message.of("METRIC")
            .with("cpu", "55.5")
            .with("memory", "32.1")
            .with("threads", "12")
            .with("taskType", "CALC");

        Message decoded = MessageCodec.decode(MessageCodec.encode(original));

        assertEquals("55.5", decoded.fields().get("cpu"));
        assertEquals("32.1", decoded.fields().get("memory"));
        assertEquals("12", decoded.fields().get("threads"));
        assertEquals("CALC", decoded.fields().get("taskType"));
    }

    @Test
    void decodeBlankLineThrows() {
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode(""));
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode("   "));
    }
}