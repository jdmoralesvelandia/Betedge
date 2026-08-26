package com.betedge.auth;

public record UserResponse(
        Long id,
        String email,
        Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
