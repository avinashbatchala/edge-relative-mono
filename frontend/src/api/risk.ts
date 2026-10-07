import { apiGet } from './http'

export interface RiskPortfolio {
  snapshotAt: string
  tradingDate: string
  netLiquidationValue: number
  availableCash: number | null
  buyingPower: number | null
  marginUsed: number | null
  grossExposure: number
  netExposure: number
  openRisk: number
  stressOpenRisk: number
  realizedSessionPnl: number
  unrealizedPnl: number
  openPositions: number
}

export interface RiskAccount {
  tradingDate: string
  riskState: string
  riskReferenceEquity: number
  currentNetLiquidationValue: number
  reservedRisk: number
  reservedNotional: number
  openRisk: number
  stressOpenRisk: number
  grossExposure: number
  netExposure: number
  realizedSessionPnl: number
  unrealizedPnl: number
  sessionDrawdown: number
  updatedAt: string
}

export interface RiskControls {
  present: boolean
  stopNewTrades: boolean
  cancelPendingEntries: boolean
  flattenOnly: boolean
  automationEnabled: boolean
  executionEnabled: boolean
  reason: string | null
  updatedBy: string | null
  updatedAt: string | null
}

export interface RiskCounts {
  openTrades: number
  activeReservations: number
  pendingOrders: number
}

/** Authoritative read-only risk posture (`/api/v1/risk/posture`). */
export interface RiskPostureResponse {
  asOf: string
  portfolioAvailable: boolean
  portfolio: RiskPortfolio | null
  accountRiskAvailable: boolean
  account: RiskAccount | null
  controls: RiskControls
  counts: RiskCounts
  unavailable: string[]
}

export function getRiskPosture(
  signal?: AbortSignal,
): Promise<RiskPostureResponse> {
  return apiGet<RiskPostureResponse>('/api/v1/risk/posture', { signal })
}

export const riskKeys = {
  all: ['risk'] as const,
  posture: () => [...riskKeys.all, 'posture'] as const,
}
