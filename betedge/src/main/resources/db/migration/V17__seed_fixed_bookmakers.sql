-- Bookmaker rows for the fixed, hand-picked odds-ingestion.bookmakers list (see application.yml).
-- Previously kept in sync by a daily call to GET /bookmakers (the full ~230-bookmaker catalog) -
-- removed (see ReferenceDataSyncService) because the ingestion bookmaker list is now fixed and
-- each entry already confirmed non-cloned by hand, so that daily full-catalog call was paying
-- real API budget for a check that no longer catches anything a fixed, pre-verified list doesn't
-- already guarantee. Name/slug match exactly what OddsPapi returned the last time the full
-- catalog was synced.
INSERT INTO bookmaker (name, api_source, external_key)
VALUES
    ('Pinnacle Sports', 'oddspapi', 'pinnacle'),
    ('bet365', 'oddspapi', 'bet365'),
    ('Unibet', 'oddspapi', 'unibet')
ON CONFLICT (external_key) DO NOTHING;
