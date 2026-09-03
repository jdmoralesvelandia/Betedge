package com.betedge.auth;

import jakarta.validation.constraints.NotBlank;

/** The raw Google ID token JWT - never a claimed email/name, only the token itself gets verified. */
public record GoogleAuthRequest(@NotBlank String idToken) {
}
