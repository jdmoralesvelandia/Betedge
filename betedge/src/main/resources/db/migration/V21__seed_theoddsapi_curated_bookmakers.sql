-- Adds Bookmaker rows for the 17 of the 22 curated theoddsapi-ingestion.bookmakers (see
-- application.yml) that don't already have one. 5 already existed from the old orphaned
-- full-catalog sync (pinnacle, betsson, coolbet, marathonbet, williamhill - see
-- ReferenceDataSyncService's class comment) and are left untouched here.
-- api_source='theoddsapi' (not 'oddspapi' like V17/V20) - these are genuinely sourced from The
-- Odds API's own bookmaker catalog, confirmed against real events on 2026-08-26.
-- matchbook (an exchange, deliberately excluded from the curated list) already has a row from
-- the same orphaned sync - not touched, not added again, per the "don't delete FK-referenced
-- rows" rule already established for Competition (see V19).
--
-- bookmaker.name is ALSO unique (not just external_key, confirmed the hard way - this migration
-- failed once against real Postgres with "duplicate key value violates unique constraint
-- bookmaker_name_key" before this fix). 7 of these 17 are the same real-world brand as an
-- existing orphaned OddsPapi row, just catalogued under a different external_key scheme
-- (OddsPapi uses dots - unibet.fr, winamax.de, tipico, 1xbet; The Odds API uses underscores -
-- unibet_fr, winamax_de, tipico_de, onexbet). Confirmed by name collision, not guessed: onexbet,
-- tipico_de, unibet_fr/nl/se, winamax_de/fr all got a "(The Odds API)" suffix on the name to
-- disambiguate from their OddsPapi-catalogued sibling row while external_key stays the real,
-- functionally-unique identifier either way.
INSERT INTO bookmaker (name, api_source, external_key)
VALUES
    ('Betclic FR', 'theoddsapi', 'betclic_fr'),
    ('BetOnline.ag', 'theoddsapi', 'betonlineag'),
    ('Codere IT', 'theoddsapi', 'codere_it'),
    ('Everygame', 'theoddsapi', 'everygame'),
    ('GTbets', 'theoddsapi', 'gtbets'),
    ('LeoVegas SE', 'theoddsapi', 'leovegas_se'),
    ('MyBookie.ag', 'theoddsapi', 'mybookieag'),
    ('1xBet (The Odds API)', 'theoddsapi', 'onexbet'),
    ('PMU FR', 'theoddsapi', 'pmu_fr'),
    ('888sport', 'theoddsapi', 'sport888'),
    ('Suprabets', 'theoddsapi', 'suprabets'),
    ('Tipico DE (The Odds API)', 'theoddsapi', 'tipico_de'),
    ('Unibet FR (The Odds API)', 'theoddsapi', 'unibet_fr'),
    ('Unibet NL (The Odds API)', 'theoddsapi', 'unibet_nl'),
    ('Unibet SE (The Odds API)', 'theoddsapi', 'unibet_se'),
    ('Winamax DE (The Odds API)', 'theoddsapi', 'winamax_de'),
    ('Winamax FR (The Odds API)', 'theoddsapi', 'winamax_fr')
ON CONFLICT (external_key) DO NOTHING;
