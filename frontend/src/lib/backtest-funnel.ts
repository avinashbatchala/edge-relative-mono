/**
 * Backtest decision funnel derived from the engine's `stageCounts`. This answers "where is the
 * strategy filtering opportunities?" for both trade-heavy and zero-trade runs. Counts come straight
 * from the run metrics; nothing is inferred.
 */

export interface FunnelStage {
  key: string
  label: string
  count: number
  /** Drop from the previous stage (positive = eliminations). */
  eliminatedFromPrevious: number | null
}

function count(stageCounts: Record<string, number>, key: string): number {
  return stageCounts[key] ?? 0
}

export function backtestFunnel(
  stageCounts: Record<string, number>,
): FunnelStage[] {
  const order: { key: string; label: string }[] = [
    { key: 'anchorsProcessed', label: 'Anchors processed' },
    { key: 'setup_WATCH', label: 'WATCH' },
    { key: 'setup_FORMING', label: 'FORMING' },
    { key: 'setup_NEAR_TRIGGER', label: 'NEAR_TRIGGER' },
    { key: 'setup_VALID', label: 'VALID (strategy-qualified)' },
  ]
  const stages: FunnelStage[] = []
  for (const { key, label } of order) {
    const value = count(stageCounts, key)
    const previous = stages.length ? stages[stages.length - 1]!.count : null
    stages.push({
      key,
      label,
      count: value,
      eliminatedFromPrevious:
        previous === null ? null : Math.max(0, previous - value),
    })
  }

  const riskApproved =
    count(stageCounts, 'risk_APPROVE') + count(stageCounts, 'risk_REDUCE')
  const riskRejected =
    count(stageCounts, 'risk_REJECT') + count(stageCounts, 'risk_HALT_REQUIRED')
  const riskEvaluated = riskApproved + riskRejected
  const extra: { key: string; label: string; count: number }[] = [
    { key: 'riskEvaluated', label: 'Risk evaluated', count: riskEvaluated },
    { key: 'riskApproved', label: 'Risk approved', count: riskApproved },
    {
      key: 'plansCreated',
      label: 'Plans created',
      count: count(stageCounts, 'plansCreated'),
    },
    {
      key: 'ordersSubmitted',
      label: 'Orders submitted',
      count: count(stageCounts, 'ordersSubmitted'),
    },
    { key: 'fills', label: 'Fills', count: count(stageCounts, 'fills') },
    {
      key: 'exits',
      label: 'Completed exits',
      count: count(stageCounts, 'exits'),
    },
  ]
  for (const stage of extra) {
    const previous = stages[stages.length - 1]!.count
    stages.push({
      key: stage.key,
      label: stage.label,
      count: stage.count,
      eliminatedFromPrevious: Math.max(0, previous - stage.count),
    })
  }
  return stages
}

export interface ReasonCount {
  code: string
  count: number
}

/** Aggregated setup-gate reason codes (long + short), most frequent first. */
export function funnelReasons(
  stageCounts: Record<string, number>,
  limit = 12,
): ReasonCount[] {
  const totals = new Map<string, number>()
  for (const [key, value] of Object.entries(stageCounts)) {
    const match = /^(?:long|short)Reason_(.+)$/.exec(key)
    if (!match) continue
    totals.set(match[1]!, (totals.get(match[1]!) ?? 0) + value)
  }
  return [...totals.entries()]
    .map(([code, value]) => ({ code, count: value }))
    .sort((a, b) => b.count - a.count)
    .slice(0, limit)
}
