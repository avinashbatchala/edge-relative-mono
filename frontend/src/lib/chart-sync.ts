import type { LogicalRange, UTCTimestamp } from 'lightweight-charts'

/**
 * Minimal time-axis synchronisation for lightweight-charts instances in the same view.
 *
 * <p>Charts that share a key mirror each other's visible logical range (zoom/pan) and crosshair
 * position. The module is intentionally tiny: components register imperative handlers and broadcast
 * their own changes; the module only fans out to peers, never touching chart objects itself.
 */
export interface ChartSyncHandlers {
  applyRange: (range: LogicalRange) => void
  applyCrosshair: (time: UTCTimestamp | null) => void
}

const groups = new Map<string, Map<string, ChartSyncHandlers>>()
let counter = 0

export function createChartSyncMemberId(): string {
  counter += 1
  return `chart-${counter}`
}

export function joinChartSync(
  key: string,
  id: string,
  handlers: ChartSyncHandlers,
): () => void {
  const group = groups.get(key) ?? new Map<string, ChartSyncHandlers>()
  group.set(id, handlers)
  groups.set(key, group)
  return () => {
    group.delete(id)
    if (group.size === 0) {
      groups.delete(key)
    }
  }
}

export function broadcastLogicalRange(
  key: string,
  sourceId: string,
  range: LogicalRange,
): void {
  const group = groups.get(key)
  if (!group) {
    return
  }
  for (const [id, handlers] of group) {
    if (id !== sourceId) {
      handlers.applyRange(range)
    }
  }
}

export function broadcastCrosshair(
  key: string,
  sourceId: string,
  time: UTCTimestamp | null,
): void {
  const group = groups.get(key)
  if (!group) {
    return
  }
  for (const [id, handlers] of group) {
    if (id !== sourceId) {
      handlers.applyCrosshair(time)
    }
  }
}
