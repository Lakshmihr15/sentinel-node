package com.finalproject.model;

public record User(long id, String username, Role role, String passwordHash) {
}
