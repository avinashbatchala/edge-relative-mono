import type { BrokerInstrument } from '@/api/types'

/** Stable identity for one broker instrument row (broker symbols are not global identity). */
export function instrumentKey(instrument: BrokerInstrument): string {
  return [
    instrument.exchange,
    instrument.segment ?? '',
    instrument.tradingSymbol,
    instrument.brokerSymbol ?? '',
  ].join('|')
}

export function isEquity(instrument: BrokerInstrument): boolean {
  return instrument.instrumentType === 'EQ'
}

export function isIndex(instrument: BrokerInstrument): boolean {
  return instrument.instrumentType === 'IDX'
}

export function isDerivative(instrument: BrokerInstrument): boolean {
  return (
    instrument.instrumentType === 'FUT' ||
    instrument.instrumentType === 'CE' ||
    instrument.instrumentType === 'PE' ||
    instrument.segment === 'FNO'
  )
}

/**
 * The underlying a route should address. Derivatives belong to their underlying so selecting a
 * contract still lands on the underlying workspace.
 */
export function routeSymbolFor(instrument: BrokerInstrument): string {
  if (isDerivative(instrument) && instrument.underlyingSymbol) {
    return instrument.underlyingSymbol
  }
  return instrument.tradingSymbol
}

/**
 * Chooses the primary instrument for an underlying symbol from search results: the cash equity when
 * present, else the index, else the first candidate. Derivatives are intentionally never the primary.
 */
export function pickUnderlying(
  instruments: BrokerInstrument[],
  symbol: string,
): BrokerInstrument | null {
  const wanted = symbol.trim().toUpperCase()
  if (!wanted) {
    return null
  }
  const exact = instruments.filter(
    (instrument) => instrument.tradingSymbol.toUpperCase() === wanted,
  )
  const pool =
    exact.length > 0
      ? exact
      : instruments.filter(
          (instrument) =>
            (instrument.underlyingSymbol ?? '').toUpperCase() === wanted,
        )
  return pool.find(isEquity) ?? pool.find(isIndex) ?? pool[0] ?? null
}
