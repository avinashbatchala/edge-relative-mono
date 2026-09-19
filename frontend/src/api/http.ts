export interface ApiErrorBody {
  code?: string
  message?: string
  operation?: string | null
}

export interface RequestOptions {
  params?: Record<string, string | number | boolean | undefined | null>
  signal?: AbortSignal
}

export interface SendOptions extends RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
}

/** Normalized backend failure. Never contains credentials or stack traces. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly operation: string | null
  readonly retryAfterSeconds: number | null

  constructor(args: {
    message: string
    status: number
    code: string
    operation?: string | null
    retryAfterSeconds?: number | null
  }) {
    super(args.message)
    this.name = 'ApiError'
    this.status = args.status
    this.code = args.code
    this.operation = args.operation ?? null
    this.retryAfterSeconds = args.retryAfterSeconds ?? null
  }

  get isRateLimited(): boolean {
    return this.status === 429 || this.code === 'BROKER_RATE_LIMITED'
  }

  get isBrokerUnavailable(): boolean {
    return (
      this.status === 503 ||
      this.status === 504 ||
      this.code === 'BROKER_UNAVAILABLE' ||
      this.code === 'BROKER_TIMEOUT'
    )
  }

  get isAuthFailure(): boolean {
    return this.status === 401 || this.code === 'BROKER_AUTHENTICATION_FAILED'
  }
}

const BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''

function buildUrl(path: string, params?: RequestOptions['params']): string {
  const query = new URLSearchParams()
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') {
        query.set(key, String(value))
      }
    }
  }
  const suffix = query.toString()
  return `${BASE_URL}${path}${suffix ? `?${suffix}` : ''}`
}

function parseRetryAfter(header: string | null): number | null {
  if (!header) {
    return null
  }
  const seconds = Number.parseInt(header, 10)
  return Number.isFinite(seconds) ? seconds : null
}

/** Single entry point for broker/application calls. Components never call fetch directly. */
export async function apiSend<T>(
  path: string,
  options: SendOptions = {},
): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  let bodyInit: BodyInit | undefined
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    bodyInit = JSON.stringify(options.body)
  }

  let response: Response
  try {
    response = await fetch(buildUrl(path, options.params), {
      method,
      headers,
      body: bodyInit,
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new ApiError({
      message: 'Network unavailable',
      status: 0,
      code: 'NETWORK_UNAVAILABLE',
    })
  }

  if (!response.ok) {
    let body: ApiErrorBody = {}
    try {
      body = (await response.json()) as ApiErrorBody
    } catch {
      // Non-JSON error body; fall through with defaults.
    }
    throw new ApiError({
      message: body.message ?? `Request failed with status ${response.status}`,
      status: response.status,
      code: body.code ?? 'APPLICATION_ERROR',
      operation: body.operation,
      retryAfterSeconds: parseRetryAfter(response.headers.get('Retry-After')),
    })
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export function apiGet<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  return apiSend<T>(path, { ...options, method: 'GET' })
}

export function apiPost<T>(
  path: string,
  body?: unknown,
  options: RequestOptions = {},
): Promise<T> {
  return apiSend<T>(path, { ...options, method: 'POST', body })
}

export function apiPut<T>(
  path: string,
  body?: unknown,
  options: RequestOptions = {},
): Promise<T> {
  return apiSend<T>(path, { ...options, method: 'PUT', body })
}

export function apiDelete<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  return apiSend<T>(path, { ...options, method: 'DELETE' })
}
