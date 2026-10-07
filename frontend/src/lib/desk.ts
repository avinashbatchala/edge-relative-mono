import type { FeatureDashboardRow } from '@/api/features'

export interface MarketSummary {
  total: number
  positives: number
  negatives: number
  /** Share of instruments with RRS > 0, over those that have an RRS value. */
  breadth: number | null
  avgAtrPercent: number | null
  /** Most common market context state across the rows. */
  marketState: string | null
}

function dominant(values: (string | null)[]): string | null {
  const counts = new Map<string, number>()
  for (const value of values) {
    if (value) {
      counts.set(value, (counts.get(value) ?? 0) + 1)
    }
  }
  let best: string | null = null
  let bestCount = 0
  for (const [state, count] of counts) {
    if (count > bestCount) {
      best = state
      bestCount = count
    }
  }
  return best
}

/** Deterministic market summary derived from the feature board rows. */
export function marketSummary(rows: FeatureDashboardRow[]): MarketSummary {
  const withRrs = rows.filter((row) => row.rrsRaw !== null)
  const positives = withRrs.filter((row) => (row.rrsRaw ?? 0) > 0).length
  const negatives = withRrs.filter((row) => (row.rrsRaw ?? 0) < 0).length
  const atrs = rows
    .map((row) => row.atrPercent)
    .filter((value): value is number => value !== null)
  const avgAtrPercent = atrs.length
    ? atrs.reduce((sum, value) => sum + value, 0) / atrs.length
    : null
  return {
    total: rows.length,
    positives,
    negatives,
    breadth: withRrs.length ? positives / withRrs.length : null,
    avgAtrPercent,
    marketState: dominant(rows.map((row) => row.marketState)),
  }
}

/** Most recently observed rows first. */
export function recentRows(
  rows: FeatureDashboardRow[],
  limit = 5,
): FeatureDashboardRow[] {
  return [...rows]
    .filter((row) => row.observationTime !== null)
    .sort((a, b) =>
      (b.observationTime ?? '').localeCompare(a.observationTime ?? ''),
    )
    .slice(0, limit)
}
