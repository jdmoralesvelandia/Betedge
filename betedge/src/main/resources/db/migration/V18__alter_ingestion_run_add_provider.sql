-- Backfill existing rows as ODDSPAPI (the only source ingestion_run has ever recorded so far),
-- then drop the default so every future insert must set it explicitly - same pattern as
-- V16__alter_odds_add_data_source.sql.
ALTER TABLE ingestion_run ADD COLUMN provider VARCHAR(255) NOT NULL DEFAULT 'ODDSPAPI';
ALTER TABLE ingestion_run ALTER COLUMN provider DROP DEFAULT;
