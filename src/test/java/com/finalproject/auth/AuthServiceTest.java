package com.finalproject.auth;

import com.finalproject.db.DatabaseManager;
import com.finalproject.model.Role;
import com.finalproject.model.User;
import com.finalproject.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private AuthService authService;
    private UserRepository userRepository;

    @BeforeEach
    void setup() throws IOException {
        Path tempDb = Files.createTempFile("workforce-auth-test-", ".db");
        tempDb.toFile().deleteOnExit();

        DatabaseManager databaseManager = new DatabaseManager("jdbc:sqlite:" + tempDb);
        databaseManager.initializeSchema();

        userRepository = new UserRepository(databaseManager);
        PasswordService passwordService = new PasswordService();
        authService = new AuthService(userRepository, passwordService);
    }

    @Test
    void registerAndLoginManager() {
        boolean created = authService.register("alice", "MyPass123", Role.MANAGER);
        assertTrue(created);

        assertTrue(authService.login("alice", "MyPass123").isPresent());
        assertTrue(authService.login("alice", "BadPassword").isEmpty());
    }

    @Test
    void duplicateUsernameRejected() {
        assertTrue(authService.register("bob", "Pass12345", Role.WORKER));
        assertFalse(authService.register("bob", "Pass12345", Role.MANAGER));
    }

    @Test
    void shortPasswordRejected() {
        // Passwords under 8 characters must be rejected
        assertFalse(authService.register("carol", "abc", Role.WORKER));
        assertFalse(authService.register("carol", "1234567", Role.WORKER));
        assertTrue(authService.register("carol", "12345678", Role.WORKER));
    }

    @Test
    void blankUsernameRejected() {
        assertFalse(authService.register("", "ValidPass1", Role.WORKER));
        assertFalse(authService.register("   ", "ValidPass1", Role.WORKER));
    }

    @Test
    void loginWithUnknownUserReturnsEmpty() {
        assertTrue(authService.login("nobody", "somepassword").isEmpty());
    }

    @Test
    void deleteUserSucceeds() {
        assertTrue(authService.register("dave", "DeleteMe99", Role.WORKER));
        assertTrue(authService.login("dave", "DeleteMe99").isPresent());

        assertTrue(authService.deleteUser("dave"));
        assertTrue(authService.login("dave", "DeleteMe99").isEmpty());
    }

    @Test
    void deleteNonExistentUserReturnsFalse() {
        assertFalse(authService.deleteUser("ghost"));
    }

    @Test
    void listUsersReturnsAllRegistered() {
        authService.register("user1", "Password1!", Role.MANAGER);
        authService.register("user2", "Password2!", Role.WORKER);

        List<User> users = authService.listUsers();
        long count = users.stream().filter(u -> u.username().equals("user1") || u.username().equals("user2")).count();
        assertEquals(2, count);
    }

    @Test
    void tokenValidationSucceeds() {
        assertTrue(authService.register("tokenuser", "Secure123!", Role.WORKER));

        // Provision token directly via userRepository
        boolean tokenSet = userRepository.createTokenForUser("tokenuser", "my-secret-token");
        assertTrue(tokenSet);

        var found = authService.validateToken("my-secret-token");
        assertTrue(found.isPresent());
        assertEquals("tokenuser", found.get().username());
    }

    @Test
    void invalidTokenReturnsEmpty() {
        assertTrue(authService.validateToken("nonexistent-token").isEmpty());
        assertTrue(authService.validateToken("").isEmpty());
        assertTrue(authService.validateToken(null).isEmpty());
    }

    @Test
    void setTokenViaAuthService() {
        assertTrue(authService.register("tokenuser2", "Secure456!", Role.WORKER));
        assertTrue(authService.setToken("tokenuser2", "auth-svc-token"));

        var found = authService.validateToken("auth-svc-token");
        assertTrue(found.isPresent());
        assertEquals("tokenuser2", found.get().username());
    }
}
