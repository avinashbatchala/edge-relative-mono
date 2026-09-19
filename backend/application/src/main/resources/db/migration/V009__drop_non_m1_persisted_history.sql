-- Edge Relative
-- Flyway V009: remove broker-native non-M1 history persisted before canonical M1 aggregation
--
-- DD-05 §94/§97: M1 is the canonical persisted base and higher timeframes are derived from it.
-- Rows previously fetched directly from Groww for other timeframes are no longer authoritative.
-- They are deleted so coverage, runs and candle counts reflect only the canonical M1 base.
-- The timeframe registry in reference.timeframe is retained (it drives the aggregator).

DELETE FROM market.candle
WHERE timeframe_id IN (SELECT timeframe_id FROM reference.timeframe WHERE code <> 'M1');

DELETE FROM market.candle_coverage
WHERE timeframe_id IN (SELECT timeframe_id FROM reference.timeframe WHERE code <> 'M1');

DELETE FROM market.backfill_run
WHERE timeframe_id IN (SELECT timeframe_id FROM reference.timeframe WHERE code <> 'M1');
