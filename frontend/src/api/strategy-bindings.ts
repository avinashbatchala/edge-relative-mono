import { apiGet, apiPost } from './http'

/** Per-instrument strategy parameter bindings (effective-dated, append-only). */
export interface StrategyBinding {
  instrumentId: number
  strategyVersionId: number
  lifecycleState: string
  effectiveFrom: string
  effectiveTo: string | null
  parameters: Record<string, unknown>
}

export function listStrategyBindings(
  instrumentId: number,
  signal?: AbortSignal,
): Promise<StrategyBinding[]> {
  return apiGet<StrategyBinding[]>(
    `/api/v1/strategy-bindings/${instrumentId}`,
    { signal },
  )
}

export function getEffectiveStrategyBinding(
  instrumentId: number,
  asOf: string,
  signal?: AbortSignal,
): Promise<StrategyBinding | null> {
  return apiGet<StrategyBinding | null>(
    `/api/v1/strategy-bindings/${instrumentId}/effective`,
    { signal, params: { asOf } },
  )
}

export interface CreateStrategyBindingRequest {
  instrumentId: number
  strategyVersionId: number
  parameters: Record<string, unknown>
  effectiveFrom: string
  effectiveTo?: string | null
  lifecycleState?: string
  source?: string
}

export function createStrategyBinding(
  request: CreateStrategyBindingRequest,
): Promise<{ bindingId: number }> {
  return apiPost<{ bindingId: number }>('/api/v1/strategy-bindings', request)
}

export const strategyBindingKeys = {
  all: ['strategy-bindings'] as const,
  forInstrument: (instrumentId: number) =>
    [...strategyBindingKeys.all, instrumentId] as const,
}
