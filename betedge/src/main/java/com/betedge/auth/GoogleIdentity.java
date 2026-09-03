package com.betedge.auth;

/**
 * The only two facts AuthService.loginWithGoogle needs out of a verified Google ID token - kept
 * separate from google-api-client's own Payload type so AuthService (and its tests) only ever
 * depend on GoogleIdTokenVerification's own contract, never that library's shape directly.
 */
record GoogleIdentity(String email, boolean emailVerified) {
}
