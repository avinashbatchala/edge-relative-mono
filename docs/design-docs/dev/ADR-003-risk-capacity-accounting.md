# ADR-003: Risk capacity accounting and modifier application

## Status

Accepted for Risk + Position Sizing V1.

## Context

DD-03 mandates the `AvailableTradeRiskBudget` min() over remaining capacities and an ordered set of
risk-reduction modifiers, but deliberately leaves several compositions underspecified (DD-03 open
questions 1–3):

- whether the risk-state modifier is applied to the ceiling and again after the min();
- how `remaining_*_capacity` is composed from realized loss, open risk, and reserved risk without
  double counting;
- whether correlation is both a budget modifier and a quantity cap.

Leaving these implicit would make decisions non-deterministic across implementers and would risk
double-reducing or double-spending capacity.

## Decision

1. **Risk-state modifier is applied exactly once**, to the post-min() budget, in the canonical order
   `risk_state_modifier → correlation_modifier (when enabled) → (quality/ML when later authorized)`.
   Every hard ceiling is rechecked after the modifiers. Correlation is a reducer only and is **not**
   also emitted as a quantity cap (its cap is `NOT_APPLICABLE`), so it cannot reduce twice.

2. **Capacity buckets are composed explicitly and never mixed with notional limits:**

   | Bucket | Remaining capacity |
   | --- | --- |
   | session | `RRE × sessionRiskBudgetFraction − realizedSessionLoss − reservedRisk` |
   | portfolio open risk | `RRE × maxOpenRiskFraction − portfolioOpenRisk − reservedRisk` |
   | strategy | `RRE × strategyBudget − strategyRealized − strategyOpen − strategyReserved` |
   | symbol | `RRE × symbolBudget − symbolRealized − symbolOpen − symbolReserved` |
   | sector | `RRE × sectorBudget − sectorRealized − sectorOpen − sectorReserved` |

   Reserved risk intentionally consumes session, portfolio, strategy, symbol, and sector buckets: a
   reservation will become open risk in each. It is never subtracted twice within one bucket, and
   open risk is never subtracted from the session bucket (session uses realized loss), so
   `SessionDrawdown` (which includes unrealized) does not double-count `PortfolioOpenRisk`.

3. **Currency budgets and currency notional limits stay separate.** The risk budget min() contains
   only currency *risk* capacities; gross/net/symbol/sector notional limits become independent
   quantity caps. They are never combined in one `min()`.

4. **Rounding is toward lower risk.** Protective stops round away from entry (LONG floor, SHORT
   ceiling); quantities always round down to the increment; monetary arithmetic uses scale 8 with
   `RoundingMode.DOWN`.

## Consequences

- Identical candidate/context/policy inputs yield identical decisions and decision keys.
- A retry of the same evaluation is a no-op at the database (context key, decision key, one
  reservation per decision), independently of whether sequential prior state was carried forward.
- The composition is conservative: reserved risk is charged to every bucket it will occupy.
- If a future cost model makes `ExecutionAllowancePerUnit` quantity-dependent, sizing must iterate
  (size → recompute costs → reduce) until all limits hold; the current per-unit model does not.
- EXIT_REQUIRED and existing-position risk management remain out of scope for this milestone.

## Alternatives considered

- Applying the risk-state modifier at the ceiling *and* after the min() — rejected: double reduction
  and non-deterministic intent.
- Subtracting `SessionDrawdown` (inclusive of unrealized) in the session bucket — rejected:
  double-counts with portfolio open risk.
- Using a single `min()` across currency risk and notional quantities — rejected: mixes units and
  obscures the binding constraint.
