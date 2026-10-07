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

export interface SectorGroup {
  code: string | null
  name: string | null
  count: number
  positives: number
  negatives: number
  rrsCount: number
  /** Mean stock RRS within the sector (null when no member reports RRS). */
  avgRrs: number | null
  /** Mean sector RRS versus the broad market (same across members). */
  sectorRrs: number | null
  state: string | null
}

/**
 * Deterministic sector board derived from feature rows. Instruments with no verified NSE sector
 * index map to a single `null` "Unmapped" group rather than being dropped or guessed. Ordering is
 * by sector strength (RRS vs market) then size, so the same input always yields the same board.
 */
export function sectorSummary(rows: FeatureDashboardRow[]): SectorGroup[] {
  const groups = new Map<string, FeatureDashboardRow[]>()
  for (const row of rows) {
    const key = row.sectorCode ?? '\u0000unmapped'
    const bucket = groups.get(key) ?? []
    bucket.push(row)
    groups.set(key, bucket)
  }

  const mean = (values: number[]): number | null =>
    values.length
      ? values.reduce((sum, value) => sum + value, 0) / values.length
      : null

  const result: SectorGroup[] = []
  for (const [key, bucket] of groups) {
    const rrsValues = bucket
      .map((row) => row.rrsRaw)
      .filter((value): value is number => value !== null)
    const sectorRrsValues = bucket
      .map((row) => row.sectorRrsRaw)
      .filter((value): value is number => value !== null)
    const named = bucket.find((row) => row.sectorName)
    result.push({
      code: key === '\u0000unmapped' ? null : key,
      name: key === '\u0000unmapped' ? null : (named?.sectorName ?? null),
      count: bucket.length,
      positives: rrsValues.filter((value) => value > 0).length,
      negatives: rrsValues.filter((value) => value < 0).length,
      rrsCount: rrsValues.length,
      avgRrs: mean(rrsValues),
      sectorRrs: mean(sectorRrsValues),
      state: dominant(bucket.map((row) => row.sectorState)),
    })
  }

  return result.sort((a, b) => {
    if ((a.code === null) !== (b.code === null)) return a.code === null ? 1 : -1
    const aRrs = a.sectorRrs ?? -Infinity
    const bRrs = b.sectorRrs ?? -Infinity
    if (aRrs !== bRrs) return bRrs - aRrs
    if (a.count !== b.count) return b.count - a.count
    return (a.code ?? '').localeCompare(b.code ?? '')
  })
}
