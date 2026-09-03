package com.betedge.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit-level (no Spring context, no DB) coverage for AuthService.loginWithGoogle - mocks
 * GoogleIdTokenVerification (real cryptographic verification is GoogleIdTokenVerifierAdapterTest's
 * own job, see that class) so these tests focus purely on OUR logic: what happens once a token has
 * (or hasn't) already been verified. JwtService is a real instance (its constructor needs no
 * Spring context) rather than mocked, so issueTokens' real JWT generation is exercised too, not
 * just stubbed through.
 */
class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtService jwtService = new JwtService("test-only-secret-at-least-32-bytes-long!!", 3_600_000L);
    private final GoogleIdTokenVerification googleIdTokenVerification = mock(GoogleIdTokenVerification.class);

    private AuthService newAuthService() {
        AuthService service = new AuthService(
                userRepository, refreshTokenRepository, passwordEncoder, authenticationManager, jwtService,
                googleIdTokenVerification);
        // @Value fields aren't populated outside a real Spring context - set directly, same
        // pattern as any other field-injected constant a plain unit test needs.
        ReflectionTestUtils.setField(service, "refreshExpirationMs", 604_800_000L);
        return service;
    }

    @Test
    void successfulGoogleLoginCreatesANewGoogleUserAndIssuesTokens() {
        when(googleIdTokenVerification.verify("valid-token"))
                .thenReturn(Optional.of(new GoogleIdentity("newuser@example.com", true)));
        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        TokenPair result = newAuthService().loginWithGoogle("valid-token");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User created = captor.getValue();
        assertThat(created.getEmail()).isEqualTo("newuser@example.com");
        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(created.getPasswordHash()).isNull();
        assertThat(created.getRole()).isEqualTo(Role.USER);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.rawRefreshToken()).isNotBlank();
        assertThat(result.user().getEmail()).isEqualTo("newuser@example.com");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void returningGoogleUserIsReusedNeverDuplicated() {
        User existing = new User();
        existing.setId(7L);
        existing.setEmail("returning@example.com");
        existing.setAuthProvider(AuthProvider.GOOGLE);
        existing.setRole(Role.USER);

        when(googleIdTokenVerification.verify("valid-token"))
                .thenReturn(Optional.of(new GoogleIdentity("returning@example.com", true)));
        when(userRepository.findByEmail("returning@example.com")).thenReturn(Optional.of(existing));

        TokenPair result = newAuthService().loginWithGoogle("valid-token");

        verify(userRepository, never()).save(any(User.class));
        assertThat(result.user().getId()).isEqualTo(7L);
    }

    @Test
    void rejectsWhenGoogleReportsTheEmailAsNotVerified() {
        when(googleIdTokenVerification.verify("token"))
                .thenReturn(Optional.of(new GoogleIdentity("unverified@example.com", false)));

        assertThatThrownBy(() -> newAuthService().loginWithGoogle("token"))
                .isInstanceOf(InvalidGoogleTokenException.class);
        verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsWhenTokenVerificationItselfFails() {
        // Represents ANY of: invalid signature, wrong audience, expired token - see
        // GoogleIdTokenVerification's own Javadoc for why those collapse into one empty() result
        // by design. From AuthService's point of view there is exactly one behavior for all three
        // real-world causes, not three identical tests wearing different names.
        when(googleIdTokenVerification.verify("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newAuthService().loginWithGoogle("bad-token"))
                .isInstanceOf(InvalidGoogleTokenException.class);
        verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsWithoutMergingWhenTheEmailAlreadyBelongsToAPasswordAccount() {
        // In practice today this can only be demo@betedge.com or admin@betedge.com - the password
        // register flow that could create more PASSWORD accounts no longer exists.
        User passwordAccount = new User();
        passwordAccount.setId(2L);
        passwordAccount.setEmail("admin@betedge.com");
        passwordAccount.setAuthProvider(AuthProvider.PASSWORD);
        passwordAccount.setPasswordHash("$2a$10$somehash");
        passwordAccount.setRole(Role.ADMIN);

        when(googleIdTokenVerification.verify("token"))
                .thenReturn(Optional.of(new GoogleIdentity("admin@betedge.com", true)));
        when(userRepository.findByEmail("admin@betedge.com")).thenReturn(Optional.of(passwordAccount));

        assertThatThrownBy(() -> newAuthService().loginWithGoogle("token"))
                .isInstanceOf(GoogleAccountConflictException.class);
        verify(userRepository, never()).save(any(User.class));
    }
}
