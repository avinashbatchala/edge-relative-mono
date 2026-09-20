import { apiGet } from './http'

const BASE = '/api/v1/features'

/** Timeframes the dashboard exposes. M5 drives the live table; D1 supplies daily context. */
export const FEATURE_TIMEFRAMES = [
  { value: 'M5', label: '5m' },
  { value: 'D1', label: '1D' },
] as const

export interface FeatureDashboardRow {
  instrumentId: number
  instrumentKey: string | null
  symbol: string
  displayName: string | null
  exchange: string
  segment: string | null
  instrumentType: string
  timeframe: string
  observationTime: string | null
  generatedAt: string | null
  lastPrice: number | null
  previousClose: number | null
  priceChange: number | null
  priceChangePercent: number | null
  rrsRaw: number | null
  rrsFast: number | null
  rrsSlow: number | null
  rrsPersistence: number | null
  rrsTrendState: string | null
  dailyRrsState: string | null
  rvolInterval: number | null
  rvolCumulative: number | null
  rve: number | null
  atr: number | null
  atrPercent: number | null
  vwapDistanceAtr: number | null
  marketState: string | null
  sectorState: string | null
  sectorRrsRaw: number | null
  quality: string
  availability: string
  qualityReason: string | null
  staleSeconds: number | null
  featureSchemaVersion: string
  featureVersions: Record<string, string>
  unavailableReasons: Record<string, string>
  /** Per-metric availability state token; distinguishes warming/missing/stale/invalid/not-implemented. */
  unavailableStates: Record<string, string>
}

export interface MetricAvailability {
  metric: string
  state: string
  reason: string
  affectedCount: number
}

export interface TradingImpact {
  status: string
  label: string
  scopeCount: number
  detail: string
}

export interface DiagnosticsFreshness {
  state: string
  sessionContext: string
  newestAgeSeconds: number | null
  oldestAgeSeconds: number | null
  policySeconds: number | null
  asOf: string | null
  basis: string
}

export interface FeatureDiagnosticsResponse {
  generatedAt: string
  engineStatus: string
  timeframe: string
  watchlistCount: number
  healthyCount: number
  stateCounts: Record<string, number>
  metricGaps: Record<string, number>
  latestObservationSeconds: number | null
  oldestObservationSeconds: number | null
  persistenceQueueDepth: number
  persistenceDropped: number
  counters: {
    snapshots: number
    warmupFailures: number
    missingDependencies: number
    alignmentFailures: number
    qualityDowngrades: number
  }
  versions: { featureSchemaVersion: string; calculationVersion: string }
  instruments: {
    instrumentId: number
    symbol: string
    state: string
    reasonCode: string | null
    staleSeconds: number | null
    quality: string
  }[]
  notes: string[]
  calculationMode: string
  tradingImpact: TradingImpact
  freshness: DiagnosticsFreshness
  metricAvailability: MetricAvailability[]
}

export interface FeatureValueResponse {
  featureKey: string
  featureVersion: string
  parameterHash: string
  timeframe: string
  availability: string
  quality: string
  value: number | null
  label: string | null
  lineage: Record<string, string>
}

export interface FeatureSnapshotResponse {
  instrumentId: number
  anchorTimestamp: string
  timeframe: string
  featureSchemaVersion: string
  quality: string
  availability: string
  features: Record<string, FeatureValueResponse>
  market: unknown
  sector: unknown
}

export function getFeatureDashboard(
  signal?: AbortSignal,
  refresh = false,
): Promise<FeatureDashboardRow[]> {
  return apiGet<FeatureDashboardRow[]>(`${BASE}/dashboard`, {
    signal,
    params: { refresh: refresh ? 'true' : undefined },
  })
}

export function getFeatureDiagnostics(
  signal?: AbortSignal,
  refresh = false,
): Promise<FeatureDiagnosticsResponse> {
  return apiGet<FeatureDiagnosticsResponse>(`${BASE}/diagnostics`, {
    signal,
    params: { refresh: refresh ? 'true' : undefined },
  })
}

export function getFeatureSeries(
  instrumentId: number,
  timeframe: string,
  from: string,
  to: string,
  limit = 2000,
  signal?: AbortSignal,
): Promise<FeatureSnapshotResponse[]> {
  return apiGet<FeatureSnapshotResponse[]>(`${BASE}/series`, {
    signal,
    params: { instrumentId, timeframe, from, to, limit },
  })
}

export const featureKeys = {
  all: ['features'] as const,
  dashboard: () => [...featureKeys.all, 'dashboard'] as const,
  diagnostics: () => [...featureKeys.all, 'diagnostics'] as const,
  series: (instrumentId: number, timeframe: string, from: string, to: string) =>
    [...featureKeys.all, 'series', instrumentId, timeframe, from, to] as const,
}
