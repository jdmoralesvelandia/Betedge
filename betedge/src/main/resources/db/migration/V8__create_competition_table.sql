-- external_keys holds one entry per data provider, e.g. {"oddspapi": "17", "theoddsapi": "soccer_epl"}.
-- oddspapi is required (the only source in production so far); theoddsapi is present only for
-- competitions tracked through that complementary source.
CREATE TABLE competition (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sport_id      BIGINT NOT NULL REFERENCES sport (id),
    name          VARCHAR(255) NOT NULL,
    external_keys JSONB NOT NULL
);

CREATE UNIQUE INDEX idx_competition_oddspapi_key ON competition ((external_keys ->> 'oddspapi'));
