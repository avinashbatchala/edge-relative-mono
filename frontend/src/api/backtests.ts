import { apiGet, apiPost } from './http'

export interface BacktestRun {
  runKey: string
  experimentRunId: number
  backtestRunId: number
  status: string
  strategyId: string | null
  strategyVersion: string | null
  datasetCode: string | null
  datasetChecksum: string | null
  startDate: string | null
  endDate: string | null
  universeSize: number
  startingCapital: number | null
  currency: string | null
  progressEvents: number
  progressTotal: number | null
  progressThrough: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  metrics: Record<string, unknown>
  failure: Record<string, unknown>
  parameters: Record<string, unknown>
}

export interface BacktestTradeRow {
  tradeKey: string
  instrumentId: number
  symbol: string
  direction: string
  entryPattern: string | null
  entryAt: string
  entryPrice: number
  exitAt: string | null
  exitPrice: number | null
  quantity: number
  grossPnl: number
  explicitCosts: number
  netPnl: number
  realizedR: number | null
  holdingSeconds: number | null
  exitReason: string | null
  ambiguousBars: number
  costBreakdown: Record<string, number>
  planKey: string | null
  decisionKey: string | null
}

export interface BacktestEquityPoint {
  at: string
  equity: number
  cash: number
  grossExposure: number
  netExposure: number
  highWater: number
  drawdown: number
  drawdownPct: number | null
  openPositions: number
}

export interface BacktestRejectionRow {
  at: string
  instrumentId: number
  direction: string
  reasonCode: string
  detail: string | null
}

export interface BacktestTimelinePoint {
  at: string
  open: number | null
  high: number
  low: number
  close: number
  volume: number
  rrsRaw: number | null
  rrsFast: number | null
  rrsSlow: number | null
  rrsPersistence: number | null
  rrsSlope: number | null
  rrsAcceleration: number | null
  rrsPercentile: number | null
  rvolDaily: number | null
  rvolInterval: number | null
  rvolCumulative: number | null
  rve: number | null
  atr: number | null
  marketStructure: string | null
  marketEfficiency: number | null
  sectorRrs: number | null
  sectorStructure: string | null
  longState: string | null
  shortState: string | null
  longReasons: string[]
  shortReasons: string[]
}

export interface BacktestTimeline {
  instrumentId: number
  symbol: string
  points: BacktestTimelinePoint[]
  trades: BacktestTradeRow[]
}

export function getBacktestTimeline(
  runKey: string,
  instrumentId: number,
  signal?: AbortSignal,
): Promise<BacktestTimeline> {
  return apiGet<BacktestTimeline>(
    `/api/v1/backtests/${runKey}/instruments/${instrumentId}/timeline`,
    { signal },
  )
}

export interface BacktestSymbolStat {
  symbol: string
  completed: number
  wins: number
  net: number
  costs: number
  total: number
}

export interface BacktestAggregate {
  symbols: BacktestSymbolStat[]
  totalTrades: number
  completedTrades: number
  openPositions: number
}

export function getBacktestAggregate(
  runKey: string,
  signal?: AbortSignal,
): Promise<BacktestAggregate> {
  return apiGet<BacktestAggregate>(`/api/v1/backtests/${runKey}/aggregate`, {
    signal,
  })
}

export function getBacktestRuns(signal?: AbortSignal): Promise<BacktestRun[]> {
  return apiGet<BacktestRun[]>('/api/v1/backtests', {
    signal,
    params: { limit: 50 },
  })
}

export function getBacktestRun(
  runKey: string,
  signal?: AbortSignal,
): Promise<BacktestRun> {
  return apiGet<BacktestRun>(`/api/v1/backtests/${runKey}`, { signal })
}

export function getBacktestTrades(
  runKey: string,
  symbol?: string,
  signal?: AbortSignal,
  offset = 0,
): Promise<BacktestTradeRow[]> {
  return apiGet<BacktestTradeRow[]>(`/api/v1/backtests/${runKey}/trades`, {
    signal,
    params: { symbol: symbol || undefined, limit: 200, offset },
  })
}

export function getBacktestEquity(
  runKey: string,
  signal?: AbortSignal,
): Promise<BacktestEquityPoint[]> {
  return apiGet<BacktestEquityPoint[]>(`/api/v1/backtests/${runKey}/equity`, {
    signal,
  })
}

export interface BacktestUniverseEntry {
  instrumentId: number
  symbol: string
}

export function getBacktestUniverse(
  runKey: string,
  signal?: AbortSignal,
): Promise<BacktestUniverseEntry[]> {
  return apiGet<BacktestUniverseEntry[]>(
    `/api/v1/backtests/${runKey}/universe`,
    { signal },
  )
}

export function getBacktestRejections(
  runKey: string,
  signal?: AbortSignal,
): Promise<BacktestRejectionRow[]> {
  return apiGet<BacktestRejectionRow[]>(
    `/api/v1/backtests/${runKey}/rejections`,
    {
      signal,
    },
  )
}

export function startBacktest(body: unknown): Promise<BacktestRun> {
  return apiPost<BacktestRun>('/api/v1/backtests', body)
}

export function cancelBacktest(runKey: string): Promise<unknown> {
  return apiPost(`/api/v1/backtests/${runKey}/cancel`, {})
}

export const backtestKeys = {
  all: ['backtests'] as const,
  list: () => [...backtestKeys.all, 'list'] as const,
  detail: (runKey: string) => [...backtestKeys.all, 'detail', runKey] as const,
  trades: (runKey: string, symbol: string) =>
    [...backtestKeys.all, 'trades', runKey, symbol] as const,
  equity: (runKey: string) => [...backtestKeys.all, 'equity', runKey] as const,
  universe: (runKey: string) =>
    [...backtestKeys.all, 'universe', runKey] as const,
  rejections: (runKey: string) =>
    [...backtestKeys.all, 'rejections', runKey] as const,
  timeline: (runKey: string, instrumentId: number) =>
    [...backtestKeys.all, 'timeline', runKey, instrumentId] as const,
  aggregate: (runKey: string) =>
    [...backtestKeys.all, 'aggregate', runKey] as const,
}
