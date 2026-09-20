import { describe, expect, test, vi } from 'vitest'
import {
  broadcastCrosshair,
  broadcastLogicalRange,
  createChartSyncMemberId,
  joinChartSync,
} from './chart-sync'

describe('chart time-axis sync', () => {
  test('mirrors the visible range to peers but not the source', () => {
    const key = `k-${createChartSyncMemberId()}`
    const a = { applyRange: vi.fn(), applyCrosshair: vi.fn() }
    const b = { applyRange: vi.fn(), applyCrosshair: vi.fn() }
    const leaveA = joinChartSync(key, 'a', a)
    joinChartSync(key, 'b', b)

    const range = { from: 1, to: 10 } as never
    broadcastLogicalRange(key, 'a', range)

    expect(b.applyRange).toHaveBeenCalledWith(range)
    expect(a.applyRange).not.toHaveBeenCalled()
    leaveA()
  })

  test('mirrors the crosshair time and clears on leave', () => {
    const key = `k-${createChartSyncMemberId()}`
    const a = { applyRange: vi.fn(), applyCrosshair: vi.fn() }
    const b = { applyRange: vi.fn(), applyCrosshair: vi.fn() }
    joinChartSync(key, 'a', a)
    const leaveB = joinChartSync(key, 'b', b)

    broadcastCrosshair(key, 'a', 123 as never)
    expect(a.applyCrosshair).not.toHaveBeenCalled()
    expect(b.applyCrosshair).toHaveBeenCalledWith(123)

    leaveB()
    broadcastCrosshair(key, 'a', 456 as never)
    expect(b.applyCrosshair).toHaveBeenCalledTimes(1)
  })
})
