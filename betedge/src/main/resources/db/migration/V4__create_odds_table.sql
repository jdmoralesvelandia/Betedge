CREATE TABLE odds (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    match_id     BIGINT NOT NULL REFERENCES match (id),
    bookmaker_id BIGINT NOT NULL REFERENCES bookmaker (id),
    market_type  VARCHAR(255) NOT NULL,
    selection    VARCHAR(255) NOT NULL,
    odd_value    NUMERIC(10, 4) NOT NULL,
    timestamp    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_odds_match_bookmaker_timestamp ON odds (match_id, bookmaker_id, timestamp);
