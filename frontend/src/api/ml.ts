import { apiGet, apiPost } from './http'

export interface MlAnalysisConfig {
  symbols: string[]
  setupTimeframe: 'M5' | 'M15' | 'M30'
  dailyTimeframe: string
  startDate: string
  endDate: string
  marketSymbol?: string
  startingCapital?: number
  strategyPreset?: string
  riskPreset?: string
  seed?: number
  modelCode?: string
  rounds?: number
  thresholds?: { rankIc?: number; ndcg?: number }
}

export interface MlAnalysisRunView {
  id: number
  key: string
  status: string
  requestedBy: string | null
  config: Record<string, unknown>
  progress: Record<string, unknown>
  metrics: Record<string, unknown>
  modelVersionId: number | null
  error: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
}

export interface MlModelVersionView {
  modelVersionId: number
  modelCode: string
  modelName: string | null
  version: number
  lifecycleState: string
  algorithm: string
  artifactUri: string
  artifactChecksum: string | null
  trainingPeriodStart: string | null
  trainingPeriodEnd: string | null
  validationPeriodStart: string | null
  validationPeriodEnd: string | null
  testPeriodStart: string | null
  testPeriodEnd: string | null
  metrics: Record<string, unknown>
  createdAt: string
}

export interface MlBindingView {
  instrumentId: number
  modelVersionId: number
  authorityLevel: string
  lifecycleState: string
  effectiveFrom: string
  effectiveTo: string | null
}

export interface MlSideMetrics {
  completedTrades: number
  wins: number
  netPnl: number
  averageRealizedR: number | null
  stageCounts: Record<string, number>
}

export interface MlVerificationReport {
  baseline: MlSideMetrics
  ranked: MlSideMetrics
  topK: number
}

export interface MlVerificationRequest {
  symbols: string[]
  setupTimeframe: string
  dailyTimeframe: string
  startDate: string
  endDate: string
  marketSymbol?: string
  startingCapital?: number
  riskPreset?: string
  strategyPreset?: string
  seed?: number
  modelVersionId: number
  rankingTopK?: number
}

export function createAnalysisRun(
  config: MlAnalysisConfig,
  signal?: AbortSignal,
): Promise<MlAnalysisRunView> {
  return apiPost<MlAnalysisRunView>(
    '/api/v1/ml/analysis-runs',
    { config, requestedBy: 'workstation' },
    { signal },
  )
}

export function listAnalysisRuns(
  signal?: AbortSignal,
): Promise<MlAnalysisRunView[]> {
  return apiGet<MlAnalysisRunView[]>('/api/v1/ml/analysis-runs', { signal })
}

export function getAnalysisRun(
  key: string,
  signal?: AbortSignal,
): Promise<MlAnalysisRunView> {
  return apiGet<MlAnalysisRunView>(`/api/v1/ml/analysis-runs/${key}`, {
    signal,
  })
}

export function cancelAnalysisRun(
  key: string,
): Promise<{ cancelled: boolean }> {
  return apiPost<{ cancelled: boolean }>(
    `/api/v1/ml/analysis-runs/${key}/cancel`,
    {},
  )
}

export function listModels(
  signal?: AbortSignal,
): Promise<MlModelVersionView[]> {
  return apiGet<MlModelVersionView[]>('/api/v1/ml/models', { signal })
}

export function setModelLifecycle(
  modelVersionId: number,
  lifecycleState: string,
): Promise<{ ok: boolean }> {
  return apiPost<{ ok: boolean }>(
    `/api/v1/ml/models/${modelVersionId}/lifecycle`,
    {
      lifecycleState,
    },
  )
}

export function listBindings(
  instrumentId: number,
  signal?: AbortSignal,
): Promise<MlBindingView[]> {
  return apiGet<MlBindingView[]>(`/api/v1/ml/bindings/${instrumentId}`, {
    signal,
  })
}

export function effectiveBinding(
  instrumentId: number,
  asOf: string,
  signal?: AbortSignal,
): Promise<MlBindingView | null> {
  return apiGet<MlBindingView | null>(
    `/api/v1/ml/bindings/${instrumentId}/effective?asOf=${asOf}`,
    { signal },
  )
}

export function createBinding(request: {
  instrumentId: number
  modelVersionId: number
  authorityLevel?: string
  effectiveFrom: string
  effectiveTo?: string | null
  lifecycleState?: string
  source?: string
}): Promise<{ bindingId: number }> {
  return apiPost<{ bindingId: number }>('/api/v1/ml/bindings', request)
}

export function verifyMl(
  request: MlVerificationRequest,
  signal?: AbortSignal,
): Promise<MlVerificationReport> {
  return apiPost<MlVerificationReport>('/api/v1/ml/verify', request, { signal })
}

export const mlKeys = {
  all: ['ml'] as const,
  runs: () => [...mlKeys.all, 'runs'] as const,
  run: (key: string) => [...mlKeys.all, 'run', key] as const,
  models: () => [...mlKeys.all, 'models'] as const,
  bindings: (instrumentId: number) =>
    [...mlKeys.all, 'bindings', instrumentId] as const,
}
