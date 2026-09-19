-- Edge Relative
-- Flyway V012: purge M1 history persisted under the incorrect Groww clock
--
-- Groww returns zone-less timestamps as exchange-local (IST) wall time. The adapter previously
-- interpreted them as UTC (+05:30 error); older rows also carried a JVM-default-zone bind error
-- (+02:00), so the stored history is wrong by mixed offsets and cannot be repaired in place.
-- All persisted M1 candles, coverage and ingestion runs are removed so the operator can re-download
-- cleanly with the corrected adapter. Higher timeframes are derived, so nothing else is affected.

DELETE FROM market.candle
WHERE timeframe_id IN (SELECT timeframe_id FROM reference.timeframe WHERE code = 'M1');

DELETE FROM market.candle_coverage
WHERE timeframe_id IN (SELECT timeframe_id FROM reference.timeframe WHERE code = 'M1');

DELETE FROM market.ingestion_run
WHERE timeframe_id IN (SELECT timeframe_id FROM reference.timeframe WHERE code = 'M1');
