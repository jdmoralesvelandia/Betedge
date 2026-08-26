package com.betedge.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieFactory {

    static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/auth";

    private final boolean secure;
    private final long refreshExpirationMs;

    public RefreshTokenCookieFactory(
            @Value("${jwt.refresh-cookie-secure}") boolean secure,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.secure = secure;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public ResponseCookie build(String rawRefreshToken) {
        return baseCookie(rawRefreshToken)
                .maxAge(Duration.ofMillis(refreshExpirationMs))
                .build();
    }

    public ResponseCookie clear() {
        return baseCookie("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}
