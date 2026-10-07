import { describe, expect, it } from 'vitest'
import { backtestFunnel, funnelReasons } from '@/lib/backtest-funnel'

describe('backtestFunnel', () => {
  it('orders stages and computes eliminations', () => {
    const stages = backtestFunnel({
      anchorsProcessed: 1000,
      setup_WATCH: 400,
      setup_FORMING: 200,
      setup_NEAR_TRIGGER: 100,
      setup_VALID: 40,
      risk_APPROVE: 30,
      risk_REJECT: 10,
      plansCreated: 30,
      ordersSubmitted: 30,
      fills: 28,
      exits: 27,
    })
    const byKey = Object.fromEntries(stages.map((s) => [s.key, s]))
    expect(byKey.anchorsProcessed?.count).toBe(1000)
    expect(byKey.setup_WATCH?.eliminatedFromPrevious).toBe(600)
    expect(byKey.riskEvaluated?.count).toBe(40)
    expect(byKey.riskApproved?.count).toBe(30)
    expect(byKey.exits?.eliminatedFromPrevious).toBe(1)
  })

  it('handles a zero-trade run without inventing counts', () => {
    const stages = backtestFunnel({ anchorsProcessed: 500, setup_WATCH: 50 })
    const byKey = Object.fromEntries(stages.map((s) => [s.key, s]))
    expect(byKey.setup_WATCH?.count).toBe(50)
    expect(byKey.setup_VALID?.count).toBe(0)
    expect(byKey.riskEvaluated?.count).toBe(0)
    expect(byKey.exits?.count).toBe(0)
  })
})

describe('funnelReasons', () => {
  it('sums long and short reason codes and sorts descending', () => {
    const reasons = funnelReasons({
      longReason_RRS_M5_FAILED: 5,
      shortReason_RRS_M5_FAILED: 3,
      longReason_RVOL_FAILED: 2,
    })
    expect(reasons[0]).toEqual({ code: 'RRS_M5_FAILED', count: 8 })
    expect(reasons[1]).toEqual({ code: 'RVOL_FAILED', count: 2 })
  })
})
