import { describe, expect, test } from 'vitest'
import {
  isDerivative,
  isEquity,
  isIndex,
  pickUnderlying,
  routeSymbolFor,
} from './instrument'
import {
  INFY,
  RELIANCE,
  instrument,
  relianceCall,
} from '@/test/market-fixtures'

describe('instrument classification', () => {
  test('distinguishes equities, indices and derivatives', () => {
    expect(isEquity(RELIANCE)).toBe(true)
    expect(isIndex(instrument({ instrumentType: 'IDX' }))).toBe(true)
    expect(isDerivative(relianceCall())).toBe(true)
  })
})

describe('routeSymbolFor', () => {
  test('routes derivatives to their underlying', () => {
    expect(routeSymbolFor(relianceCall())).toBe('RELIANCE')
    expect(routeSymbolFor(instrument({ instrumentType: 'FUT' }))).toBe(
      'RELIANCE',
    )
  })

  test('routes cash instruments to themselves', () => {
    expect(routeSymbolFor(RELIANCE)).toBe('RELIANCE')
    expect(routeSymbolFor(INFY)).toBe('INFY')
  })
})

describe('pickUnderlying', () => {
  test('prefers the cash equity over derivatives of the same underlying', () => {
    const results = [
      relianceCall(),
      instrument({ instrumentType: 'FUT' }),
      RELIANCE,
    ]
    expect(pickUnderlying(results, 'RELIANCE')?.tradingSymbol).toBe('RELIANCE')
    expect(pickUnderlying(results, 'reliance')?.instrumentType).toBe('EQ')
  })

  test('falls back to an index', () => {
    const index = instrument({
      tradingSymbol: 'NIFTY',
      instrumentType: 'IDX',
      name: 'Nifty 50',
    })
    expect(pickUnderlying([index], 'NIFTY')?.tradingSymbol).toBe('NIFTY')
  })

  test('returns null when nothing matches', () => {
    expect(pickUnderlying([RELIANCE], 'TCS')).toBeNull()
    expect(pickUnderlying([], 'RELIANCE')).toBeNull()
  })
})
