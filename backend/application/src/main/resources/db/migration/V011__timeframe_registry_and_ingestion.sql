-- Edge Relative
-- Flyway V011: timeframe registry, market data source, and ingestion naming
--
-- DD-05 §96: timeframes are registry/configuration data. The registry is seeded
-- here (M1, M3, M5, M15, M30, H1, H2, H4, D1, W1); higher timeframes are derived
-- from M1. Non-registry rows (M2/M10/MN1) are deactivated, never trusted.
-- market.candle_coverage stays; market.backfill_run is renamed to the clearer
-- market.ingestion_run. Both now reference reference.market_data_source.

INSERT INTO reference.market_data_source (code, name, source_type)
VALUES ('GROWW', 'Groww', 'BROKER')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, source_type = EXCLUDED.source_type;

UPDATE reference.market_data_source mds
SET broker_id = b.broker_id
FROM reference.broker b
WHERE mds.code = 'GROWW' AND b.code = 'GROWW' AND mds.broker_id IS NULL;

INSERT INTO reference.timeframe (code, duration_seconds, calendar_based) VALUES
    ('M1',  60,    FALSE),
    ('M3',  180,   FALSE),
    ('M5',  300,   FALSE),
    ('M15', 900,   FALSE),
    ('M30', 1800,  FALSE),
    ('H1',  3600,  FALSE),
    ('H2',  7200,  FALSE),
    ('H4',  14400, FALSE),
    ('D1',  NULL,  TRUE),
    ('W1',  NULL,  TRUE)
ON CONFLICT (code) DO UPDATE
    SET duration_seconds = EXCLUDED.duration_seconds,
        calendar_based = EXCLUDED.calendar_based,
        active = TRUE;

UPDATE reference.timeframe SET active = FALSE WHERE code IN ('M2', 'M10', 'MN1');

ALTER TABLE market.backfill_run RENAME TO ingestion_run;
ALTER TABLE market.ingestion_run RENAME COLUMN backfill_run_id TO ingestion_run_id;
ALTER INDEX market.ix_backfill_run_instrument RENAME TO ix_ingestion_run_instrument;
ALTER TABLE market.ingestion_run RENAME CONSTRAINT uq_backfill_run_key TO uq_ingestion_run_key;
ALTER TABLE market.ingestion_run RENAME CONSTRAINT ck_backfill_run_range TO ck_ingestion_run_range;
ALTER TABLE market.ingestion_run RENAME CONSTRAINT ck_backfill_run_counts TO ck_ingestion_run_counts;
ALTER TABLE market.ingestion_run RENAME CONSTRAINT ck_backfill_run_status TO ck_ingestion_run_status;

ALTER TABLE market.ingestion_run
    ADD COLUMN market_data_source_id BIGINT REFERENCES reference.market_data_source(market_data_source_id) ON DELETE RESTRICT;
UPDATE market.ingestion_run
SET market_data_source_id = (SELECT market_data_source_id FROM reference.market_data_source WHERE code = 'GROWW')
WHERE market_data_source_id IS NULL;
ALTER TABLE market.ingestion_run ALTER COLUMN market_data_source_id SET NOT NULL;

ALTER TABLE market.candle_coverage
    ADD COLUMN market_data_source_id BIGINT REFERENCES reference.market_data_source(market_data_source_id) ON DELETE RESTRICT;
UPDATE market.candle_coverage
SET market_data_source_id = (SELECT market_data_source_id FROM reference.market_data_source WHERE code = 'GROWW')
WHERE market_data_source_id IS NULL;
ALTER TABLE market.candle_coverage ALTER COLUMN market_data_source_id SET NOT NULL;
