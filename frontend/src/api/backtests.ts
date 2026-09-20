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
): Promise<BacktestTradeRow[]> {
  return apiGet<BacktestTradeRow[]>(`/api/v1/backtests/${runKey}/trades`, {
    signal,
    params: { symbol: symbol || undefined, limit: 200 },
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
}
