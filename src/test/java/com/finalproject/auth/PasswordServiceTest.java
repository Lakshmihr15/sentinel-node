package com.finalproject.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {

    @Test
    void hashAndVerifyRoundTripSucceeds() {
        PasswordService passwordService = new PasswordService();

        String hash = passwordService.hashPassword("StrongPass123");

        assertTrue(passwordService.verifyPassword("StrongPass123", hash));
        assertFalse(passwordService.verifyPassword("WrongPass123", hash));
    }
}
