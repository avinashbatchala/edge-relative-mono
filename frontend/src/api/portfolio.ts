import { apiGet } from './http'
import type { BrokerSegment } from './types'

const BASE = '/api/v1/brokers/groww'

/** Read-only broker account models, mirroring the backend `broker-api` records. */

export interface BrokerPosition {
  tradingSymbol: string
  isin: string | null
  exchange: string
  segment: string
  product: string
  quantity: number
  netPrice: number | null
  creditQuantity: number
  creditPrice: number | null
  debitQuantity: number
  debitPrice: number | null
  carryForwardCreditQuantity: number
  carryForwardCreditPrice: number | null
  carryForwardDebitQuantity: number
  carryForwardDebitPrice: number | null
  netCarryForwardQuantity: number
  netCarryForwardPrice: number | null
  realisedPnl: number | null
}

export interface BrokerHolding {
  isin: string
  tradingSymbol: string
  quantity: number
  averagePrice: number | null
  pledgeQuantity: number | null
  dematLockedQuantity: number | null
  brokerLockedQuantity: number | null
  repledgeQuantity: number | null
  t1Quantity: number | null
  dematFreeQuantity: number | null
  corporateActionAdditionalQuantity: number
  activeDematTransferQuantity: number
}

export interface BrokerOrder {
  brokerOrderId: string | null
  orderReferenceId: string | null
  tradingSymbol: string
  exchange: string
  segment: string
  transactionType: string
  orderType: string
  product: string
  validity: string
  status: string
  quantity: number
  price: number | null
  triggerPrice: number | null
  filledQuantity: number
  remainingQuantity: number
  averageFillPrice: number | null
  deliverableQuantity: number
  remark: string | null
  createdAt: string | null
  exchangeTime: string | null
  tradeDate: string | null
}

export interface BrokerMargin {
  clearCash: number | null
  netMarginUsed: number | null
  brokerageAndCharges: number | null
  collateralUsed: number | null
  collateralAvailable: number | null
  adhocMargin: number | null
  fno: {
    netMarginUsed: number | null
    spanMarginUsed: number | null
    exposureMarginUsed: number | null
    futureBalanceAvailable: number | null
    optionBuyBalanceAvailable: number | null
    optionSellBalanceAvailable: number | null
  } | null
  equity: {
    netEquityMarginUsed: number | null
    cncMarginUsed: number | null
    misMarginUsed: number | null
    cncBalanceAvailable: number | null
    misBalanceAvailable: number | null
  } | null
}

export interface BrokerUserProfile {
  userId: string
  uniqueClientCode: string
  nseEnabled: boolean
  bseEnabled: boolean
  ddpiEnabled: boolean
  activeSegments: string[]
}

export function getHoldings(signal?: AbortSignal): Promise<BrokerHolding[]> {
  return apiGet<BrokerHolding[]>(`${BASE}/portfolio/holdings`, { signal })
}

export function getPositions(
  segment?: BrokerSegment,
  signal?: AbortSignal,
): Promise<BrokerPosition[]> {
  return apiGet<BrokerPosition[]>(`${BASE}/portfolio/positions`, {
    signal,
    params: { segment },
  })
}

export function getUserProfile(
  signal?: AbortSignal,
): Promise<BrokerUserProfile> {
  return apiGet<BrokerUserProfile>(`${BASE}/portfolio/user`, { signal })
}

export function getUserMargin(signal?: AbortSignal): Promise<BrokerMargin> {
  return apiGet<BrokerMargin>(`${BASE}/margin/user`, { signal })
}

export function getOrders(
  segment?: BrokerSegment,
  signal?: AbortSignal,
): Promise<BrokerOrder[]> {
  return apiGet<BrokerOrder[]>(`${BASE}/orders`, {
    signal,
    params: { segment },
  })
}

export const portfolioKeys = {
  all: ['portfolio'] as const,
  holdings: () => [...portfolioKeys.all, 'holdings'] as const,
  positions: (segment?: BrokerSegment) =>
    [...portfolioKeys.all, 'positions', segment ?? 'all'] as const,
  user: () => [...portfolioKeys.all, 'user'] as const,
  margin: () => [...portfolioKeys.all, 'margin'] as const,
  orders: (segment?: BrokerSegment) =>
    [...portfolioKeys.all, 'orders', segment ?? 'all'] as const,
}
