package com.betedge.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public email+password register flow that used to live here (see git history, removed
 * 2026-09-02) is retired: Google Sign-In is now the only path for a genuinely new account.
 * {@link #login}/{@link #refresh}/{@link #logout}/{@link #bootstrapAdmin} are all unchanged - the
 * only account-creation path left is {@link #loginWithGoogle}'s own find-or-create.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final GoogleIdTokenVerification googleIdTokenVerification;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${admin.bootstrap-secret}")
    private String adminBootstrapSecret;

    @Transactional
    public TokenPair login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        return issueTokens(user);
    }

    /**
     * Verifies the raw Google ID token (signature, audience, expiration - all via
     * {@link GoogleIdTokenVerification}, backed by Google's own library) and requires
     * {@code email_verified} on top of that - either check failing throws
     * {@link InvalidGoogleTokenException} (401), never partially proceeding on an unverified or
     * unverifiable identity.
     *
     * <p>Find-or-create by the token's OWN verified email, never a claimed one:
     * <ul>
     *   <li>No existing user -&gt; created fresh with {@code authProvider=GOOGLE},
     *       {@code passwordHash=null}, {@code role=USER}.
     *   <li>Existing user with {@code authProvider=GOOGLE} -&gt; reused as-is (a returning
     *       Google user).
     *   <li>Existing user with {@code authProvider=PASSWORD} -&gt; refused with
     *       {@link GoogleAccountConflictException} (409), NEVER silently linked - an attacker
     *       controlling a Google account for someone else's already-registered email must never
     *       be able to ride into that existing account just by proving they own that email's
     *       Google identity. In practice today this can only ever be demo@betedge.com or
     *       admin@betedge.com, since the password register flow that could create more
     *       PASSWORD accounts no longer exists.
     * </ul>
     *
     * Ends by calling the SAME {@link #issueTokens} every other login path uses - a Google-issued
     * session is byte-for-byte indistinguishable downstream (JWT, refresh cookie, JwtAuthenticationFilter)
     * from a password-issued one.
     */
    @Transactional
    public TokenPair loginWithGoogle(String idToken) {
        GoogleIdentity identity = googleIdTokenVerification.verify(idToken)
                .filter(GoogleIdentity::emailVerified)
                .orElseThrow(InvalidGoogleTokenException::new);

        User user = userRepository.findByEmail(identity.email())
                .map(existing -> requireGoogleProvider(existing))
                .orElseGet(() -> createGoogleUser(identity.email()));

        return issueTokens(user);
    }

    private static User requireGoogleProvider(User existing) {
        if (existing.getAuthProvider() != AuthProvider.GOOGLE) {
            throw new GoogleAccountConflictException();
        }
        return existing;
    }

    private User createGoogleUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(null);
        user.setRole(Role.USER);
        user.setAuthProvider(AuthProvider.GOOGLE);
        return userRepository.save(user);
    }

    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        RefreshToken existing = findValidRefreshToken(rawRefreshToken);

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return issueTokens(existing.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public UserResponse bootstrapAdmin(BootstrapAdminRequest request, String providedSecret) {
        boolean secretMatches = MessageDigest.isEqual(
                adminBootstrapSecret.getBytes(StandardCharsets.UTF_8),
                (providedSecret == null ? "" : providedSecret).getBytes(StandardCharsets.UTF_8));
        boolean adminExists = userRepository.countByRole(Role.ADMIN) > 0;

        if (!secretMatches || adminExists) {
            throw new BootstrapNotAllowedException();
        }

        User admin = new User();
        admin.setEmail(request.email());
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        admin.setRole(Role.ADMIN);
        admin.setAuthProvider(AuthProvider.PASSWORD);

        return UserResponse.from(userRepository.save(admin));
    }

    private RefreshToken findValidRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken existing = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        return existing;
    }

    private TokenPair issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole());
        String rawRefreshToken = generateRawToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));
        refreshTokenRepository.save(refreshToken);

        return new TokenPair(accessToken, rawRefreshToken, user);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
