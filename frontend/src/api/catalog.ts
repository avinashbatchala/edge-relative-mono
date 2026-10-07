import { apiGet, apiPost } from './http'

export interface StrategyVersionView {
  strategyVersionId: number
  version: number
  lifecycleState: string
  primaryTimeframe: string | null
  featureSchemaVersionId: number | null
  parameters: Record<string, unknown> | null
  parametersError: string | null
  createdAt: string | null
}

export interface StrategyView {
  strategyId: number
  code: string
  name: string
  description: string | null
  setupFamily: string | null
  status: string
  retiredAt: string | null
  retiredReason: string | null
  createdAt: string | null
  versions: StrategyVersionView[]
}

export interface RiskPolicyVersionView {
  riskPolicyVersionId: number
  version: number
  lifecycleState: string
  parameters: Record<string, unknown> | null
  parametersError: string | null
  createdAt: string | null
}

export interface RiskPolicyView {
  riskPolicyId: number
  code: string
  name: string
  description: string | null
  status: string
  retiredAt: string | null
  retiredReason: string | null
  createdAt: string | null
  versions: RiskPolicyVersionView[]
}

export function getStrategies(
  includeRetired = true,
  signal?: AbortSignal,
): Promise<StrategyView[]> {
  return apiGet<StrategyView[]>('/api/v1/strategies', {
    signal,
    params: { includeRetired },
  })
}

export function getStrategyTemplate(): Promise<Record<string, unknown>> {
  return apiGet<Record<string, unknown>>(
    '/api/v1/strategies/templates/parameters',
  )
}

export function createStrategy(body: unknown): Promise<StrategyView> {
  return apiPost<StrategyView>('/api/v1/strategies', body)
}

export function addStrategyVersion(
  code: string,
  body: unknown,
): Promise<StrategyView> {
  return apiPost<StrategyView>(
    `/api/v1/strategies/${encodeURIComponent(code)}/versions`,
    body,
  )
}

export function retireStrategy(
  code: string,
  reason?: string,
): Promise<StrategyView> {
  return apiPost<StrategyView>(
    `/api/v1/strategies/${encodeURIComponent(code)}/retire`,
    {
      reason,
    },
  )
}

export function restoreStrategy(code: string): Promise<StrategyView> {
  return apiPost<StrategyView>(
    `/api/v1/strategies/${encodeURIComponent(code)}/restore`,
    {},
  )
}

export function getRiskPolicies(
  includeRetired = true,
  signal?: AbortSignal,
): Promise<RiskPolicyView[]> {
  return apiGet<RiskPolicyView[]>('/api/v1/risk-policies', {
    signal,
    params: { includeRetired },
  })
}

export function getRiskPolicyTemplate(): Promise<Record<string, unknown>> {
  return apiGet<Record<string, unknown>>(
    '/api/v1/risk-policies/templates/parameters',
  )
}

export function createRiskPolicy(body: unknown): Promise<RiskPolicyView> {
  return apiPost<RiskPolicyView>('/api/v1/risk-policies', body)
}

export function addRiskPolicyVersion(
  code: string,
  body: unknown,
): Promise<RiskPolicyView> {
  return apiPost<RiskPolicyView>(
    `/api/v1/risk-policies/${encodeURIComponent(code)}/versions`,
    body,
  )
}

export function retireRiskPolicy(
  code: string,
  reason?: string,
): Promise<RiskPolicyView> {
  return apiPost<RiskPolicyView>(
    `/api/v1/risk-policies/${encodeURIComponent(code)}/retire`,
    {
      reason,
    },
  )
}

export function restoreRiskPolicy(code: string): Promise<RiskPolicyView> {
  return apiPost<RiskPolicyView>(
    `/api/v1/risk-policies/${encodeURIComponent(code)}/restore`,
    {},
  )
}

export const catalogKeys = {
  all: ['catalog'] as const,
  strategies: () => [...catalogKeys.all, 'strategies'] as const,
  activeStrategies: () => [...catalogKeys.all, 'strategies', 'active'] as const,
  riskPolicies: () => [...catalogKeys.all, 'risk-policies'] as const,
  activeRiskPolicies: () =>
    [...catalogKeys.all, 'risk-policies', 'active'] as const,
}
