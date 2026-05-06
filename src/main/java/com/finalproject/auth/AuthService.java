package com.finalproject.auth;

import com.finalproject.model.Role;
import com.finalproject.model.User;
import com.finalproject.repository.UserRepository;

import java.util.Optional;

public class AuthService {
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

    public java.util.List<User> listUsers() {
        return userRepository.listUsers();
    }

    public boolean deleteUser(String username) {
        return userRepository.deleteByUsername(username);
    }

    public java.util.Optional<User> validateToken(String token) {
        if (token == null || token.isBlank()) return java.util.Optional.empty();
        return userRepository.findByToken(token);
    }

    public boolean setToken(String username, String token) {
        return userRepository.createTokenForUser(username, token);
    }

    private boolean isValid(String username, String password) {
        return username != null
            && password != null
            && !username.isBlank()
            && password.length() >= 8;
    }
}
