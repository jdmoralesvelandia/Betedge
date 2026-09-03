-- Google Sign-In (2026-09-02): password_hash stops being mandatory - a Google-only account never
-- has one, and never will (see AuthService.loginWithGoogle). auth_provider distinguishes the two
-- account kinds so /auth/google can refuse to silently take over a PASSWORD account that happens
-- to share the same verified email (see the conflict check there) instead of merging them.
--
-- DEFAULT 'PASSWORD' backfills the 2 existing rows (demo@betedge.com, admin@betedge.com - both
-- created before Google Sign-In existed) for free; dropped right after so nothing relies on it
-- implicitly going forward - every new row (either path) sets auth_provider explicitly in code.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users ADD COLUMN auth_provider VARCHAR(255) NOT NULL DEFAULT 'PASSWORD';
ALTER TABLE users ALTER COLUMN auth_provider DROP DEFAULT;
