CREATE TABLE match (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sport_id   BIGINT NOT NULL REFERENCES sport (id),
    home_team  VARCHAR(255) NOT NULL,
    away_team  VARCHAR(255) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    status     VARCHAR(255) NOT NULL
);
