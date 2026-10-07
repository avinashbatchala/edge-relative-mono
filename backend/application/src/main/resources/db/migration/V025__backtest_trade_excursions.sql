-- Record maximum favourable / adverse excursion per trade (in R), independent of the chosen exit.
-- This is the label that lets research judge whether a target of a given multiple is reachable and
-- whether the loss is an exit-model artifact or a weak signal.
ALTER TABLE research.backtest_trade
    ADD COLUMN mfe_r NUMERIC(18,6),
    ADD COLUMN mae_r NUMERIC(18,6);

COMMENT ON COLUMN research.backtest_trade.mfe_r IS
    'Max favourable excursion during the holding period, as a multiple of initial planned risk.';
COMMENT ON COLUMN research.backtest_trade.mae_r IS
    'Max adverse excursion during the holding period, as a multiple of initial planned risk.';
