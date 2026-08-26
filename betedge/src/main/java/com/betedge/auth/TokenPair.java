package com.betedge.auth;

/** Internal carrier only - the raw refresh token must never be serialized into a JSON response body. */
record TokenPair(String accessToken, String rawRefreshToken, User user) {
}
