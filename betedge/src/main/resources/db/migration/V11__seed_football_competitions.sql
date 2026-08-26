INSERT INTO sport (name)
VALUES ('Fútbol')
ON CONFLICT (name) DO NOTHING;

-- oddspapi tournamentId confirmed against OddsPapi GET /v4/tournaments?sportId=10.
-- theoddsapi is the sport "key" from The Odds API GET /v4/sports (not yet integrated - see
-- MatchReconciliationService / Paso B-C) - Champions League has no theoddsapi entry because it
-- isn't covered by that source, so the key is simply absent for that row.
INSERT INTO competition (sport_id, name, external_keys)
SELECT s.id, c.name, c.external_keys::jsonb
FROM sport s
CROSS JOIN (VALUES
    ('Champions League', '{"oddspapi": "7"}'),
    ('La Liga', '{"oddspapi": "8", "theoddsapi": "soccer_spain_la_liga"}'),
    ('Premier League', '{"oddspapi": "17", "theoddsapi": "soccer_epl"}'),
    ('Serie A', '{"oddspapi": "23", "theoddsapi": "soccer_italy_serie_a"}'),
    ('Ligue 1', '{"oddspapi": "34", "theoddsapi": "soccer_france_ligue_one"}'),
    ('Bundesliga', '{"oddspapi": "35", "theoddsapi": "soccer_germany_bundesliga"}'),
    ('Brasileirão', '{"oddspapi": "325", "theoddsapi": "soccer_brazil_campeonato"}')
) AS c(name, external_keys)
WHERE s.name = 'Fútbol'
ON CONFLICT ((external_keys ->> 'oddspapi')) DO NOTHING;
