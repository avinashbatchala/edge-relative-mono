import { apiPost } from './http'

const BASE = '/api/v1/llm'

export interface LlmNarrationRequest {
  question: string
  facts: string
}

/** Advisory narration. The API key stays server-side; the browser never holds it (ADR-007). */
export interface LlmNarrationResponse {
  advisory: boolean
  provider: string
  model: string
  text: string
}

export function narrateFundamentals(
  request: LlmNarrationRequest,
  signal?: AbortSignal,
): Promise<LlmNarrationResponse> {
  return apiPost<LlmNarrationResponse>(`${BASE}/narration`, request, { signal })
}
