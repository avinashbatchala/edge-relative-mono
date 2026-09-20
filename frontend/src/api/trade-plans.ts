import { apiGet } from './http'

export interface TradePlanEligibility {
  status: string
  permitsEntry: boolean
  evaluatedAt: string
  expiresAt: string | null
  reasons: string[]
}

export interface TradePlan {
  planKey: string
  decisionKey: string
  riskDecision: string
  decisionAt: string
  setupObservationId: number
  setupInstanceId: string | null
  brokerAccountId: number
  instrumentId: number
  symbol: string
  displayName: string | null
  direction: string
  strategyId: string
  strategyVersion: string
  entryPattern: string | null
  entryMethod: string | null
  entryLow: number | null
  entryHigh: number | null
  structuralInvalidation: number | null
  invalidationReason: string | null
  protectiveStop: number | null
  stopBufferMethod: string | null
  targetMethod: string | null
  targetReference: number | null
  targetRationale: string | null
  expectedRewardRisk: number | null
  entryTriggerPrice: number | null
  noChasePrice: number | null
  noChaseBasis: string | null
  plannedQuantity: number
  approvedQuantityCeiling: number
  quantityIncrement: number | null
  tickSize: number | null
  plannedRisk: number | null
  approvedRiskCeiling: number | null
  plannedNotional: number | null
  approvedNotionalCeiling: number | null
  maximumPlannedLoss: number | null
  expectedCost: number | null
  expectedSlippage: number | null
  createdAt: string
  validFrom: string | null
  expiresAt: string | null
  entryCutoffAt: string | null
  marketRegime: string | null
  sectorCode: string | null
  correlationId: string | null
  featureSchemaVersion: string | null
  policyReference: string | null
  reasonCodes: string[]
  explanation: string | null
  eligibility: TradePlanEligibility
}

export function getTradePlan(
  planKey: string,
  signal?: AbortSignal,
): Promise<TradePlan> {
  return apiGet<TradePlan>(`/api/v1/trade-plans/${planKey}`, { signal })
}

export function getTradePlanByDecision(
  decisionKey: string,
  signal?: AbortSignal,
): Promise<TradePlan> {
  return apiGet<TradePlan>(`/api/v1/trade-plans/by-decision/${decisionKey}`, {
    signal,
  })
}

export function getTradePlansBySetup(
  setupObservationId: number,
  signal?: AbortSignal,
): Promise<TradePlan[]> {
  return apiGet<TradePlan[]>(
    `/api/v1/trade-plans/by-setup/${setupObservationId}`,
    { signal },
  )
}

export const tradePlanKeys = {
  all: ['trade-plans'] as const,
  detail: (planKey: string) =>
    [...tradePlanKeys.all, 'detail', planKey] as const,
  bySetup: (setupObservationId: number) =>
    [...tradePlanKeys.all, 'setup', setupObservationId] as const,
}
