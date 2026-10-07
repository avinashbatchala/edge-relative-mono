import { apiGet } from './http'

const BASE = '/api/v1/fundamentals'

export interface FundamentalStatementLine {
  lineCode: string
  label: string | null
  value: string
  unit: string
  scale: string
}

export interface FundamentalMetric {
  metricCode: string
  value: string
  unit: string
  decimals: number | null
}

/** Advisory point-in-time fundamentals. `advisory` is always true (DD-06 §2). */
export interface FundamentalResponse {
  advisory: boolean
  instrumentId: number
  exchange: string
  symbol: string
  provider: string
  sourceRevision: string
  asOf: string
  filedAt: string
  fiscalYear: string
  periodType: string
  reportingBasis: string
  periodEnd: string
  statements: FundamentalStatementLine[]
  metrics: FundamentalMetric[]
}

export interface FundamentalsOptions {
  asOf?: string
  refresh?: boolean
  signal?: AbortSignal
}

export function getFundamentals(
  instrumentId: number,
  options: FundamentalsOptions = {},
): Promise<FundamentalResponse> {
  const asOf = options.asOf ?? new Date().toISOString()
  return apiGet<FundamentalResponse>(`${BASE}/${instrumentId}`, {
    signal: options.signal,
    params: {
      asOf,
      refresh: options.refresh ? 'true' : undefined,
    },
  })
}

export const fundamentalKeys = {
  all: ['fundamentals'] as const,
  detail: (instrumentId: number, asOf: string, refresh: boolean) =>
    [...fundamentalKeys.all, instrumentId, asOf, refresh] as const,
}
