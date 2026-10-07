# ADR-004: macOS Desktop via Tauri 2 Wrapping the Vue Workstation

- **Status:** Superseded (2026-10-07) — the desktop shell was not pursued. The product is
  web-only: a browser Vue workstation served same-origin against the local backend, single
  operator, localhost-only. The desktop decision is retained here for history only.
- **Date:** 2026-10-07
- **Superseded by:** the web-only delivery decision (this note). The `desktop/` Tauri shell,
  its backend CORS origin allowances, and its macOS CI job were removed.
- **Related:** DD-01 §"Product UI & Operator Workstation", DD-04 §44/§48 frontend, `DD06 - Fundamental Analysis.md`

## Context

DD-04 specifies the operator workstation as a **browser-delivered Vue 3 application**
(HTTPS+JSON and WebSocket to the backend) and does not mention a native desktop client.
There is currently no desktop/native packaging anywhere in the repository; the frontend is
a pure browser SPA.

The operator wants an installable macOS application equivalent to FinRobot Desktop. FinRobot
Desktop v0.1.0 is a Tauri/Rust shell around a React frontend, distributed as an Apple-Silicon
`.dmg`. Edge Relative's frontend is Vue, not React.

DD-04 and AGENTS.md require not introducing new frameworks without a demonstrated
requirement. A native macOS app is now an explicit requirement.

## Decision

1. Add a Tauri 2 desktop shell in a new top-level `desktop/` workspace that consumes the
   existing `frontend/` build output (`frontendDist` → `../frontend/dist`, `devUrl` → the
   Vite dev server). The Vue application stays a pure browser SPA and its CI is unchanged.
2. Targets are macOS (Apple Silicon first) producing a `.app`/`.dmg`. Recall the unsigned
   first-launch caveat (`xattr -cr`) until signing/notarization is configured.
3. The desktop shell is a presentation client only. It holds **no** broker or LLM
   credentials and talks only to the local backend. All authority remains in Java.

## Consequences

- Reuses all existing frontend work; no React rewrite.
- Introduces Rust/Tauri as a new build dependency, recorded here as the demonstrated-need
  exception to the DD-04 stack. It does not enter the trading-authority boundary.
- Requires a macOS CI job (`pnpm tauri build`) separate from the browser frontend checks.
- Secrets: `LLM_API_KEY`, `GROWW_*`, and database credentials are never bundled into the
  app; only `VITE_*` public values are compiled into the frontend.
- Signing/notarization, auto-update, and distribution are deferred until the app is used
  beyond the single operator.

## Verification

- `pnpm build` in `frontend/` still passes unchanged.
- `pnpm tauri build` in `desktop/` produces a launchable `.app`/`.dmg` against a local
  backend.
