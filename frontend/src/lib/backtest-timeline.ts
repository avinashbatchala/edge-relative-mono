import type { BacktestTimelinePoint, BacktestTradeRow } from '@/api/backtests'
import type { BrokerCandle } from '@/api/types'
import type { ChartMarker } from '@/lib/chart-markers'

export function timelineCandles(
  points: BacktestTimelinePoint[],
): BrokerCandle[] {
  return points.map((point) => ({
    openTime: point.at,
    open: point.open,
    high: point.high,
    low: point.low,
    close: point.close,
    volume: point.volume,
    openInterest: null,
  }))
}

function epochSeconds(iso: string): number {
  return Math.floor(Date.parse(iso) / 1000)
}

export interface TimelineOverlay {
  key: string
  title: string
  color: string
  baseline: number | null
  points: { time: number; value: number | null }[]
}

export function timelineOverlays(
  points: BacktestTimelinePoint[],
): TimelineOverlay[] {
  const build = (
    key: string,
    title: string,
    color: string,
    baseline: number | null,
    read: (point: BacktestTimelinePoint) => number | null,
  ): TimelineOverlay => ({
    key,
    title,
    color,
    baseline,
    points: points.map((point) => ({
      time: epochSeconds(point.at),
      value: read(point),
    })),
  })
  return [
    build('rrs', 'RRS', '#2563eb', 0, (p) => p.rrsRaw),
    build('rvol', 'RVOL', '#d97706', 1, (p) => p.rvolInterval),
    build('rve', 'RVE', '#7c3aed', 0, (p) => p.rve),
  ]
}

const STATE_LABELS: Record<string, { color: string; text: string }> = {
  WATCH: { color: '#64748b', text: 'WATCH' },
  FORMING: { color: '#0ea5e9', text: 'FORMING' },
  NEAR_TRIGGER: { color: '#f59e0b', text: 'NEAR' },
  VALID: { color: '#15803d', text: 'VALID' },
  INVALIDATED: { color: '#b91c1c', text: 'INVALID' },
  EXPIRED: { color: '#a16207', text: 'EXPIRED' },
  MISSED: { color: '#a16207', text: 'MISSED' },
}

export function timelineMarkers(
  points: BacktestTimelinePoint[],
  trades: BacktestTradeRow[],
  direction: 'long' | 'short',
): ChartMarker[] {
  const markers: ChartMarker[] = []
  let previous: string | null = null
  for (const point of points) {
    const state = direction === 'long' ? point.longState : point.shortState
    if (state && state !== previous && STATE_LABELS[state]) {
      markers.push({
        time: point.at,
        position: 'inBar',
        color: STATE_LABELS[state].color,
        shape: 'square',
        text: STATE_LABELS[state].text,
      })
    }
    previous = state
  }
  for (const trade of trades) {
    const long = trade.direction === 'LONG'
    markers.push({
      time: trade.entryAt,
      position: long ? 'belowBar' : 'aboveBar',
      color: long ? '#15803d' : '#b91c1c',
      shape: long ? 'arrowUp' : 'arrowDown',
      text: `Entry ${trade.direction}`,
    })
    if (trade.exitAt) {
      markers.push({
        time: trade.exitAt,
        position: long ? 'aboveBar' : 'belowBar',
        color: '#525252',
        shape: 'circle',
        text: `Exit ${trade.exitReason ?? ''}`.trim(),
      })
    }
  }
  return markers.sort((a, b) => Date.parse(a.time) - Date.parse(b.time))
}
