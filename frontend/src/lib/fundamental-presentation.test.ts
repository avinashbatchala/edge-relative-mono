import { expect, test } from 'vitest'
import type { FundamentalMetric, FundamentalResponse } from '@/api/fundamentals'
import {
  buildNarrationFacts,
  formatMetricValue,
  formatStatementValue,
  lineLabel,
  metricLabel,
} from './fundamental-presentation'

function metric(overrides: Partial<FundamentalMetric>): FundamentalMetric {
  return {
    metricCode: 'trailing_pe',
    value: '25.0',
    unit: 'ratio',
    decimals: null,
    ...overrides,
  }
}

function response(
  overrides: Partial<FundamentalResponse> = {},
): FundamentalResponse {
  return {
    advisory: true,
    instrumentId: 7,
    exchange: 'NSE',
    symbol: 'RELIANCE',
    provider: 'yahoo-nse',
    sourceRevision: 'rev-1',
    asOf: '2026-04-01T00:00:00Z',
    filedAt: '2026-04-01T00:00:00Z',
    fiscalYear: 'FY2025',
    periodType: 'ANNUAL',
    reportingBasis: 'CONSOLIDATED',
    periodEnd: '2025-03-31',
    statements: [
      {
        lineCode: 'total_revenue',
        label: null,
        value: '9000000000000',
        unit: 'INR',
        scale: 'unit',
      },
    ],
    metrics: [metric({})],
    ...overrides,
  }
}

test('labels known metrics and humanises unknown codes', () => {
  expect(metricLabel('trailing_pe')).toBe('P/E (trailing)')
  expect(metricLabel('price_to_book')).toBe('P/B')
  expect(metricLabel('some_new_ratio')).toBe('Some new ratio')
  expect(lineLabel('total_revenue', null)).toBe('Total revenue')
  expect(lineLabel('weird_code', 'Custom label')).toBe('Custom label')
})

test('formats a fraction metric as a percentage and a multiple as a number', () => {
  expect(
    formatMetricValue(
      metric({ metricCode: 'return_on_equity', value: '0.15' }),
    ),
  ).toBe('+15.00%')
  expect(
    formatMetricValue(metric({ metricCode: 'trailing_pe', value: '25.0' })),
  ).toBe('25.00')
  expect(
    formatMetricValue(
      metric({ metricCode: 'trailing_eps', value: '100.0', unit: 'INR' }),
    ),
  ).toBe('₹100.00')
  expect(
    formatMetricValue(
      metric({ metricCode: 'market_cap', value: '2000000000000', unit: 'INR' }),
    ).startsWith('₹'),
  ).toBe(true)
})

test('formats statement values compactly and handles missing numbers', () => {
  expect(formatStatementValue('INR', '9000000000000').startsWith('₹')).toBe(
    true,
  )
  expect(formatStatementValue('ratio', '1.5')).toBe('1.50')
  expect(formatStatementValue('INR', 'not-a-number')).toBe('—')
})

test('builds narration facts from already-computed values', () => {
  const facts = buildNarrationFacts(response())
  expect(facts).toContain('RELIANCE (NSE)')
  expect(facts).toContain('FY2025 ANNUAL (CONSOLIDATED)')
  expect(facts).toContain('total_revenue: 9000000000000 INR')
  expect(facts).toContain('trailing_pe: 25.0 ratio')
})
