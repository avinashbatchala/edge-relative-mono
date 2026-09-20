-- Edge Relative
-- Flyway V020: remove non-session prints from the canonical M1 base
--
-- The M1 base carries candle_definition_version 'er-m1-base-v1', which is defined as the canonical
-- continuous NSE cash session [09:15, 15:30) IST on trading days (DD-05 §§93/99/115). The backfill
-- previously persisted whatever the vendor returned, including pre-open/auction minutes, post-close
-- minutes and non-trading-day prints, all stamped is_complete=TRUE, quality_state='GOOD'. Those rows
-- disagree with the definition, inflate coverage.candleCount and are invisible to the read path,
-- which filters them out. They are removed so the persisted base is a faithful function of the
-- definition. Ingestion is fixed at the same time so they are not recreated.
--
-- Pre-open/auction and special-session prints are not deleted as data policy: they belong to a
-- separate dataset (DD-05 §115) and special sessions need explicit calendar support (DD-05 §116).
-- Until then they are not part of the canonical M1 base and can be re-downloaded once those exist.
-- The configured holiday list is not available in SQL, but holidays carry no trades.

DELETE FROM market.candle
WHERE candle_definition_version = 'er-m1-base-v1'
  AND (
        EXTRACT(ISODOW FROM open_time AT TIME ZONE 'Asia/Kolkata') IN (6, 7)
     OR (open_time AT TIME ZONE 'Asia/Kolkata')::time < TIME '09:15'
     OR (open_time AT TIME ZONE 'Asia/Kolkata')::time >= TIME '15:30'
      );
