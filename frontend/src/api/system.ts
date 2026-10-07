import { apiGet } from './http'

/** Actuator health only (details are never exposed by the backend). */
export interface HealthResponse {
  status: string
}

export function getHealth(signal?: AbortSignal): Promise<HealthResponse> {
  return apiGet<HealthResponse>('/actuator/health', { signal })
}

export interface SystemModeControl {
  present: boolean
  stopNewTrades: boolean
  cancelPendingEntries: boolean
  flattenOnly: boolean
  automationEnabled: boolean
  executionEnabled: boolean
  reason: string | null
  updatedBy: string | null
  updatedAt: string | null
}

/** Authoritative declared mode and persisted safety controls (`/api/v1/system/mode`). */
export interface SystemModeResponse {
  configuredMode: string
  realCapital: boolean
  executionEnabled: boolean
  control: SystemModeControl
  modeSource: string
  asOf: string
}

export function getSystemMode(
  signal?: AbortSignal,
): Promise<SystemModeResponse> {
  return apiGet<SystemModeResponse>('/api/v1/system/mode', { signal })
}

export const systemKeys = {
  all: ['system'] as const,
  health: () => [...systemKeys.all, 'health'] as const,
  mode: () => [...systemKeys.all, 'mode'] as const,
}
