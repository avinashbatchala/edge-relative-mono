# Edge Relative Frontend

Operator workstation. The presentation layer displays authoritative backend state and
requests actions; it never owns trading authority, risk, or order state.

## Toolchain

- Node 24 LTS, at least 24.15.0 (verified with 24.21.0). If it is not already on your
  PATH: `brew install node@24` (keg-only) and add `/opt/homebrew/opt/node@24/bin` to PATH.
- pnpm 10.34.5, pinned in `packageManager`; run `corepack enable` once with Node 24 on
  PATH so the `pnpm` command exists. **Use `pnpm`, not `npm`**: native optional
  dependencies (`@rolldown/binding-*`, `esbuild`) are linked per platform by pnpm, and
  `npm run dev` against a pnpm-installed `node_modules` fails with "Cannot find native
  binding". After switching Node versions or platforms, delete `node_modules` and run
  `pnpm install --frozen-lockfile` again.
- Vue 3 + strict TypeScript + Composition API (`<script setup>`), Vite, and Pinia.
- `vue-router` for routes, TanStack Query (`@tanstack/vue-query`) for server state,
  Tailwind CSS v4 with shadcn-vue primitives in `src/components/ui`, and
  `lightweight-charts` for candlesticks.
- Exact direct dependency versions are in `package.json`; `pnpm-lock.yaml` pins resolution.

## Commands

Run from `frontend/` with Node and pnpm on PATH:

```sh
pnpm install --frozen-lockfile
pnpm dev
pnpm lint
pnpm format
pnpm typecheck
pnpm test
pnpm build
```

`lint` runs ESLint and a Prettier check. `format` writes formatting changes.
`typecheck` runs `vue-tsc`; `test` runs Vitest component tests without watch mode.
`build` type-checks before creating the static bundle in `dist/`.

## Application shell

The left sidebar (native shadcn-vue `Sidebar`) is the navigation shell, organised around
the decision pipeline:

- `/desk` — Desk: watchlist/live counts plus Market, System and Portfolio tiles, an
  opportunity-board preview, a "what changed" recent-observations list, and data-quality
  notices.
- `/scanner` — Scanner: the feature board (filters, saved screens, column selector, and a
  per-row **Why** drawer with the deterministic measurements behind a row).
- `/setups` — Setups: setup, risk and plan state for the watchlist, with the trade-plan sheet.
- `/chart` and `/chart/:symbol` — Chart: the market-data workspace for one underlying.
- `/watchlist` — the active watchlist (up to 20 canonical instruments).
- `/trades` — Trades: read-only broker account state (positions, holdings, orders, margin).
  Order placement/modification/cancellation is not enabled.
- `/research` — Research: canonical M1 coverage, backfill with a **paginated** run history
  (status filter, run detail, retry), and a chart/table view of persisted candles.
- `/validate` — Validate: backtests.
- `/catalog` — Catalog: versioned strategies and risk policies.
- `/fundamentals` — Fundamentals: a point-in-time, code-computed watchlist screener with
  advisory LLM narration; values are also available in the Chart rail.

Legacy paths (`/overview`, `/market`, `/opportunities`, `/features`, `/backtests`,
`/strategies`) redirect to the workspace paths. `Journal` and `System` are shown
disabled as coming later.

The shell also provides a global **command palette** (⌘K / Ctrl+K) for navigation, symbol
search and actions, a bottom **status bar** (broker health, stream state, data age, and the
advisory/no-execution reminder), a **density** toggle (comfortable/compact, persisted), and
toasts. Reusable primitives live in `src/components/common` (`EmptyState`,
`PermissionNotice`, `SegmentedTabs`) and `src/components/data-table` (`DataTable`, built on
`@tanstack/vue-table`). Client preferences persist via `src/stores/preferences`.

## Watchlist

The Watchlist tool persists canonical instrument identity via the broker-neutral
`/api/v1/watchlist` API (backed by the existing `operational.watchlist` /
`reference.instrument` schema). Broker tokens are stored only as a mapping detail and
never as identity.

- Add via search (underlying-first), remove, and reorder; capacity is enforced at 20
  and duplicates are rejected server-side.
- Every row renders current market state from the existing market-data quote API with
  its own query, so one failing instrument does not break the table.
- Clicking a row opens the instrument's ticker page.

## Market Data

Market Data is underlying-first:

- `/chart` is the search landing page. The search is ticker/company-first: the
  backend ranks an exact ticker or company name on the cash equity above its
  futures/options, so searching `RELIANCE` or `Reliance Industries` selects the
  equity.
- `/chart/:symbol` is the ticker workspace for one underlying (e.g.
  `/chart/RELIANCE`). Selecting a derivative in search routes to its underlying.

The ticker workspace shows a slim instrument title (symbol · name · badges) above two
even-height cards (Price, Session), a full-width **chart card**, and a separate full-width
**data card** with tabs (Features, Depth, Options, Fundamentals, Historical, Details, Raw).
The data card scrolls internally; the Options chain defaults to the nearest upcoming expiry
(Groww does not expose futures enumeration, so there is no Futures tab). Range metrics live in
the Details tab. `src/components/common/WhyPanel.vue` renders the "why" explanations.

- Server state lives in TanStack Query, keyed per symbol/interval/range, so a late
  response for a previous instrument can never overwrite the current one.
- Only client-side preferences (interval, range) live in Pinia; the instrument is
  derived from the route and the instrument master.
- The API layer is `src/api/`; components never call `fetch` directly.
- Broker-side mutations are intentionally absent; this screen only observes market data.

`pnpm dev` proxies `/api` and `/actuator` to `http://127.0.0.1:8080` (see
`vite.config.ts`). Set `VITE_API_BASE_URL` to point at another origin instead.
