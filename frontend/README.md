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

## Market Data

Market Data is underlying-first:

- `/market` is the search landing page. The search is ticker/company-first: the
  backend ranks an exact ticker or company name on the cash equity above its
  futures/options, so searching `RELIANCE` or `Reliance Industries` selects the
  equity.
- `/market/:symbol` is the ticker workspace for one underlying (e.g.
  `/market/RELIANCE`). Selecting a derivative in search routes to its underlying.

The ticker workspace shows live quote/OHLC, bid/ask and depth, a candlestick chart, a
dense historical table, instrument reference details, and the raw normalized API
payloads — plus **Futures** and **Options** tabs as secondary data for that
underlying.

- Server state lives in TanStack Query, keyed per symbol/interval/range, so a late
  response for a previous instrument can never overwrite the current one.
- Only client-side preferences (interval, range) live in Pinia; the instrument is
  derived from the route and the instrument master.
- The API layer is `src/api/`; components never call `fetch` directly.
- Broker-side mutations are intentionally absent; this screen only observes market data.

`pnpm dev` proxies `/api` and `/actuator` to `http://127.0.0.1:8080` (see
`vite.config.ts`). Set `VITE_API_BASE_URL` to point at another origin instead.
