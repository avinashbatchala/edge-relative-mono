import { createPinia } from 'pinia'
import { cleanup, render, screen } from '@testing-library/vue'
import { afterEach, expect, test } from 'vitest'
import App from './App.vue'

afterEach(cleanup)

test('renders a disconnected, presentation-only workstation without trading controls', () => {
  render(App, { global: { plugins: [createPinia()] } })

  expect(screen.getByRole('heading', { level: 1 }).textContent).toContain(
    'Nothing live. Yet.',
  )
  expect(screen.getByText('Not connected')).toBeTruthy()
  expect(
    screen.getByRole('heading', { name: 'Trading unavailable' }),
  ).toBeTruthy()
  expect(
    screen.getByText('No live or simulated trading is running.'),
  ).toBeTruthy()
  expect(screen.getAllByRole('article')).toHaveLength(3)
  expect(screen.queryAllByRole('button')).toHaveLength(0)
})
