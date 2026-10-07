import { describe, expect, it } from 'vitest'
import { deriveReadiness } from '@/lib/system-readiness'

describe('deriveReadiness', () => {
  it('is OFFLINE when health is unreachable', () => {
    const result = deriveReadiness({
      health: null,
      stream: 'open',
      freshness: 'FRESH',
      session: 'OPEN',
    })
    expect(result.state).toBe('OFFLINE')
  })

  it('is BLOCKED when feature stream is closed', () => {
    const result = deriveReadiness({
      health: 'UP',
      stream: 'closed',
      freshness: 'FRESH',
      session: 'OPEN',
    })
    expect(result.state).toBe('BLOCKED')
  })

  it('is DEGRADED when market data is stale', () => {
    const result = deriveReadiness({
      health: 'UP',
      stream: 'open',
      freshness: 'STALE',
      session: 'OPEN',
    })
    expect(result.state).toBe('DEGRADED')
    expect(result.reasons).toContain('Market data is stale')
  })

  it('is READY when healthy and fresh in session', () => {
    const result = deriveReadiness({
      health: 'UP',
      stream: 'open',
      freshness: 'FRESH',
      session: 'OPEN',
    })
    expect(result.state).toBe('READY')
  })

  it('is READY but notes a closed market', () => {
    const result = deriveReadiness({
      health: 'UP',
      stream: 'open',
      freshness: 'FRESH',
      session: 'CLOSED',
    })
    expect(result.state).toBe('READY')
    expect(result.headline).toContain('market closed')
  })
})
