package com.betedge.auth;

import java.util.Optional;

/**
 * Verifies a raw Google ID token string and extracts the two facts AuthService needs - never the
 * email/name a caller merely CLAIMS in a request body, only what a cryptographically verified
 * token actually says. Empty means verification failed for ANY reason (bad signature, wrong
 * audience, expired - see the real implementation's own Javadoc for exactly what its underlying
 * library checks) - AuthService.loginWithGoogle treats every failure reason identically as a 401,
 * on purpose: never leak to an unauthenticated caller which specific check failed.
 */
public interface GoogleIdTokenVerification {

    Optional<GoogleIdentity> verify(String idTokenString);
}
