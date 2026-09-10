-- OddsPapi quota guard (2026-09-09, see IngestionService.runIngestion): adds status and
-- remaining_quota to ingestion_run. Every existing row here predates this feature and, by
-- definition, was a fully completed run (nothing before this could have been skipped) - DEFAULT
-- 'COMPLETED' backfills them correctly, then gets dropped right after so no future row can rely
-- on it implicitly (same pattern as V23__add_auth_provider_and_nullable_password.sql's own
-- DEFAULT on auth_provider).
ALTER TABLE ingestion_run ADD COLUMN status VARCHAR(255) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE ingestion_run ALTER COLUMN status DROP DEFAULT;

-- No default and no backfill needed: remaining_quota was never tracked before this, so NULL is
-- the honest value for every existing row - and stays NULL forever for every TheOddsApi row too,
-- since that pipeline never calls OddsPapi's /account endpoint.
ALTER TABLE ingestion_run ADD COLUMN remaining_quota INTEGER;
