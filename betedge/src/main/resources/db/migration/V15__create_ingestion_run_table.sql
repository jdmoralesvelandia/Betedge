CREATE TABLE ingestion_run (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    triggered_by            VARCHAR(255) NOT NULL,
    started_at              TIMESTAMPTZ NOT NULL,
    finished_at             TIMESTAMPTZ NOT NULL,
    total_events_received   INTEGER NOT NULL,
    total_new_matches       INTEGER NOT NULL,
    total_new_odds          INTEGER NOT NULL,
    value_bets_detected     INTEGER NOT NULL,
    surebets_detected       INTEGER NOT NULL,
    competition_breakdown   JSONB NOT NULL
);
