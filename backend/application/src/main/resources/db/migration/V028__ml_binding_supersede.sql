-- Edge Relative
-- Flyway V028: allow superseding an ML model binding on its own effective date.
--
-- When a new binding for an instrument takes effect on the same day as the current open binding, the
-- prior row is closed by setting effective_to = effective_from, producing an empty [d, d) window.
-- Postgres excludes empty ranges from the overlap exclusion constraint, so the prior row is retired
-- for point-in-time resolution yet remains in the table for history. Relax the period check to permit
-- effective_to = effective_from.
ALTER TABLE control.ml_model_binding DROP CONSTRAINT ck_ml_binding_period;
ALTER TABLE control.ml_model_binding
    ADD CONSTRAINT ck_ml_binding_period CHECK (effective_to IS NULL OR effective_to >= effective_from);
