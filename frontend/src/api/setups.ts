import { apiGet } from './http'

/** Read models for the per-instrument setup observation endpoint (`/api/v1/setups/{id}`). */
export interface SetupObservation {
  observedAt: string
  direction: string
  setupStatus: string
  entryPattern: string | null
  structuralInvalidation: number | null
  structuralRr: number | null
  strategyVersion: string | null
  explanation: string | null
}

export function getSetups(
  instrumentId: number,
  signal?: AbortSignal,
): Promise<SetupObservation[]> {
  return apiGet<SetupObservation[]>(`/api/v1/setups/${instrumentId}`, {
    signal,
  })
}

export const setupKeys = {
  all: ['setups'] as const,
  forInstrument: (instrumentId: number) =>
    [...setupKeys.all, instrumentId] as const,
}
