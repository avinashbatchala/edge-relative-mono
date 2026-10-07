import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { render, screen } from '@testing-library/vue'
import { createPinia } from 'pinia'
import { expect, test, vi } from 'vitest'
import StatusBar from './StatusBar.vue'
import * as systemApi from '@/api/system'

vi.mock('@/api/system', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/system')>()
  return { ...actual, getHealth: vi.fn() }
})

vi.mocked(systemApi.getHealth).mockResolvedValue({ status: 'UP' })

test('shows broker, stream and advisory status', async () => {
  render(StatusBar, {
    global: {
      plugins: [
        createPinia(),
        [
          VueQueryPlugin,
          {
            queryClient: new QueryClient({
              defaultOptions: { queries: { retry: false } },
            }),
          },
        ],
      ],
    },
  })

  expect(await screen.findByText('Broker up')).toBeTruthy()
  expect(screen.getByText('Stream idle')).toBeTruthy()
  expect(screen.getByText('Advisory · execution disabled')).toBeTruthy()
})
