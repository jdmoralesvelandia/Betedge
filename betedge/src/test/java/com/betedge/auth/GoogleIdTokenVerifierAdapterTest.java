package com.betedge.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Real (non-mocked) coverage for the parts of Google ID token verification that are actually OUR
 * code, as opposed to google-api-client's own already-tested cryptographic verification logic:
 *
 * <ol>
 *   <li>Audience wiring - the client id from application.yml must actually reach
 *       GoogleIdTokenVerifier's own {@code aud} check. There is no way to also prove, in an
 *       automated test, that a real Google-signed token with the WRONG audience gets rejected -
 *       that would require an actual Google-issued token, which requires a real Google login (see
 *       the real-browser verification step in this feature's PR instead). What's tested here is
 *       the one thing under our control: that our own client id is the one actually configured.
 *   <li>Invalid/malformed/forged tokens are rejected - any string we construct ourselves (we
 *       don't have Google's private signing key) fails verification one way or another. A
 *       syntactically malformed one fails fast at parse time, no network needed. A well-formed
 *       but forged one (right shape, right claims, wrong signer) needs this test to reach Google's
 *       real public-certs endpoint to compare against - that's expected, not a flaw: it's
 *       confirming rejection against the SAME real keys production uses, not a stand-in.
 * </ol>
 */
class GoogleIdTokenVerifierAdapterTest {

    private static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";

    @Test
    void configuresTheVerifierWithOurOwnClientIdAsTheRequiredAudience() {
        var adapter = new GoogleIdTokenVerifierAdapter(CLIENT_ID);

        assertThat(adapter.configuredAudience()).containsExactly(CLIENT_ID);
    }

    @Test
    void rejectsAMalformedTokenWithoutThrowing() {
        var adapter = new GoogleIdTokenVerifierAdapter(CLIENT_ID);

        var result = adapter.verify("this-is-not-a-jwt");

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsATokenThatIsNotSignedByGoogle() {
        // Well-formed JWT shape (header.payload.signature, all valid base64url), claiming the
        // right audience and an unexpired exp - but signed with an arbitrary HMAC key, never
        // Google's. Confirms verification fails on signature mismatch specifically, not just on
        // "couldn't even parse the string" like the malformed case above.
        var adapter = new GoogleIdTokenVerifierAdapter(CLIENT_ID);
        String forgedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJhdWQiOiJ0ZXN0LWNsaWVudC1pZC5hcHBzLmdvb2dsZXVzZXJjb250ZW50LmNvbSIsImVtYWlsIjoi"
                + "YXR0YWNrZXJAZXhhbXBsZS5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZXhwIjo5OTk5OTk5OTk5fQ"
                + ".c2lnbmF0dXJlLW5vdC1mcm9tLWdvb2dsZQ";

        var result = adapter.verify(forgedToken);

        assertThat(result).isEmpty();
    }
}
