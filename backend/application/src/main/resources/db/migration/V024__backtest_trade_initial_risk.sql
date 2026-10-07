-- Persist the initial planned risk per unit on each backtest trade so realized R and the forensic
-- timeline survive a reload; previously the value was computed in-memory and lost on read.
ALTER TABLE research.backtest_trade
    ADD COLUMN initial_risk_per_unit NUMERIC(20,8);

COMMENT ON COLUMN research.backtest_trade.initial_risk_per_unit IS
    'Initial planned risk per unit at entry (entry to protective stop); used for realized R.';
