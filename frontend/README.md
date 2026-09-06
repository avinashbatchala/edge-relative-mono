# Edge Relative Frontend

Static, presentation-only workstation scaffold. **Not connected; trading unavailable.**
No market data (live or simulated), backend business API, broker integration, trading
controls, or browser-owned financial state is implemented.

## Toolchain

- Node 24 LTS, at least 24.15.0 (verified with 24.20.0).
- pnpm 10.34.5, pinned in `packageManager`.
- Vue 3 + strict TypeScript + Composition API (`<script setup>`), Vite, and Pinia.
- Pinia is registered only; no store is needed for this static page. Future local UI
  state belongs in Vue, not an invented trading store.
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
`typecheck` runs `vue-tsc`; `test` runs the Vitest component smoke test without watch
mode. `build` type-checks before creating the static bundle in `dist/`.
`dev` serves the page locally with Vite; it does not connect to a backend.

There is deliberately no router, API client, charting library, component framework,
or persistence. The component smoke test uses Vue Testing Library and jsdom; it is
not a real-browser visual or end-to-end test.
