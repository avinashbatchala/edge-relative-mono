# Groww Trading API — Endpoint Support Matrix

Source of truth: <https://groww.in/trade-api/docs/curl> (fetched for this change). Groww applies rate
limits at type level; every endpoint consumes its category quota.

Legend:

- `IMPLEMENTED` — read-only/idempotent capability wired through a broker-neutral port.
- `EXPOSED_BUT_DISABLED` — Edge Relative application contract exists; the adapter refuses with
  `BROKER_OPERATION_NOT_ENABLED` and emits zero downstream HTTP.
- `DEPRECATED` — documented deprecated by Groww; not used in production paths.
- `NOT_APPLICABLE` — not part of the current scope.

Rate-limit categories: `AUTHENTICATION`, `ORDERS`, `LIVE_DATA`, `NON_TRADING`.

## Authentication

| Endpoint | Method | Path | Category | Semantics | Internal port | Status | Tests |
|---|---|---|---|---|---|---|---|
| Generate access token (approval) | POST | `/v1/token/api/access` | AUTHENTICATION | read-only (creates a token) | `GrowwAuthenticationClient` / `GrowwAccessTokenProvider` | IMPLEMENTED | `GrowwChecksumTest`, `GrowwAccessTokenProviderSingleFlightTest` |
| Generate access token (TOTP) | POST | `/v1/token/api/access` | AUTHENTICATION | read-only (creates a token) | `GrowwAuthenticationClient` | IMPLEMENTED (TOTP supplied externally) | `GrowwAccessTokenProviderSingleFlightTest` |
| Static access token | n/a | n/a | n/a | config-provided | `GrowwAccessTokenProvider` | IMPLEMENTED | `GrowwAccessTokenProviderSingleFlightTest` |

## Instruments

| Endpoint | Method | Path | Category | Semantics | Internal port | Status | Tests |
|---|---|---|---|---|---|---|---|
| Download instrument master | GET | `https://growwapi-assets.groww.in/instruments/instrument.csv` | NON_TRADING | read-only | `InstrumentBroker.downloadInstrumentMaster` | IMPLEMENTED | `GrowwInstrumentCsvParserTest`, `GrowwReadClientsIntegrationTest` |

## Live Data

| Endpoint | Method | Path | Category | Semantics | Internal port | Status | Tests |
|---|---|---|---|---|---|---|---|
| Get quote | GET | `/v1/live-data/quote` | LIVE_DATA | read-only | `MarketDataBroker.quote` | IMPLEMENTED | `GrowwReadClientsIntegrationTest`, `GrowwApiSafetyTest` |
| Get LTP (batch ≤ 50) | GET | `/v1/live-data/ltp` | LIVE_DATA | read-only | `MarketDataBroker.lastTradedPrices` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Get OHLC (batch ≤ 50) | GET | `/v1/live-data/ohlc` | LIVE_DATA | read-only | `MarketDataBroker.ohlc` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Get option chain | GET | `/v1/option-chain/exchange/{exchange}/underlying/{underlying}` | LIVE_DATA | read-only | `MarketDataBroker.optionChain` | IMPLEMENTED | `GrowwMapperTest`, `GrowwReadClientsIntegrationTest` |
| Get greeks | GET | `/v1/live-data/greeks/exchange/{exchange}/underlying/{underlying}/trading_symbol/{symbol}/expiry/{expiry}` | LIVE_DATA | read-only | `MarketDataBroker.greeks` | IMPLEMENTED | `GrowwMapperTest` |

## Portfolio / Margin / User

| Endpoint | Method | Path | Category | Semantics | Internal port | Status | Tests |
|---|---|---|---|---|---|---|---|
| Get holdings | GET | `/v1/holdings/user` | NON_TRADING | read-only | `PortfolioBroker.holdings` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Get positions | GET | `/v1/positions/user` | NON_TRADING | read-only | `PortfolioBroker.positions` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Get position by symbol | GET | `/v1/positions/trading-symbol` | NON_TRADING | read-only | `PortfolioBroker.positionsForSymbol` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Get available margin | GET | `/v1/margins/detail/user` | NON_TRADING | read-only | `MarginBroker.userMargin` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Required margin for basket | POST | `/v1/margins/detail/orders` | NON_TRADING | **read-only calculation** (no state change) | `MarginBroker.requiredMargin` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Get user profile | GET | `/v1/user/detail` | NON_TRADING | read-only | `PortfolioBroker.userProfile` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |

## Orders (read-only)

| Endpoint | Method | Path | Category | Semantics | Internal port | Status | Tests |
|---|---|---|---|---|---|---|---|
| Get order status | GET | `/v1/order/status/{groww_order_id}` | NON_TRADING | read-only | `OrderQueryBroker.orderStatus` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Get order status by reference | GET | `/v1/order/status/reference/{order_reference_id}` | NON_TRADING | read-only | `OrderQueryBroker.orderStatusByReference` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Get order detail | GET | `/v1/order/detail/{groww_order_id}` | NON_TRADING | read-only | `OrderQueryBroker.orderDetail` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Get order list (page_size ≤ 100) | GET | `/v1/order/list` | NON_TRADING | read-only | `OrderQueryBroker.orders` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Get trades for order (page_size ≤ 50) | GET | `/v1/order/trades/{groww_order_id}` | NON_TRADING | read-only | `OrderQueryBroker.trades` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |

