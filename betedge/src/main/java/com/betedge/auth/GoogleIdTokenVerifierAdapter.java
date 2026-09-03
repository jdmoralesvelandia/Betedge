package com.betedge.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Real implementation, backed entirely by Google's own official google-api-client library -
 * {@link GoogleIdTokenVerifier#verify} does the actual cryptographic work (fetches Google's
 * current public keys, checks the token's signature against them, confirms it hasn't expired,
 * confirms {@code aud} matches OUR OWN client id below) - deliberately NOT reimplemented by hand
 * here. Returns {@code null} (never throws) for an invalid signature, wrong audience, or expired
 * token - see that method's own Javadoc.
 *
 * <p>{@code emailVerified} is checked separately here because {@code verify()} itself does NOT
 * check it - Google's own backend-auth documentation is explicit that {@code email_verified} is
 * an application-level decision, not part of token validity, so a caller must inspect the claim
 * itself rather than assume a structurally valid token implies a verified email.
 */
@Component
public class GoogleIdTokenVerifierAdapter implements GoogleIdTokenVerification {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifierAdapter.class);

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenVerifierAdapter(@Value("${google.oauth.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    @Override
    public Optional<GoogleIdentity> verify(String idTokenString) {
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | IOException e) {
            log.warn("Google ID token verification failed", e);
            return Optional.empty();
        } catch (RuntimeException e) {
            // Confirmed live (GoogleIdTokenVerifierAdapterTest): a malformed token string (not
            // proper JWT shape) makes GoogleIdToken.parse throw a plain IllegalArgumentException -
            // an unchecked exception the method signature doesn't declare, thrown from deep inside
            // google-api-client's own parser. idTokenString here is entirely attacker-controlled
            // (the raw body of a public POST /auth/google), so any malformed/garbage input must
            // still resolve to a clean 401, never an uncaught exception surfacing as a 500.
            log.warn("Google ID token was malformed", e);
            return Optional.empty();
        }
        if (idToken == null) {
            // Not thrown as an exception by the library for an invalid/expired/wrong-audience
            // token - verify() itself returns null in that case (see its own Javadoc).
            return Optional.empty();
        }
        GoogleIdToken.Payload payload = idToken.getPayload();
        return Optional.of(new GoogleIdentity(payload.getEmail(), Boolean.TRUE.equals(payload.getEmailVerified())));
    }

    /**
     * Test-only accessor - lets a test confirm the client id from application.yml actually reaches
     * the underlying verifier's {@code aud} check, without exposing the verifier itself (or a
     * setter) publicly. There's no supported way to make GoogleIdTokenVerifier itself reject a
     * real Google-signed token in a test (that would need a real token, which needs a real Google
     * login) - this is the part of "wrong audience gets rejected" that's actually OUR code and OUR
     * responsibility to get right; the rejection mechanics themselves are google-api-client's own,
     * already covered by its own test suite.
     */
    Collection<String> configuredAudience() {
        return verifier.getAudience();
    }
}
