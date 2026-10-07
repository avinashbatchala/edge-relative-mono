import type { FeatureDashboardRow } from '@/api/features'
import type { WhySection, WhyTone } from '@/lib/why'

function num(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : value.toFixed(2)
}

function tone(value: number | null | undefined): WhyTone {
  if (value === null || value === undefined) {
    return 'muted'
  }
  return value > 0 ? 'positive' : value < 0 ? 'negative' : 'default'
}

/**
 * Deterministic "why" explanation for a feature-board row. Every line is derived from the
 * measured values the backend already returned; nothing here is inferred or predicted.
 */
export function featureWhySections(row: FeatureDashboardRow): WhySection[] {
  return [
    {
      title: 'Relative strength',
      items: [
        { label: `RRS ${num(row.rrsRaw)}`, tone: tone(row.rrsRaw) },
        { label: `Daily ${row.dailyRrsState ?? '—'}` },
        { label: `Trend ${row.rrsTrendState ?? '—'}` },
        { label: `Persistence ${num(row.rrsPersistence)}` },
      ],
    },
    {
      title: 'Participation',
      items: [
        { label: `RVOL interval ${num(row.rvolInterval)}` },
        { label: `RVOL cumulative ${num(row.rvolCumulative)}` },
        { label: `RVE ${num(row.rve)}`, tone: tone(row.rve) },
      ],
    },
    {
      title: 'Context',
      items: [
        { label: `Market ${row.marketState ?? '—'}` },
        { label: `Sector ${row.sectorState ?? '—'}` },
        {
          label: `Sector RRS ${num(row.sectorRrsRaw)}`,
          tone: tone(row.sectorRrsRaw),
        },
      ],
    },
    {
      title: 'Volatility & location',
      items: [
        { label: `ATR % ${num(row.atrPercent)}` },
        { label: `VWAP distance ${num(row.vwapDistanceAtr)}` },
      ],
    },
    {
      title: 'Data quality',
      items: [
        {
          label: `Quality ${row.quality}`,
          tone:
            row.quality === 'GOOD'
              ? 'positive'
              : row.quality === 'UNAVAILABLE'
                ? 'negative'
                : 'muted',
        },
        { label: `Availability ${row.availability}` },
        {
          label: `Freshness ${
            row.staleSeconds === null ? '—' : `${row.staleSeconds}s`
          }`,
          tone: row.staleSeconds === null ? 'muted' : 'default',
        },
      ],
    },
  ]
}
