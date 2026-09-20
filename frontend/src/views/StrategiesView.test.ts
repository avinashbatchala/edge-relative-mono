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
import * as catalogApi from '@/api/catalog'
import StrategiesView from './StrategiesView.vue'

vi.mock('@/api/catalog', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/catalog')>()
  return {
    ...actual,
    getStrategies: vi.fn(),
    getRiskPolicies: vi.fn(),
    getStrategyTemplate: vi.fn(),
    getRiskPolicyTemplate: vi.fn(),
    createStrategy: vi.fn(),
    addStrategyVersion: vi.fn(),
    createRiskPolicy: vi.fn(),
    addRiskPolicyVersion: vi.fn(),
    retireStrategy: vi.fn(),
    restoreStrategy: vi.fn(),
    retireRiskPolicy: vi.fn(),
    restoreRiskPolicy: vi.fn(),
  }
})

const getStrategies = vi.mocked(catalogApi.getStrategies)
const getRiskPolicies = vi.mocked(catalogApi.getRiskPolicies)
const getStrategyTemplate = vi.mocked(catalogApi.getStrategyTemplate)
const getRiskPolicyTemplate = vi.mocked(catalogApi.getRiskPolicyTemplate)
const createStrategy = vi.mocked(catalogApi.createStrategy)

function setup() {
  getStrategies.mockResolvedValue([
    {
      strategyId: 1,
      code: 'ER_RS_CONTINUATION_V1',
      name: 'ER RS Continuation',
      description: null,
      setupFamily: 'M5_3_8_CONFIRMATION',
      status: 'ACTIVE',
      retiredAt: null,
      retiredReason: null,
      createdAt: '2026-09-18T04:30:00Z',
      versions: [
        {
          strategyVersionId: 1,
          version: 1,
          lifecycleState: 'RESEARCH',
          primaryTimeframe: 'M5',
          featureSchemaVersionId: null,
          parameters: {
            parameterSetId: 'ER_RS_CONTINUATION_V1',
            parameterVersion: 1,
          },
          parametersError: null,
          createdAt: '2026-09-18T04:30:00Z',
        },
      ],
    },
  ])
  getRiskPolicies.mockResolvedValue([
    {
      riskPolicyId: 1,
      code: 'RESEARCH_PERMISSIVE',
      name: 'Permissive',
      description: null,
      status: 'ACTIVE',
      retiredAt: null,
      retiredReason: null,
      createdAt: '2026-09-18T04:30:00Z',
      versions: [
        {
          riskPolicyVersionId: 7,
          version: 1,
          lifecycleState: 'EXPERIMENTAL',
          parameters: { code: 'RESEARCH_PERMISSIVE', version: 1 },
          parametersError: null,
          createdAt: '2026-09-18T04:30:00Z',
        },
      ],
    },
  ])
  getStrategyTemplate.mockResolvedValue({
    parameterSetId: 'TEMPLATE',
    parameterVersion: 1,
  })
  getRiskPolicyTemplate.mockResolvedValue({ code: 'TEMPLATE', version: 1 })
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchInterval: false } },
  })
  render(defineComponent({ render: () => h(StrategiesView) }), {
    global: { plugins: [[VueQueryPlugin, { queryClient }]] },
  })
}

beforeEach(() => {
  getStrategies.mockReset()
  getRiskPolicies.mockReset()
  getStrategyTemplate.mockReset()
  getRiskPolicyTemplate.mockReset()
  createStrategy.mockReset()
})

afterEach(cleanup)

test('lists strategies with versions and switches to risk policies', async () => {
  setup()
  await screen.findByText('ER_RS_CONTINUATION_V1')
  expect(screen.getByText(/1 version/)).toBeTruthy()

  await fireEvent.click(screen.getByRole('tab', { name: 'Risk policies' }))
  await waitFor(() =>
    expect(screen.getByText('RESEARCH_PERMISSIVE')).toBeTruthy(),
  )
})

test('creating a strategy sends the edited parameters and reports server validation', async () => {
  setup()
  await screen.findByText('ER_RS_CONTINUATION_V1')

  await fireEvent.update(screen.getByLabelText('Strategy code'), 'MY_STRATEGY')
  await fireEvent.update(screen.getByLabelText('Strategy name'), 'My Strategy')
  createStrategy.mockRejectedValueOnce(
    new Error('Strategy code already exists.'),
  )

  await fireEvent.click(screen.getByRole('button', { name: /Create strategy/ }))
  await waitFor(() =>
    expect(screen.getByRole('alert').textContent).toContain(
      'Strategy code already exists',
    ),
  )
  expect(createStrategy).toHaveBeenCalledOnce()
})
