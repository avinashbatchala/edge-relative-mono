import { cleanup, render, screen } from '@testing-library/vue'
import { afterEach, expect, test } from 'vitest'
import ConnectionStatus from './ConnectionStatus.vue'

afterEach(cleanup)

test('announces each connection state in a live region', () => {
  const cases: [string, string][] = [
    ['open', 'Live'],
    ['connecting', 'Connecting'],
    ['reconnecting', 'Reconnecting'],
    ['closed', 'Offline'],
    ['idle', 'Idle'],
  ]
  for (const [connection, label] of cases) {
    render(ConnectionStatus, {
      props: { connection, lastUpdatedAt: '2026-09-20T10:00:00Z' },
    })
    const status = screen.getByText(label)
    expect(status.closest('[aria-live]')).toBeTruthy()
    cleanup()
  }
})

test('surfaces a resync gap without hiding the connection state', () => {
  render(ConnectionStatus, {
    props: { connection: 'open', gapDetected: true, showAge: false },
  })
  expect(screen.getByText('Live')).toBeTruthy()
  expect(screen.getByText('Resyncing')).toBeTruthy()
})
