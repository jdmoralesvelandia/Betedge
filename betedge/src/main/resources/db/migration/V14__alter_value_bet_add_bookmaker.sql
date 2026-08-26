-- value_bet never has any rows before this point in dev (see project history), so this can
-- be added NOT NULL directly - no backfill needed.
ALTER TABLE value_bet
    ADD COLUMN bookmaker_id BIGINT NOT NULL REFERENCES bookmaker (id);
