import type { FundamentalMetric, FundamentalResponse } from '@/api/fundamentals'
import {
  formatCompact,
  formatInr,
  formatPercent,
  formatPrice,
} from '@/lib/format'

const DASH = '—'

const METRIC_LABELS: Record<string, string> = {
  trailing_pe: 'P/E (trailing)',
  forward_pe: 'P/E (forward)',
  price_to_book: 'P/B',
  trailing_eps: 'EPS (trailing)',
  dividend_yield: 'Dividend yield',
  return_on_equity: 'Return on equity',
  debt_to_equity: 'Debt / equity',
  revenue_growth: 'Revenue growth',
  earnings_growth: 'Earnings growth',
  profit_margin: 'Net margin',
  market_cap: 'Market cap',
  target_mean_price: 'Mean target price',
}

const LINE_LABELS: Record<string, string> = {
  total_revenue: 'Total revenue',
  gross_profit: 'Gross profit',
  operating_income: 'Operating income',
  net_income: 'Net income',
}

/** Fraction-valued metrics rendered as percentages. Multiples such as P/E stay as numbers. */
const PERCENT_METRICS = new Set([
  'dividend_yield',
  'return_on_equity',
  'revenue_growth',
  'earnings_growth',
  'profit_margin',
])

function humanize(code: string): string {
  const spaced = code.replace(/_/g, ' ')
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

export function metricLabel(code: string): string {
  return METRIC_LABELS[code] ?? humanize(code)
}

export function lineLabel(lineCode: string, label: string | null): string {
  return LINE_LABELS[lineCode] ?? label ?? humanize(lineCode)
}

function toNumber(value: string): number | null {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : null
}

/** Presentation only. The authoritative value is the decimal string from the backend. */
export function formatMetricValue(metric: FundamentalMetric): string {
  const numeric = toNumber(metric.value)
  if (numeric === null) {
    return DASH
  }
  if (PERCENT_METRICS.has(metric.metricCode)) {
    return formatPercent(numeric * 100)
  }
  if (metric.metricCode === 'market_cap') {
    return `₹${formatCompact(numeric)}`
  }
  if (metric.unit === 'INR') {
    return formatInr(numeric)
  }
  return formatPrice(numeric)
}

export function formatStatementValue(unit: string, value: string): string {
  const numeric = toNumber(value)
  if (numeric === null) {
    return DASH
  }
  return unit === 'INR' ? `₹${formatCompact(numeric)}` : formatPrice(numeric)
}

/**
 * Build the code-computed facts passed to the advisory narrator. Only numbers already computed by
 * the backend are included; the model is never asked to derive them.
 */
export function buildNarrationFacts(fundamentals: FundamentalResponse): string {
  const lines = [
    `${fundamentals.symbol} (${fundamentals.exchange}) fundamentals`,
    `Period: ${fundamentals.fiscalYear} ${fundamentals.periodType} ` +
      `(${fundamentals.reportingBasis}), ending ${fundamentals.periodEnd}`,
    `Source: ${fundamentals.provider}; filed/observed ${fundamentals.filedAt}; as-of ${fundamentals.asOf}`,
    'Statement lines:',
    ...fundamentals.statements.map(
      (line) =>
        `- ${line.lineCode}: ${line.value} ${line.unit} (scale ${line.scale})`,
    ),
    'Metrics:',
    ...fundamentals.metrics.map(
      (metric) => `- ${metric.metricCode}: ${metric.value} ${metric.unit}`,
    ),
  ]
  return lines.join('\n')
}

export const DEFAULT_NARRATION_QUESTION =
  'Summarise what these fundamentals show for this company in plain language.'
