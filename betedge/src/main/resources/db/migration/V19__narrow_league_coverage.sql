-- Narrows league coverage: OddsPapi drops Bundesliga and Brasileirão (keeps Champions League, La
-- Liga, Premier League, Serie A, Ligue 1); The Odds API drops Brasileirão (already didn't cover
-- Champions League - see V11__seed_football_competitions.sql). Competition rows are kept, not
-- deleted: existing Match rows (e.g. Cruzeiro EC MG vs Mirassol FC SP) still FK-reference them and
-- must stay queryable in history - only external_keys is cleared, so no scheduler picks them up
-- for new ingestion anymore.
UPDATE competition SET external_keys = external_keys - 'oddspapi'
WHERE name = 'Bundesliga';

UPDATE competition SET external_keys = '{}'::jsonb
WHERE name = 'Brasileirão';
