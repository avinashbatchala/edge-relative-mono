import { onBeforeUnmount, onMounted } from 'vue'
import {
  useFeatureStreamStore,
  type FeatureStreamEnvelope,
} from '@/stores/feature-stream'

const MAX_BACKOFF_MS = 30_000
const RESYNC_TYPE = 'feature.resync'

function resolveStreamUrl(): string {
  const configured = import.meta.env.VITE_WS_BASE_URL as string | undefined
  if (configured) {
    return configured
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/features`
}

/**
 * WebSocket lifecycle for the feature stream. Reconnects with exponential backoff and requests an
 * authoritative resync on a sequence gap. It never applies events out of order and never computes
 * features locally.
 */
export function useFeatureStream() {
  const store = useFeatureStreamStore()
  let socket: WebSocket | null = null
  let reconnectTimer: number | null = null
  let attempts = 0
  let closedByUs = false

  function connect() {
    store.setConnection(attempts === 0 ? 'connecting' : 'reconnecting')
    try {
      socket = new WebSocket(resolveStreamUrl())
    } catch {
      store.setError('Feature stream unavailable')
      scheduleReconnect()
      return
    }
    socket.onopen = () => {
      attempts = 0
      store.setConnection('open')
      store.setError(null)
    }
    socket.onmessage = (event: MessageEvent<string>) => {
      let envelope: FeatureStreamEnvelope
      try {
        envelope = JSON.parse(event.data) as FeatureStreamEnvelope
      } catch {
        return
      }
      if (store.applyEnvelope(envelope) === 'gap') {
        requestResync()
      }
    }
    socket.onerror = () => {
      store.setError('Feature stream connection error')
    }
    socket.onclose = () => {
      store.setConnection('closed')
      scheduleReconnect()
    }
  }

  function requestResync() {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: RESYNC_TYPE }))
    }
  }

  function scheduleReconnect() {
    if (closedByUs) {
      return
    }
    attempts += 1
    const delay = Math.min(MAX_BACKOFF_MS, 1000 * 2 ** Math.min(attempts, 5))
    store.setConnection('reconnecting')
    reconnectTimer = window.setTimeout(connect, delay)
  }

  function disconnect() {
    closedByUs = true
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    socket?.close()
    socket = null
    store.setConnection('closed')
  }

  onMounted(connect)
  onBeforeUnmount(disconnect)

  return { connect, disconnect, requestResync }
}
