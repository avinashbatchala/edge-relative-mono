import type { FeatureDashboardRow } from '@/api/features'
import type { OpportunityRow } from '@/api/opportunities'

/**
 * Deterministic opportunity ranking. There is no validated probabilistic score, so this is an
 * explicit, documented rule — not an invented confidence. Order:
 *   1. setup maturity (VALID > NEAR_TRIGGER > FORMING > WATCH > other)
 *   2. risk permission (approved/reduced > awaiting > rejected/halted)
 *   3. directional RRS momentum (fast - slow, signed for the trade direction)
 *   4. RVOL interval (participation)
 *   5. symbol (stable tiebreak)
 * The rule is labelled "deterministic" wherever it is shown.
 */

export type Alignment = 'ALIGNED' | 'NEUTRAL' | 'OPPOSED' | null

export interface RankedOpportunity {
  row: OpportunityRow
  feature: FeatureDashboardRow | null
  rank: number
  maturity: number
  momentum: number | null
  alignment: Alignment
}

const MATURITY: Record<string, number> = {
  VALID: 5,
  NEAR_TRIGGER: 4,
  FORMING: 3,
  WATCH: 2,
}

function maturityOf(state: string | null): number {
  return MATURITY[state ?? ''] ?? 1
}

function riskRank(state: string | null): number {
  switch (state) {
    case 'APPROVE':
      return 3
    case 'REDUCE':
      return 2
    case 'AWAITING_RISK_EVALUATION':
    case 'RISK_UNAVAILABLE':
      return 1
    default:
      return 0
  }
}

function directionalMomentum(
  feature: FeatureDashboardRow | null,
  direction: string | null,
): number | null {
  if (!feature || feature.rrsFast === null || feature.rrsSlow === null) {
    return null
  }
  const raw = feature.rrsFast - feature.rrsSlow
  return direction === 'SHORT' ? -raw : raw
}

function alignmentOf(
  feature: FeatureDashboardRow | null,
  direction: string | null,
): Alignment {
  if (!feature || feature.rrsRaw === null) {
    return null
  }
  const long = direction !== 'SHORT'
  const rrsAligned = long ? feature.rrsRaw > 0 : feature.rrsRaw < 0
  const structureLabel = (feature.marketState ?? '').toUpperCase()
  const structureOpposed = long
    ? structureLabel === 'BEAR_STRUCTURE'
    : structureLabel === 'BULL_STRUCTURE'
  if (rrsAligned && !structureOpposed) return 'ALIGNED'
  if (!rrsAligned || structureOpposed) return 'OPPOSED'
  return 'NEUTRAL'
}

export function rankOpportunities(
  rows: OpportunityRow[],
  features: FeatureDashboardRow[],
): RankedOpportunity[] {
  const byInstrument = new Map(features.map((row) => [row.instrumentId, row]))
  const scored = rows.map((row) => {
    const feature = byInstrument.get(row.instrumentId) ?? null
    return {
      row,
      feature,
      rank: 0,
      maturity: maturityOf(row.setupStatus),
      momentum: directionalMomentum(feature, row.direction),
      alignment: alignmentOf(feature, row.direction),
    } satisfies RankedOpportunity
  })

  scored.sort((a, b) => {
    if (a.maturity !== b.maturity) return b.maturity - a.maturity
    const risk = riskRank(b.row.riskState) - riskRank(a.row.riskState)
    if (risk !== 0) return risk
    const momentum =
      (b.momentum ?? Number.NEGATIVE_INFINITY) -
      (a.momentum ?? Number.NEGATIVE_INFINITY)
    if (momentum !== 0) return momentum
    const rvol =
      (b.feature?.rvolInterval ?? Number.NEGATIVE_INFINITY) -
      (a.feature?.rvolInterval ?? Number.NEGATIVE_INFINITY)
    if (rvol !== 0) return rvol
    return a.row.symbol.localeCompare(b.row.symbol)
  })

  scored.forEach((item, index) => {
    item.rank = index + 1
  })
  return scored
}
