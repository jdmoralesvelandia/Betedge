-- Fully removes Brasileirão from the system: the competition row itself, every Match that ever
-- belonged to it, and everything that FK-references those Match rows. V19 only stopped new
-- ingestion (cleared external_keys, kept the rows for FK integrity at the time) - this finishes
-- the job now that keeping the historical rows queryable is no longer wanted.
--
-- FKs confirmed via information_schema (not just grepping the code) before writing this:
--   - Only 3 tables FK-reference match: odds.match_id, surebet.match_id, value_bet.match_id.
--   - Only 1 table FK-references competition: match.competition_id. Nothing else does.
--   - No surebet_leg table exists - surebet.legs is a JSONB column, not a child table, so there's
--     nothing to delete there ahead of surebet itself.
--
-- Real counts confirmed against production data before writing this migration: 22 Match rows (all
-- FINISHED, none SCHEDULED/LIVE), 3048 odds, 12 value_bet, 0 surebet.
--
-- Deletes in dependency order (children before parents): value_bet, surebet, and odds (order among
-- these three doesn't matter to each other - none of them reference one another) before match,
-- then match before competition.
--
-- ingestion_run.competition_breakdown (JSONB) keeps its historical mentions of Brasileirão -
-- that's not an FK, just a point-in-time snapshot of past ingestion runs, and is left alone as an
-- honest historical record of what actually happened, same reasoning as V19's own comment.
DELETE FROM value_bet
WHERE match_id IN (
    SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE name = 'Brasileirão')
);

DELETE FROM surebet
WHERE match_id IN (
    SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE name = 'Brasileirão')
);

DELETE FROM odds
WHERE match_id IN (
    SELECT id FROM match WHERE competition_id = (SELECT id FROM competition WHERE name = 'Brasileirão')
);

DELETE FROM match
WHERE competition_id = (SELECT id FROM competition WHERE name = 'Brasileirão');

DELETE FROM competition
WHERE name = 'Brasileirão';
