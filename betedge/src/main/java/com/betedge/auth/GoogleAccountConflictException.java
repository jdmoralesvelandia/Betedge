package com.betedge.auth;

/**
 * Thrown when a verified Google email matches an existing account that was NOT created via
 * Google (in practice today, only demo@betedge.com or admin@betedge.com could ever trigger this,
 * since the password register flow is retired) - see AuthService.loginWithGoogle's own Javadoc
 * for why this refuses instead of silently merging the two accounts.
 */
public class GoogleAccountConflictException extends RuntimeException {
}
