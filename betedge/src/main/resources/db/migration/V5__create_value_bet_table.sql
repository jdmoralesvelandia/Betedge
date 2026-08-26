CREATE TABLE value_bet (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    match_id                   BIGINT NOT NULL REFERENCES match (id),
    selection                  VARCHAR(255) NOT NULL,
    implied_probability        NUMERIC(10, 4) NOT NULL,
    estimated_true_probability NUMERIC(10, 4) NOT NULL,
    edge_percentage            NUMERIC(10, 4) NOT NULL,
    bookmaker_probabilities    JSONB,
    detected_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);
