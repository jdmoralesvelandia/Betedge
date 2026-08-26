-- Backfill existing rows as ODDSPAPI (the only source ever ingested so far), then drop the
-- default so every future insert must set it explicitly via the Odds entity.
ALTER TABLE odds ADD COLUMN data_source VARCHAR(255) NOT NULL DEFAULT 'ODDSPAPI';
ALTER TABLE odds ALTER COLUMN data_source DROP DEFAULT;
