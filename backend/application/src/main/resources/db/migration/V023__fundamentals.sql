-- Edge Relative
-- Flyway V023: fundamental analysis store (DD-06, ADR-005).
--
-- Advisory, periodically-reported company fundamentals for NSE/BSE instruments. Kept in PostgreSQL
-- for the current single-operator scope (the same bounded exception as ADR-001/ADR-002). Values are
-- append-only and revisioned: a restatement adds a row, it never overwrites history. A value is only
-- visible from its filing/observation timestamp, so point-in-time reads cannot see the future.

CREATE SCHEMA IF NOT EXISTS fundamental;

CREATE TABLE fundamental.data_source
(
    data_source_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           TEXT NOT NULL,
    provider       TEXT NOT NULL,
    license        TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_fundamental_data_source_code UNIQUE (code),
    CONSTRAINT ck_fundamental_data_source_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_fundamental_data_source_provider_nonblank CHECK (btrim(provider) <> '')
);

CREATE TABLE fundamental.company_profile
(
    company_profile_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_id      BIGINT NOT NULL REFERENCES reference.instrument (instrument_id) ON DELETE RESTRICT,
    sector_id          BIGINT REFERENCES reference.sector (sector_id) ON DELETE RESTRICT,
    currency_code      TEXT NOT NULL DEFAULT 'INR',
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_fundamental_company_profile_instrument UNIQUE (instrument_id),
    CONSTRAINT ck_fundamental_company_profile_currency CHECK (length(currency_code) = 3)
);

CREATE TABLE fundamental.filing
(
    filing_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_id      BIGINT NOT NULL REFERENCES reference.instrument (instrument_id) ON DELETE RESTRICT,
    data_source_id     BIGINT NOT NULL REFERENCES fundamental.data_source (data_source_id) ON DELETE RESTRICT,
    filed_at           TIMESTAMPTZ NOT NULL,
    observed_at        TIMESTAMPTZ NOT NULL,
    document_reference TEXT,
    revision           TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_fundamental_filing UNIQUE (instrument_id, data_source_id, filed_at, revision),
    CONSTRAINT ck_fundamental_filing_revision_nonblank CHECK (btrim(revision) <> ''),
    CONSTRAINT ck_fundamental_filing_observed CHECK (observed_at >= filed_at)
);

CREATE INDEX ix_fundamental_filing_pit
    ON fundamental.filing (instrument_id, filed_at DESC);

CREATE TABLE fundamental.reporting_period
(
    reporting_period_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    filing_id           BIGINT NOT NULL REFERENCES fundamental.filing (filing_id) ON DELETE RESTRICT,
    fiscal_year         TEXT NOT NULL,
    fiscal_quarter      INTEGER NOT NULL DEFAULT 0,
    period_type         TEXT NOT NULL,
    reporting_basis     TEXT NOT NULL,
    period_end          DATE NOT NULL,
    CONSTRAINT uq_fundamental_reporting_period UNIQUE (
        filing_id, fiscal_year, fiscal_quarter, period_type, reporting_basis),
    CONSTRAINT ck_fundamental_period_fiscal_year_nonblank CHECK (btrim(fiscal_year) <> ''),
    CONSTRAINT ck_fundamental_period_type CHECK (period_type IN ('ANNUAL', 'QUARTERLY')),
    CONSTRAINT ck_fundamental_period_basis CHECK (reporting_basis IN ('CONSOLIDATED', 'STANDALONE')),
    CONSTRAINT ck_fundamental_period_quarter CHECK (
        (period_type = 'QUARTERLY' AND fiscal_quarter BETWEEN 1 AND 4)
            OR (period_type = 'ANNUAL' AND fiscal_quarter = 0))
);

CREATE INDEX ix_fundamental_period_end
    ON fundamental.reporting_period (period_end DESC);

CREATE TABLE fundamental.statement_line
(
    statement_line_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporting_period_id BIGINT NOT NULL REFERENCES fundamental.reporting_period (reporting_period_id) ON DELETE RESTRICT,
    line_code           TEXT NOT NULL,
    label               TEXT,
    value               NUMERIC(30, 6) NOT NULL,
    unit                TEXT NOT NULL,
    scale               TEXT NOT NULL,
    ordinal             INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uq_fundamental_statement_line UNIQUE (reporting_period_id, line_code),
    CONSTRAINT ck_fundamental_statement_line_code_nonblank CHECK (btrim(line_code) <> ''),
    CONSTRAINT ck_fundamental_statement_line_unit_nonblank CHECK (btrim(unit) <> ''),
    CONSTRAINT ck_fundamental_statement_line_scale_nonblank CHECK (btrim(scale) <> ''),
    CONSTRAINT ck_fundamental_statement_line_ordinal CHECK (ordinal > 0)
);

CREATE TABLE fundamental.metric_value
(
    metric_value_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporting_period_id BIGINT NOT NULL REFERENCES fundamental.reporting_period (reporting_period_id) ON DELETE RESTRICT,
    metric_code         TEXT NOT NULL,
    value               NUMERIC(30, 8) NOT NULL,
    unit                TEXT NOT NULL,
    decimals            INTEGER,
    CONSTRAINT uq_fundamental_metric_value UNIQUE (reporting_period_id, metric_code),
    CONSTRAINT ck_fundamental_metric_value_code_nonblank CHECK (btrim(metric_code) <> ''),
    CONSTRAINT ck_fundamental_metric_value_unit_nonblank CHECK (btrim(unit) <> '')
);

CREATE INDEX ix_fundamental_metric_value_code
    ON fundamental.metric_value (metric_code, reporting_period_id);

-- Reported facts are append-only; corrections are new revisions, never rewrites.
CREATE FUNCTION fundamental.reject_mutation() RETURNS trigger AS $$
BEGIN
    RAISE
EXCEPTION 'fundamental records are immutable';
END;
$$
LANGUAGE plpgsql;

CREATE TRIGGER trg_fundamental_filing_immutable
    BEFORE UPDATE OR
DELETE
ON fundamental.filing
    FOR EACH ROW EXECUTE FUNCTION fundamental.reject_mutation();

CREATE TRIGGER trg_fundamental_reporting_period_immutable
    BEFORE UPDATE OR
DELETE
ON fundamental.reporting_period
    FOR EACH ROW EXECUTE FUNCTION fundamental.reject_mutation();

CREATE TRIGGER trg_fundamental_statement_line_immutable
    BEFORE UPDATE OR
DELETE
ON fundamental.statement_line
    FOR EACH ROW EXECUTE FUNCTION fundamental.reject_mutation();

CREATE TRIGGER trg_fundamental_metric_value_immutable
    BEFORE UPDATE OR
DELETE
ON fundamental.metric_value
    FOR EACH ROW EXECUTE FUNCTION fundamental.reject_mutation();
