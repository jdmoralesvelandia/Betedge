ALTER TABLE match
    DROP COLUMN sport_id,
    ADD COLUMN competition_id BIGINT NOT NULL REFERENCES competition (id),
    ADD COLUMN external_id VARCHAR(255) NOT NULL UNIQUE;
