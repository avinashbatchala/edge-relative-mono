import { apiGet } from './http'

export interface OpportunityRow {
  instrumentId: number
  symbol: string
  displayName: string | null
  exchange: string
  segment: string | null
  timeframe: string
  setupObservationId: number | null
  setupStatus: string | null
  direction: string | null
  setupFamily: string | null
  setupObservedAt: string | null
  setupInstanceId: string | null
  riskState: string
  riskDecisionKey: string | null
  riskDecision: string | null
  rejectionReason: string | null
  planKey: string | null
  planEligibilityStatus: string | null
  planPermitsEntry: boolean
  planExpiresAt: string | null
}

export function getOpportunities(
  signal?: AbortSignal,
): Promise<OpportunityRow[]> {
  return apiGet<OpportunityRow[]>('/api/v1/opportunities', { signal })
}

export const opportunityKeys = {
  all: ['opportunities'] as const,
  list: () => [...opportunityKeys.all, 'list'] as const,
}
