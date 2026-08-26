CREATE TABLE surebet (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    match_id                    BIGINT NOT NULL REFERENCES match (id),
    legs                        JSONB NOT NULL,
    total_implied_probability   NUMERIC(10, 4) NOT NULL,
    profit_percentage           NUMERIC(10, 4) NOT NULL,
    detected_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);
