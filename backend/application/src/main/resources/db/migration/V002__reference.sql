-- Edge Relative
-- Flyway V002: reference schema

CREATE TABLE reference.exchange (
    exchange_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    timezone TEXT NOT NULL,
    currency_code TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_exchange_code UNIQUE (code),
    CONSTRAINT ck_exchange_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_exchange_name_nonblank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_exchange_timezone_nonblank CHECK (btrim(timezone) <> ''),
    CONSTRAINT ck_exchange_currency_code CHECK (length(currency_code) = 3)
);

CREATE TABLE reference.broker (
    broker_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_broker_code UNIQUE (code),
    CONSTRAINT ck_broker_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_broker_name_nonblank CHECK (btrim(name) <> '')
);

CREATE TABLE reference.timeframe (
    timeframe_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL,
    duration_seconds INTEGER,
    calendar_based BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_timeframe_code UNIQUE (code),
    CONSTRAINT ck_timeframe_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_timeframe_duration CHECK (
        (calendar_based = TRUE AND duration_seconds IS NULL)
        OR (calendar_based = FALSE AND duration_seconds IS NOT NULL AND duration_seconds > 0)
    )
);

CREATE TABLE reference.instrument (
    instrument_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_key UUID NOT NULL,
    exchange_id BIGINT NOT NULL REFERENCES reference.exchange(exchange_id) ON DELETE RESTRICT,
    instrument_type TEXT NOT NULL,
    segment TEXT NOT NULL,
    canonical_symbol TEXT NOT NULL,
    display_name TEXT,
    currency_code TEXT NOT NULL DEFAULT 'INR',
    tick_size NUMERIC(20,8) NOT NULL,
    lot_size BIGINT NOT NULL DEFAULT 1,
    trading_status TEXT NOT NULL DEFAULT 'ACTIVE',
    listed_from DATE,
    listed_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_instrument_key UNIQUE (instrument_key),
    CONSTRAINT ck_instrument_type CHECK (instrument_type IN ('EQUITY','INDEX','ETF','FUTURE','OPTION','CURRENCY','COMMODITY','OTHER')),
    CONSTRAINT ck_instrument_segment_nonblank CHECK (btrim(segment) <> ''),
    CONSTRAINT ck_instrument_symbol_nonblank CHECK (btrim(canonical_symbol) <> ''),
    CONSTRAINT ck_instrument_currency_code CHECK (length(currency_code) = 3),
    CONSTRAINT ck_instrument_tick_size CHECK (tick_size > 0),
    CONSTRAINT ck_instrument_lot_size CHECK (lot_size > 0),
    CONSTRAINT ck_instrument_status CHECK (trading_status IN ('ACTIVE','SUSPENDED','DELISTED','EXPIRED','INACTIVE')),
    CONSTRAINT ck_instrument_listing_dates CHECK (listed_to IS NULL OR listed_from IS NULL OR listed_to >= listed_from)
);

CREATE INDEX ix_instrument_exchange_status
    ON reference.instrument(exchange_id, trading_status, instrument_type);

CREATE TABLE reference.instrument_identifier (
    instrument_identifier_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    exchange_id BIGINT REFERENCES reference.exchange(exchange_id) ON DELETE RESTRICT,
    identifier_type TEXT NOT NULL,
    identifier_value TEXT NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    validity DATERANGE GENERATED ALWAYS AS (daterange(valid_from, valid_to, '[)')) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_instrument_identifier_type_nonblank CHECK (btrim(identifier_type) <> ''),
    CONSTRAINT ck_instrument_identifier_value_nonblank CHECK (btrim(identifier_value) <> ''),
    CONSTRAINT ck_instrument_identifier_dates CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ex_instrument_identifier_one_value_per_type
        EXCLUDE USING gist (instrument_id WITH =, identifier_type WITH =, validity WITH &&),
    CONSTRAINT ex_instrument_identifier_unique_assignment
        EXCLUDE USING gist ((COALESCE(exchange_id, 0)) WITH =, identifier_type WITH =, identifier_value WITH =, validity WITH &&)
);

