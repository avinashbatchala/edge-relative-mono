import type { FeatureDashboardRow } from '@/api/features'
import type { OpportunityRow } from '@/api/opportunities'
import type { SetupObservation } from '@/api/setups'

/**
 * Derived decision gates for one instrument, in the DD-01 order:
 *   MARKET → SECTOR → STOCK → SETUP → RISK.
 * These are derived from the measurements available to the browser (feature dashboard, latest setup
 * observation, opportunity risk state). They are advisory and explicitly labelled "derived" — the
 * authoritative gate results exist only inside the strategy/risk engines.
 */

export type GateStatus = 'PASS' | 'FAIL' | 'WAITING' | 'NOT_EVALUATED'

export interface DecisionGate {
  code: string
  label: string
  status: GateStatus
  detail: string
}

export interface DecisionGateInput {
  feature: FeatureDashboardRow | null
  setup: SetupObservation | null
  opportunity: OpportunityRow | null
}

function direction(input: DecisionGateInput): 'LONG' | 'SHORT' {
  const raw = input.setup?.direction ?? input.opportunity?.direction ?? 'LONG'
  return raw === 'SHORT' ? 'SHORT' : 'LONG'
}

export function decisionGates(input: DecisionGateInput): DecisionGate[] {
  const feature = input.feature
  const long = direction(input) === 'LONG'
  const gates: DecisionGate[] = []

  // Market structure alignment.
  const marketState = (feature?.marketState ?? '').toUpperCase()
  gates.push({
    code: 'MARKET',
    label: 'Market alignment',
    status:
      marketState === ''
        ? 'NOT_EVALUATED'
        : (
              long
                ? marketState === 'BEAR_STRUCTURE'
                : marketState === 'BULL_STRUCTURE'
            )
          ? 'FAIL'
          : 'PASS',
    detail: feature?.marketState ?? 'market context unavailable',
  })

  // Sector alignment.
  const sectorState = (feature?.sectorState ?? '').toUpperCase()
  gates.push({
    code: 'SECTOR',
    label: 'Sector alignment',
    status:
      sectorState === ''
        ? 'NOT_EVALUATED'
        : (long ? sectorState === 'WEAK' : sectorState === 'STRONG')
          ? 'FAIL'
          : 'PASS',
    detail: feature?.sectorState ?? 'sector context unavailable',
  })

  // Daily structure alignment.
  const daily = (feature?.dailyRrsState ?? '').toUpperCase()
  gates.push({
    code: 'DAILY',
    label: 'Daily structure',
    status:
      daily === ''
        ? 'NOT_EVALUATED'
        : daily === (long ? 'LONG_ALIGNED' : 'SHORT_ALIGNED')
          ? 'PASS'
          : 'FAIL',
    detail: feature?.dailyRrsState ?? 'daily context unavailable',
  })

  // Intraday RRS direction.
  const rrs = feature?.rrsRaw ?? null
  gates.push({
    code: 'RRS',
    label: 'RRS direction',
    status:
      rrs === null
        ? 'NOT_EVALUATED'
        : (long ? rrs > 0 : rrs < 0)
          ? 'PASS'
          : 'FAIL',
    detail: rrs === null ? 'RRS unavailable' : `raw ${rrs.toFixed(2)}`,
  })

  // Participation.
  const rvol = feature?.rvolInterval ?? null
  gates.push({
    code: 'PARTICIPATION',
    label: 'Participation',
    status: rvol === null ? 'NOT_EVALUATED' : rvol >= 1 ? 'PASS' : 'FAIL',
    detail:
      rvol === null ? 'RVOL unavailable' : `interval RVOL ${rvol.toFixed(2)}`,
  })

  // Technical void / structure are not exposed to the browser.
  gates.push({
    code: 'STRUCTURE',
    label: 'Technical void / structure',
    status: 'NOT_EVALUATED',
    detail: 'not exposed to the browser',
  })

  // Trigger.
  const setupState = (
    input.setup?.setupStatus ??
    input.opportunity?.setupStatus ??
    ''
  ).toUpperCase()
  gates.push({
    code: 'TRIGGER',
    label: 'Trigger',
    status:
      setupState === 'VALID'
        ? 'PASS'
        : setupState === ''
          ? 'NOT_EVALUATED'
          : 'WAITING',
    detail:
      input.setup?.setupStatus ??
      input.opportunity?.setupStatus ??
      'no setup observation',
  })

  // Risk.
  const riskState = (input.opportunity?.riskState ?? '').toUpperCase()
  gates.push({
    code: 'RISK',
    label: 'Risk',
    status:
      riskState === 'APPROVE' || riskState === 'REDUCE'
        ? 'PASS'
        : riskState === 'REJECT' || riskState === 'HALT_REQUIRED'
          ? 'FAIL'
          : riskState === ''
            ? 'NOT_EVALUATED'
            : 'WAITING',
    detail:
      input.opportunity?.rejectionReason ??
      input.opportunity?.riskState ??
      'not evaluated',
  })

  return gates
}
