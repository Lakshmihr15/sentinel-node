package com.finalproject.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public class PasswordService {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final String FORMAT_VERSION = "v1";

    private final String pepper;
    private final SecureRandom secureRandom;

    public PasswordService() {
        this.pepper = System.getenv().getOrDefault("APP_PEPPER", "dev-only-pepper-change-me");
        this.secureRandom = new SecureRandom();
    }

    public String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);

        byte[] derived = deriveKey(passwordWithPepper(password), salt, ITERATIONS, KEY_LENGTH_BITS);

        return String.format(
            "%s$%d$%s$%s",
            FORMAT_VERSION,
            ITERATIONS,
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(derived)
        );
    }

    public boolean verifyPassword(String candidatePassword, String storedHash) {
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !FORMAT_VERSION.equals(parts[0])) {
            return false;
        }

        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = deriveKey(passwordWithPepper(candidatePassword), salt, iterations, expected.length * 8);

        return constantTimeEquals(expected, actual);
    }

    private String passwordWithPepper(String password) {
        return password + pepper;
    }

    private byte[] deriveKey(String input, byte[] salt, int iterations, int keyLengthBits) {
        PBEKeySpec spec = new PBEKeySpec(input.toCharArray(), salt, iterations, keyLengthBits);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Password hashing failed", e);
        } finally {
            spec.clearPassword();
        }
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
