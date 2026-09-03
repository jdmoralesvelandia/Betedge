package com.betedge.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    /**
     * Google Sign-In - the only path left for a genuinely new account (the public
     * email+password register endpoint was retired 2026-09-02, see git history). Public, but
     * "public" only means anyone can call it, not that it trusts anything unverified: idToken is
     * the raw Google-issued JWT, cryptographically verified server-side before any User is ever
     * looked up or created - see AuthService.loginWithGoogle's own Javadoc.
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleAuthRequest request) {
        return withRefreshCookie(authService.loginWithGoogle(request.idToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                .build();
    }

    @PostMapping("/bootstrap-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse bootstrapAdmin(
            @Valid @RequestBody BootstrapAdminRequest request,
            @RequestHeader(value = "X-Bootstrap-Secret", required = false) String bootstrapSecret) {
        return authService.bootstrapAdmin(request, bootstrapSecret);
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(TokenPair tokenPair) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.build(tokenPair.rawRefreshToken()).toString())
                .body(AuthResponse.from(tokenPair));
    }
}