CREATE TABLE reference.derivative_contract (
    instrument_id BIGINT PRIMARY KEY REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    underlying_instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    expiry_date DATE NOT NULL,
    strike_price NUMERIC(20,8),
    option_type TEXT,
    contract_multiplier NUMERIC(20,8) NOT NULL DEFAULT 1,
    settlement_type TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_derivative_not_self_underlying CHECK (instrument_id <> underlying_instrument_id),
    CONSTRAINT ck_derivative_multiplier CHECK (contract_multiplier > 0),
    CONSTRAINT ck_derivative_option_type CHECK (option_type IS NULL OR option_type IN ('CALL','PUT')),
    CONSTRAINT ck_derivative_option_shape CHECK (
        (option_type IS NULL AND strike_price IS NULL)
        OR (option_type IN ('CALL','PUT') AND strike_price IS NOT NULL AND strike_price >= 0)
    )
);

CREATE INDEX ix_derivative_underlying_expiry
    ON reference.derivative_contract(underlying_instrument_id, expiry_date);

CREATE TABLE reference.broker_instrument_mapping (
    broker_instrument_mapping_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    broker_id BIGINT NOT NULL REFERENCES reference.broker(broker_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    broker_token TEXT NOT NULL,
    broker_symbol TEXT,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    validity TSTZRANGE GENERATED ALWAYS AS (tstzrange(valid_from, valid_to, '[)')) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_broker_mapping_token_nonblank CHECK (btrim(broker_token) <> ''),
    CONSTRAINT ck_broker_mapping_dates CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ex_broker_instrument_one_active_token
        EXCLUDE USING gist (broker_id WITH =, instrument_id WITH =, validity WITH &&),
    CONSTRAINT ex_broker_token_one_instrument
        EXCLUDE USING gist (broker_id WITH =, broker_token WITH =, validity WITH &&)
);

CREATE INDEX ix_broker_instrument_mapping_current
    ON reference.broker_instrument_mapping(broker_id, instrument_id)
    WHERE valid_to IS NULL;

CREATE TABLE reference.sector (
    sector_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sector_code UNIQUE (code),
    CONSTRAINT ck_sector_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_sector_name_nonblank CHECK (btrim(name) <> '')
);

CREATE TABLE reference.instrument_sector_history (
    instrument_sector_history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    sector_id BIGINT NOT NULL REFERENCES reference.sector(sector_id) ON DELETE RESTRICT,
    valid_from DATE NOT NULL,
    valid_to DATE,
    validity DATERANGE GENERATED ALWAYS AS (daterange(valid_from, valid_to, '[)')) STORED,
    source TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_instrument_sector_dates CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ex_instrument_sector_no_overlap
        EXCLUDE USING gist (instrument_id WITH =, validity WITH &&)
);

CREATE INDEX ix_instrument_sector_history_sector_date
    ON reference.instrument_sector_history(sector_id, valid_from, valid_to);

CREATE TABLE reference.benchmark (
    benchmark_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    benchmark_key UUID NOT NULL,
    exchange_id BIGINT REFERENCES reference.exchange(exchange_id) ON DELETE RESTRICT,
    instrument_id BIGINT REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    benchmark_type TEXT NOT NULL DEFAULT 'INDEX',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_benchmark_key UNIQUE (benchmark_key),
    CONSTRAINT uq_benchmark_code UNIQUE (code),
    CONSTRAINT ck_benchmark_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_benchmark_name_nonblank CHECK (btrim(name) <> '')
);

CREATE TABLE reference.sector_benchmark_history (
    sector_benchmark_history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sector_id BIGINT NOT NULL REFERENCES reference.sector(sector_id) ON DELETE RESTRICT,
    benchmark_id BIGINT NOT NULL REFERENCES reference.benchmark(benchmark_id) ON DELETE RESTRICT,
    valid_from DATE NOT NULL,
    valid_to DATE,
    validity DATERANGE GENERATED ALWAYS AS (daterange(valid_from, valid_to, '[)')) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sector_benchmark_dates CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ex_sector_benchmark_no_overlap
        EXCLUDE USING gist (sector_id WITH =, validity WITH &&)
);

CREATE TABLE reference.benchmark_constituent_history (
    benchmark_constituent_history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    benchmark_id BIGINT NOT NULL REFERENCES reference.benchmark(benchmark_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    weight DOUBLE PRECISION,
    valid_from DATE NOT NULL,
    valid_to DATE,
    validity DATERANGE GENERATED ALWAYS AS (daterange(valid_from, valid_to, '[)')) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_benchmark_constituent_weight CHECK (weight IS NULL OR (weight >= 0 AND weight <= 1)),
    CONSTRAINT ck_benchmark_constituent_dates CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ex_benchmark_constituent_no_overlap
        EXCLUDE USING gist (benchmark_id WITH =, instrument_id WITH =, validity WITH &&)
);

CREATE INDEX ix_benchmark_constituent_instrument_date
    ON reference.benchmark_constituent_history(instrument_id, valid_from, valid_to);

CREATE TABLE reference.universe (
    universe_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    universe_key UUID NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_universe_key UNIQUE (universe_key),
    CONSTRAINT uq_universe_code UNIQUE (code),
    CONSTRAINT ck_universe_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_universe_name_nonblank CHECK (btrim(name) <> '')
);

CREATE TABLE reference.universe_membership_history (
    universe_membership_history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    universe_id BIGINT NOT NULL REFERENCES reference.universe(universe_id) ON DELETE RESTRICT,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    valid_from DATE NOT NULL,
    valid_to DATE,
    validity DATERANGE GENERATED ALWAYS AS (daterange(valid_from, valid_to, '[)')) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_universe_membership_dates CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ex_universe_membership_no_overlap
        EXCLUDE USING gist (universe_id WITH =, instrument_id WITH =, validity WITH &&)
);

CREATE INDEX ix_universe_membership_instrument_date
    ON reference.universe_membership_history(instrument_id, valid_from, valid_to);

CREATE TABLE reference.trading_calendar_day (
    trading_calendar_day_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exchange_id BIGINT NOT NULL REFERENCES reference.exchange(exchange_id) ON DELETE RESTRICT,
    trading_date DATE NOT NULL,
    day_type TEXT NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trading_calendar_day UNIQUE (exchange_id, trading_date),
    CONSTRAINT ck_trading_calendar_day_type CHECK (day_type IN ('TRADING','HOLIDAY','SPECIAL','CLOSED'))
);

CREATE TABLE reference.trading_session (
    trading_session_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trading_calendar_day_id BIGINT NOT NULL REFERENCES reference.trading_calendar_day(trading_calendar_day_id) ON DELETE CASCADE,
    session_type TEXT NOT NULL,
    opens_at TIMESTAMPTZ NOT NULL,
    closes_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trading_session_type UNIQUE (trading_calendar_day_id, session_type),
    CONSTRAINT ck_trading_session_type CHECK (session_type IN ('PRE_OPEN','NORMAL','POST_CLOSE','SPECIAL')),
    CONSTRAINT ck_trading_session_times CHECK (closes_at > opens_at)
);

CREATE TABLE reference.corporate_action (
    corporate_action_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES reference.instrument(instrument_id) ON DELETE RESTRICT,
    action_type TEXT NOT NULL,
    ex_date DATE NOT NULL,
    record_date DATE,
    effective_date DATE,
    ratio_numerator NUMERIC(24,8),
    ratio_denominator NUMERIC(24,8),
    cash_amount NUMERIC(24,8),
    currency_code TEXT,
    source_reference TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_corporate_action_type CHECK (action_type IN ('DIVIDEND','SPLIT','BONUS','RIGHTS','MERGER','DEMERGER','SYMBOL_CHANGE','DELISTING','OTHER')),
    CONSTRAINT ck_corporate_action_ratio CHECK (
        (ratio_numerator IS NULL AND ratio_denominator IS NULL)
        OR (ratio_numerator IS NOT NULL AND ratio_denominator IS NOT NULL AND ratio_numerator > 0 AND ratio_denominator > 0)
    ),
    CONSTRAINT ck_corporate_action_cash CHECK (cash_amount IS NULL OR cash_amount >= 0),
    CONSTRAINT ck_corporate_action_currency_code CHECK (currency_code IS NULL OR length(currency_code) = 3)
);

CREATE INDEX ix_corporate_action_instrument_date
    ON reference.corporate_action(instrument_id, ex_date);

CREATE TABLE reference.market_data_source (
    market_data_source_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    source_type TEXT NOT NULL,
    broker_id BIGINT REFERENCES reference.broker(broker_id) ON DELETE RESTRICT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_market_data_source_code UNIQUE (code),
    CONSTRAINT ck_market_data_source_code_nonblank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_market_data_source_type CHECK (source_type IN ('BROKER','EXCHANGE','VENDOR','INTERNAL','OTHER'))
);