## Smart Orders (read-only)

| Endpoint | Method | Path | Category | Semantics | Internal port | Status | Tests |
|---|---|---|---|---|---|---|---|
| Get smart order | GET | `/v1/order-advance/status/{segment}/{smart_order_type}/internal/{smart_order_id}` | NON_TRADING | read-only | `SmartOrderQueryBroker.smartOrder` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| List smart orders | GET | `/v1/order-advance/list` | NON_TRADING | read-only | `SmartOrderQueryBroker.smartOrders` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |

Validation enforced client-side: `page` 0..500, `page_size` 1..50, date range ≤ 1 month,
`end_date_time` not before `start_date_time`.

## Historical Data / Backtesting

| Endpoint | Method | Path | Category | Semantics | Internal port | Status | Tests |
|---|---|---|---|---|---|---|---|
| Get historical candles | GET | `/v1/historical/candles` | NON_TRADING | read-only | `HistoricalDataBroker.candles` | IMPLEMENTED (range split + bulk fan-out) | `GrowwHistoricalRangeSplitterTest`, `GrowwReadClientsIntegrationTest` |
| Get expiries | GET | `/v1/historical/expiries` | NON_TRADING | read-only | `HistoricalDataBroker.expiries` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Get contracts | GET | `/v1/historical/contracts` | NON_TRADING | read-only | `HistoricalDataBroker.contracts` | IMPLEMENTED | `GrowwReadClientsIntegrationTest` |
| Get historical data (deprecated) | GET | `/v1/historical/candle/range` | NON_TRADING | read-only | — | DEPRECATED (documented replacement used) | — |

Documented max duration per request: 1/2/3/5m → 30 days; 10/15/30m → 90 days; 1h/4h/1d/1w/1m → 180 days.

## State-changing endpoints (represented, never executed)

All of these return HTTP `501` / `BROKER_OPERATION_NOT_ENABLED` from Edge Relative and perform **zero**
downstream HTTP. The safe default adapter has no HTTP client reference.

| Endpoint | Method | Path | Category | Internal port | Status | Tests |
|---|---|---|---|---|---|---|
| Place order | POST | `/v1/order/create` | ORDERS | `ExecutionBroker.placeOrder` | EXPOSED_BUT_DISABLED | `GrowwMutationSafetyTest`, `GrowwApiSafetyTest` |
| Modify order | POST | `/v1/order/modify` | ORDERS | `ExecutionBroker.modifyOrder` | EXPOSED_BUT_DISABLED | `GrowwMutationSafetyTest`, `GrowwApiSafetyTest` |
| Cancel order | POST | `/v1/order/cancel` | ORDERS | `ExecutionBroker.cancelOrder` | EXPOSED_BUT_DISABLED | `GrowwMutationSafetyTest`, `GrowwApiSafetyTest` |
| Create GTT/OCO | POST | `/v1/order-advance/create` | ORDERS | `SmartOrderBroker.createSmartOrder` | EXPOSED_BUT_DISABLED | `GrowwMutationSafetyTest` |
| Modify GTT/OCO | PUT | `/v1/order-advance/modify/{smart_order_id}` | ORDERS | `SmartOrderBroker.modifySmartOrder` | EXPOSED_BUT_DISABLED | `GrowwMutationSafetyTest` |
| Cancel GTT/OCO | POST | `/v1/order-advance/cancel/{segment}/{smart_order_type}/{smart_order_id}` | ORDERS | `SmartOrderBroker.cancelSmartOrder` | EXPOSED_BUT_DISABLED | `GrowwMutationSafetyTest` |

## Internal Edge Relative endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/brokers/groww/capabilities` | capability discovery (no execution capabilities advertised) |
| GET | `/api/v1/brokers/groww/instruments` | instrument master |
| GET | `/api/v1/brokers/groww/market-data/quote|ltp|ohlc|option-chain|greeks` | live data |
| GET | `/api/v1/brokers/groww/historical/candles|expiries|contracts` | historical data |
| GET | `/api/v1/brokers/groww/portfolio/holdings|positions|positions/trading-symbol|user` | portfolio |
| GET | `/api/v1/brokers/groww/margin/user` | available margin |
| POST | `/api/v1/brokers/groww/margin/required` | required-margin calculation (read-only) |
| GET | `/api/v1/brokers/groww/orders|orders/status/{id}|orders/status/reference/{ref}|orders/detail/{id}|orders/{id}/trades` | order/trade queries |
| GET | `/api/v1/brokers/groww/smart-orders|smart-orders/{segment}/{type}/{id}` | smart-order queries |
| POST/PUT | `/api/v1/brokers/groww/orders`, `/orders/cancel`, `/smart-orders`, `/smart-orders/{id}`, `/smart-orders/cancel/...` | mutations (501) |

## Documented Groww inconsistencies handled explicitly

- `payload` array vs `payload.expiries`/`payload.contracts` — both shapes accepted.
- Stringified OHLC — parsed explicitly.
- Mixed timestamp formats (`T`-separated, space-separated, epoch) — parsed explicitly.
- Order status is open-ended — unknown values degrade to `UNKNOWN`; other enums fail observably.
- Cancel-order request table erroneously lists `status`; treated as documentation defect.
