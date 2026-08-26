package com.betedge.auth;

public record AuthResponse(
        String accessToken,
        String tokenType,
        String email,
        Role role) {

    static AuthResponse from(TokenPair tokenPair) {
        return new AuthResponse(
                tokenPair.accessToken(), "Bearer", tokenPair.user().getEmail(), tokenPair.user().getRole());
    }
}
