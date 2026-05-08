package com.finalproject.auth;

import com.finalproject.model.Role;
import com.finalproject.model.User;
import com.finalproject.repository.UserRepository;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordService passwordService;

    public AuthService(UserRepository userRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    public boolean register(String username, String password, Role role) {
        if (!isValid(username, password)) {
            return false;
        }
        if (userRepository.findByUsername(username).isPresent()) {
            return false;
        }
        String passwordHash = passwordService.hashPassword(password);
        return userRepository.createUser(username, role, passwordHash);
    }

    public Optional<User> login(String username, String password) {
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        boolean ok = passwordService.verifyPassword(password, user.get().passwordHash());
        return ok ? user : Optional.empty();
    }

    public List<User> listUsers() {
        return userRepository.listUsers();
    }

    public boolean deleteUser(String username) {
        return userRepository.deleteByUsername(username);
    }

    public boolean revokeUser(String username) {
        return userRepository.revokeByUsername(username);
    }

    public boolean restoreUser(String username) {
        return userRepository.restoreByUsername(username);
    }

    public Optional<User> validateToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return userRepository.findByToken(token);
    }

    public boolean setToken(String username, String token) {
        return userRepository.createTokenForUser(username, token);
    }

    public Optional<String> tokenFor(String username) {
        return userRepository.findTokenForUser(username);
    }

    public String generateToken() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean isValid(String username, String password) {
        return username != null
            && password != null
            && !username.isBlank()
            && password.length() >= 8;
    }
}
