package com.betedge.auth;

/**
 * How a User's account was created/authenticated - PASSWORD for the (now-retired) email+password
 * register flow, GOOGLE for accounts created via /auth/google. Used by
 * AuthService.loginWithGoogle to refuse silently taking over an existing PASSWORD account that
 * happens to share the same verified Google email, instead of merging them - see its own Javadoc.
 */
public enum AuthProvider {
    PASSWORD,
    GOOGLE
}
