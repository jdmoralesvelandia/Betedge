-- Adds the 2 new odds-ingestion.bookmakers entries (see application.yml) so
-- BookmakerRepository.findByExternalKey resolves them instead of silently skipping every
-- betano/betplay line in bookmakerOdds (same silent-skip failure mode already seen with the
-- phantom market 10911 - see OddsPapiMarketDto). Both confirmed against the real OddsPapi
-- GET /bookmakers catalog: "betano" (bookmakerName "Betano", cloneOf: null - the generic/global
-- entry, not a regional variant like betano.bet.br/.cz/.pt, which are also non-cloned but were
-- not what was asked for) and "betplay" (bookmakerName "BetPlay", cloneOf: null - distinct from
-- "betplay.io", which IS a clone of roobet and must not be used).
-- bet365/unibet rows from V17 are left in place - existing Odds rows still FK-reference them.
INSERT INTO bookmaker (name, api_source, external_key)
VALUES
    ('Betano', 'oddspapi', 'betano'),
    ('BetPlay', 'oddspapi', 'betplay')
ON CONFLICT (external_key) DO NOTHING;
