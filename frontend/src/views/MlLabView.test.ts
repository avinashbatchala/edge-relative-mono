import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/vue'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as mlApi from '@/api/ml'
import MlLabView from './MlLabView.vue'

vi.mock('@/api/ml', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/ml')>()
  return {
    ...actual,
    createAnalysisRun: vi.fn(),
    listAnalysisRuns: vi.fn().mockResolvedValue([]),
  }
})

vi.mock('@/api/watchlist', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/watchlist')>()
  return {
    ...actual,
    getWatchlist: vi.fn().mockResolvedValue({
      name: 'Watchlist',
      capacity: 20,
      count: 1,
      entries: [{ symbol: 'SBIN', instrumentId: 1 }],
    }),
  }
})

const createAnalysisRun = vi.mocked(mlApi.createAnalysisRun)

function renderView() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, retryDelay: 0 } },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/research/ml',
        name: 'ml-lab',
        component: { template: '<div/>' },
      },
      {
        path: '/research/ml/:runKey',
        name: 'ml-run',
        component: { template: '<div/>' },
      },
    ],
  })
  router.push('/research/ml')
  render(defineComponent({ render: () => h(MlLabView) }), {
    global: { plugins: [[VueQueryPlugin, { queryClient }], router] },
  })
  return router
}

beforeEach(() => createAnalysisRun.mockReset())
afterEach(cleanup)

test('launches an analysis for the selected universe', async () => {
  const router = renderView()
  createAnalysisRun.mockResolvedValue({
    id: 1,
    key: 'run-key',
    status: 'QUEUED',
    requestedBy: 'workstation',
    config: {},
    progress: {},
    metrics: {},
    modelVersionId: null,
    error: null,
    createdAt: '2026-09-18T10:00:00Z',
    startedAt: null,
    completedAt: null,
  })

  const sbin = await screen.findByLabelText('Include SBIN')
  await fireEvent.click(sbin)
  await fireEvent.click(screen.getByRole('button', { name: /Start analysis/ }))

  await waitFor(() => expect(createAnalysisRun).toHaveBeenCalledTimes(1))
  const call = createAnalysisRun.mock.calls[0]![0]
  expect(call.symbols).toEqual(['SBIN'])
  expect(call.setupTimeframe).toBe('M5')
  await waitFor(() => expect(router.currentRoute.value.name).toBe('ml-run'))
})

test('disables launch until a symbol is selected', async () => {
  renderView()
  await screen.findByLabelText('Include SBIN')
  expect(
    (
      screen.getByRole('button', {
        name: /Start analysis/,
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true)
})
