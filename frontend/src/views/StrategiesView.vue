<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { Plus, Save, ShieldCheck, ShieldOff } from '@lucide/vue'
import {
  addRiskPolicyVersion,
  addStrategyVersion,
  catalogKeys,
  createRiskPolicy,
  createStrategy,
  getRiskPolicies,
  getRiskPolicyTemplate,
  getStrategies,
  getStrategyTemplate,
  restoreRiskPolicy,
  restoreStrategy,
  retireRiskPolicy,
  retireStrategy,
  type RiskPolicyView,
  type StrategyView,
} from '@/api/catalog'
import { ApiError } from '@/api/http'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { formatIstDateTime } from '@/lib/format'

const queryClient = useQueryClient()
const tab = ref<'strategies' | 'risk'>('strategies')

const strategiesQuery = useQuery(() => ({
  queryKey: catalogKeys.strategies(),
  queryFn: ({ signal }) => getStrategies(true, signal),
}))
const riskQuery = useQuery(() => ({
  queryKey: catalogKeys.riskPolicies(),
  queryFn: ({ signal }) => getRiskPolicies(true, signal),
}))

const strategies = computed<StrategyView[]>(
  () => strategiesQuery.data.value ?? [],
)
const riskPolicies = computed<RiskPolicyView[]>(
  () => riskQuery.data.value ?? [],
)

const selectedStrategy = ref<string | null>(null)
const selectedRisk = ref<string | null>(null)

const strategyTemplate = ref('')
const riskTemplate = ref('')

const newStrategy = reactive({
  code: '',
  name: '',
  description: '',
  setupFamily: 'M5_3_8_CONFIRMATION',
  primaryTimeframe: 'M5',
  lifecycleState: 'RESEARCH',
  parameters: '',
})
const newRisk = reactive({
  code: '',
  name: '',
  description: '',
  lifecycleState: 'EXPERIMENTAL',
  parameters: '',
})

const version = reactive({ lifecycleState: 'VALIDATED', parameters: '' })

const currentStrategy = computed(
  () => strategies.value.find((s) => s.code === selectedStrategy.value) ?? null,
)
const currentRisk = computed(
  () => riskPolicies.value.find((p) => p.code === selectedRisk.value) ?? null,
)

function report(failure: unknown) {
  errorMessage.value =
    failure instanceof ApiError
      ? failure.message
      : ((failure as Error)?.message ?? 'Request failed.')
}

const errorMessage = ref<string | null>(null)

function parseJson(text: string): Record<string, unknown> {
  const value = JSON.parse(text) as unknown
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Parameters must be a JSON object.')
  }
  return value as Record<string, unknown>
}

const createStrategyMutation = useMutation({
  mutationFn: () =>
    createStrategy({
      code: newStrategy.code,
      name: newStrategy.name,
      description: newStrategy.description || null,
      setupFamily: newStrategy.setupFamily || null,
      lifecycleState: newStrategy.lifecycleState,
      primaryTimeframe: newStrategy.primaryTimeframe,
      parameters: parseJson(newStrategy.parameters),
    }),
  onSuccess: (created) => {
    selectedStrategy.value = created.code
    void queryClient.invalidateQueries({ queryKey: catalogKeys.strategies() })
  },
  onError: (e) => report(e),
})

const addStrategyVersionMutation = useMutation({
  mutationFn: () =>
    addStrategyVersion(selectedStrategy.value as string, {
      lifecycleState: version.lifecycleState,
      parameters: parseJson(version.parameters),
    }),
  onSuccess: () => {
    void queryClient.invalidateQueries({ queryKey: catalogKeys.strategies() })
  },
  onError: (e) => report(e),
})

const createRiskMutation = useMutation({
  mutationFn: () =>
    createRiskPolicy({
      code: newRisk.code,
      name: newRisk.name,
      description: newRisk.description || null,
      lifecycleState: newRisk.lifecycleState,
      parameters: parseJson(newRisk.parameters),
    }),
  onSuccess: (created) => {
    selectedRisk.value = created.code
    void queryClient.invalidateQueries({ queryKey: catalogKeys.riskPolicies() })
  },
  onError: (e) => report(e),
})

const addRiskVersionMutation = useMutation({
  mutationFn: () =>
    addRiskPolicyVersion(selectedRisk.value as string, {
      lifecycleState: version.lifecycleState,
      parameters: parseJson(version.parameters),
    }),
  onSuccess: () => {
    void queryClient.invalidateQueries({ queryKey: catalogKeys.riskPolicies() })
  },
  onError: (e) => report(e),
})

const retireMutation = useMutation({
  mutationFn: async ({
    kind,
    code,
  }: {
    kind: 'strategy' | 'risk'
    code: string
  }) => {
    if (kind === 'strategy') {
      await retireStrategy(code)
    } else {
      await retireRiskPolicy(code)
    }
  },
  onSuccess: () => queryClient.invalidateQueries({ queryKey: catalogKeys.all }),
  onError: (e) => report(e),
})

const restoreMutation = useMutation({
  mutationFn: async ({
    kind,
    code,
  }: {
    kind: 'strategy' | 'risk'
    code: string
  }) => {
    if (kind === 'strategy') {
      await restoreStrategy(code)
    } else {
      await restoreRiskPolicy(code)
    }
  },
  onSuccess: () => queryClient.invalidateQueries({ queryKey: catalogKeys.all }),
  onError: (e) => report(e),
})

async function loadTemplates() {
  if (!strategyTemplate.value) {
    strategyTemplate.value = JSON.stringify(
      await getStrategyTemplate(),
      null,
      2,
    )
    newStrategy.parameters = strategyTemplate.value
  }
  if (!riskTemplate.value) {
    riskTemplate.value = JSON.stringify(await getRiskPolicyTemplate(), null, 2)
    newRisk.parameters = riskTemplate.value
  }
}

void loadTemplates()

function selectStrategy(strategy: StrategyView) {
  selectedStrategy.value = strategy.code
  version.parameters = JSON.stringify(
    strategy.versions.at(-1)?.parameters ?? {},
    null,
    2,
  )
}

function selectRisk(policy: RiskPolicyView) {
  selectedRisk.value = policy.code
  version.parameters = JSON.stringify(
    policy.versions.at(-1)?.parameters ?? {},
    null,
    2,
  )
}
</script>

<template>
  <div class="flex flex-1 flex-col gap-4 p-4 lg:p-6">
    <header class="space-y-2">
      <h1 class="text-xl font-semibold tracking-tight">
        Strategies &amp; Risk
      </h1>
      <p class="max-w-3xl text-sm text-muted-foreground">
        Versioned configurations of implemented strategies and risk policies.
        Versions are immutable — saving creates a new version — and retired
        entries stay resolvable for reproducible backtests.
      </p>
      <div class="inline-flex rounded-lg border p-0.5" role="tablist">
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'strategies'"
          class="rounded-md px-3 py-1.5 text-sm font-medium"
          :class="tab === 'strategies' ? 'bg-muted' : 'text-muted-foreground'"
          @click="tab = 'strategies'"
        >
          Strategies
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'risk'"
          class="rounded-md px-3 py-1.5 text-sm font-medium"
          :class="tab === 'risk' ? 'bg-muted' : 'text-muted-foreground'"
          @click="tab = 'risk'"
        >
          Risk policies
        </button>
      </div>
      <p v-if="errorMessage" class="text-sm text-negative" role="alert">
        {{ errorMessage }}
      </p>
    </header>

    <div
      v-if="tab === 'strategies'"
      class="grid gap-4 lg:grid-cols-[1fr_1.2fr]"
    >
      <Card class="gap-0 overflow-hidden py-0">
        <CardHeader class="px-5 py-4">
          <CardTitle class="text-base">Strategies</CardTitle>
          <CardDescription>Select one to add a new version.</CardDescription>
        </CardHeader>
        <CardContent class="space-y-2 border-t p-4">
          <button
            v-for="strategy in strategies"
            :key="strategy.code"
            type="button"
            class="w-full rounded-md border p-3 text-left hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            :class="selectedStrategy === strategy.code ? 'border-primary' : ''"
            @click="selectStrategy(strategy)"
          >
            <div class="flex items-center justify-between gap-2">
              <span class="text-sm font-medium">{{ strategy.code }}</span>
              <Badge
                variant="outline"
                :class="
                  strategy.status === 'ACTIVE'
                    ? 'text-positive'
                    : 'text-muted-foreground'
                "
              >
                {{ strategy.status }}
              </Badge>
            </div>
            <p class="text-xs text-muted-foreground">{{ strategy.name }}</p>
            <p class="text-[10px] text-muted-foreground">
              {{ strategy.versions.length }} version(s) ·
              {{ strategy.versions.at(-1)?.lifecycleState ?? '—' }}
            </p>
          </button>
          <p
            v-if="strategies.length === 0"
            class="text-sm text-muted-foreground"
          >
            No strategies yet. Create one on the right.
          </p>
        </CardContent>
      </Card>

      <div class="space-y-4">
        <Card v-if="currentStrategy" class="gap-0 py-0">
          <CardHeader class="px-5 py-4">
            <CardTitle class="text-base">{{ currentStrategy.code }}</CardTitle>
            <CardDescription>
              {{ currentStrategy.name }} · {{ currentStrategy.status }}
            </CardDescription>
          </CardHeader>
          <CardContent class="space-y-3 border-t p-4">
            <ul class="space-y-1 text-xs">
              <li
                v-for="entry in currentStrategy.versions"
                :key="entry.strategyVersionId"
                class="flex items-center justify-between gap-2 rounded-md border px-3 py-2"
              >
                <span>
                  v{{ entry.version }} · {{ entry.lifecycleState }} ·
                  {{ entry.primaryTimeframe ?? '—' }}
                </span>
                <span class="text-muted-foreground">
                  {{ formatIstDateTime(entry.createdAt) }}
                </span>
              </li>
            </ul>
            <p
              v-if="currentStrategy.versions.some((v) => v.parametersError)"
              class="text-xs text-negative"
            >
              A stored version has invalid parameters and cannot be resolved.
            </p>
            <div class="flex flex-wrap items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                @click="
                  currentStrategy.status === 'ACTIVE'
                    ? retireMutation.mutate({
                        kind: 'strategy',
                        code: currentStrategy.code,
                      })
                    : restoreMutation.mutate({
                        kind: 'strategy',
                        code: currentStrategy.code,
                      })
                "
              >
                <ShieldOff
                  v-if="currentStrategy.status === 'ACTIVE'"
                  class="mr-1 size-3.5"
                />
                <ShieldCheck v-else class="mr-1 size-3.5" />
                {{ currentStrategy.status === 'ACTIVE' ? 'Retire' : 'Restore' }}
              </Button>
              <Button
                size="sm"
                :disabled="currentStrategy.status !== 'ACTIVE'"
                @click="addStrategyVersionMutation.mutate()"
              >
                <Save class="mr-1 size-3.5" /> Save as new version
              </Button>
            </div>
            <div class="grid gap-2 sm:grid-cols-[160px_1fr]">
              <label class="space-y-1 text-sm">
                <span class="text-muted-foreground">Lifecycle</span>
                <select
                  v-model="version.lifecycleState"
                  aria-label="Lifecycle state"
                  class="w-full rounded-md border bg-transparent px-2 py-1"
                >
                  <option>RESEARCH</option>
                  <option>EXPERIMENTAL</option>
                  <option>BACKTESTED</option>
                  <option>VALIDATED</option>
                  <option>SHADOW</option>
                  <option>PAPER</option>
                </select>
              </label>
              <label class="space-y-1 text-sm">
                <span class="text-muted-foreground">Parameters (JSON)</span>
                <textarea
                  v-model="version.parameters"
                  aria-label="Strategy parameters JSON"
                  rows="12"
                  class="w-full rounded-md border bg-transparent p-2 font-mono text-xs"
                />
              </label>
            </div>
          </CardContent>
        </Card>

        <Card class="gap-0 py-0">
          <CardHeader class="px-5 py-4">
            <CardTitle class="text-base">New strategy</CardTitle>
            <CardDescription
              >Creates the strategy and its first immutable
              version.</CardDescription
            >
          </CardHeader>
          <CardContent class="grid gap-3 border-t p-4 sm:grid-cols-2">
            <label class="space-y-1 text-sm">
              <span class="text-muted-foreground">Code</span>
              <input
                v-model="newStrategy.code"
                aria-label="Strategy code"
                class="w-full rounded-md border bg-transparent px-2 py-1"
              />
            </label>
            <label class="space-y-1 text-sm">
              <span class="text-muted-foreground">Name</span>
              <input
                v-model="newStrategy.name"
                aria-label="Strategy name"
                class="w-full rounded-md border bg-transparent px-2 py-1"
              />
            </label>
            <label class="space-y-1 text-sm">
              <span class="text-muted-foreground">Lifecycle</span>
              <select
                v-model="newStrategy.lifecycleState"
                aria-label="New strategy lifecycle"
                class="w-full rounded-md border bg-transparent px-2 py-1"
              >
                <option>RESEARCH</option>
                <option>EXPERIMENTAL</option>
                <option>BACKTESTED</option>
              </select>
            </label>
            <label class="space-y-1 text-sm">
              <span class="text-muted-foreground">Parameters (JSON)</span>
              <textarea
                v-model="newStrategy.parameters"
                aria-label="New strategy parameters JSON"
                rows="10"
                class="w-full rounded-md border bg-transparent p-2 font-mono text-xs sm:col-span-2"
              />
            </label>
            <div class="sm:col-span-2">
              <Button
                :disabled="createStrategyMutation.isPending.value"
                @click="createStrategyMutation.mutate()"
              >
                <Plus class="mr-1 size-3.5" /> Create strategy
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>

    <div v-else class="grid gap-4 lg:grid-cols-[1fr_1.2fr]">
      <Card class="gap-0 overflow-hidden py-0">
        <CardHeader class="px-5 py-4">
          <CardTitle class="text-base">Risk policies</CardTitle>
          <CardDescription>Select one to add a new version.</CardDescription>
        </CardHeader>
        <CardContent class="space-y-2 border-t p-4">
          <button
            v-for="policy in riskPolicies"
            :key="policy.code"
            type="button"
            class="w-full rounded-md border p-3 text-left hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            :class="selectedRisk === policy.code ? 'border-primary' : ''"
            @click="selectRisk(policy)"
          >
            <div class="flex items-center justify-between gap-2">
              <span class="text-sm font-medium">{{ policy.code }}</span>
              <Badge
                variant="outline"
                :class="
                  policy.status === 'ACTIVE'
                    ? 'text-positive'
                    : 'text-muted-foreground'
                "
              >
                {{ policy.status }}
              </Badge>
            </div>
            <p class="text-xs text-muted-foreground">{{ policy.name }}</p>
          </button>
          <p
            v-if="riskPolicies.length === 0"
            class="text-sm text-muted-foreground"
          >
            No risk policies yet.
          </p>
        </CardContent>
      </Card>

      <div class="space-y-4">
        <Card v-if="currentRisk" class="gap-0 py-0">
          <CardHeader class="px-5 py-4">
            <CardTitle class="text-base">{{ currentRisk.code }}</CardTitle>
            <CardDescription
              >{{ currentRisk.name }} ·
              {{ currentRisk.status }}</CardDescription
            >
          </CardHeader>
          <CardContent class="space-y-3 border-t p-4">
            <ul class="space-y-1 text-xs">
              <li
                v-for="entry in currentRisk.versions"
                :key="entry.riskPolicyVersionId"
                class="flex items-center justify-between gap-2 rounded-md border px-3 py-2"
              >
                <span>v{{ entry.version }} · {{ entry.lifecycleState }}</span>
                <span class="text-muted-foreground">{{
                  formatIstDateTime(entry.createdAt)
                }}</span>
              </li>
            </ul>
            <div class="flex flex-wrap items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                @click="
                  currentRisk.status === 'ACTIVE'
                    ? retireMutation.mutate({
                        kind: 'risk',
                        code: currentRisk.code,
                      })
                    : restoreMutation.mutate({
                        kind: 'risk',
                        code: currentRisk.code,
                      })
                "
              >
                {{ currentRisk.status === 'ACTIVE' ? 'Retire' : 'Restore' }}
              </Button>
              <Button
                size="sm"
                :disabled="currentRisk.status !== 'ACTIVE'"
                @click="addRiskVersionMutation.mutate()"
              >
                <Save class="mr-1 size-3.5" /> Save as new version
              </Button>
            </div>
            <label class="space-y-1 text-sm">
              <span class="text-muted-foreground">Parameters (JSON)</span>
              <textarea
                v-model="version.parameters"
                aria-label="Risk policy parameters JSON"
                rows="12"
                class="w-full rounded-md border bg-transparent p-2 font-mono text-xs"
              />
            </label>
          </CardContent>
        </Card>

        <Card class="gap-0 py-0">
          <CardHeader class="px-5 py-4">
            <CardTitle class="text-base">New risk policy</CardTitle>
          </CardHeader>
          <CardContent class="grid gap-3 border-t p-4 sm:grid-cols-2">
            <label class="space-y-1 text-sm">
              <span class="text-muted-foreground">Code</span>
              <input
                v-model="newRisk.code"
                aria-label="Risk policy code"
                class="w-full rounded-md border bg-transparent px-2 py-1"
              />
            </label>
            <label class="space-y-1 text-sm">
              <span class="text-muted-foreground">Name</span>
              <input
                v-model="newRisk.name"
                aria-label="Risk policy name"
                class="w-full rounded-md border bg-transparent px-2 py-1"
              />
            </label>
            <label class="space-y-1 text-sm sm:col-span-2">
              <span class="text-muted-foreground">Parameters (JSON)</span>
              <textarea
                v-model="newRisk.parameters"
                aria-label="New risk policy parameters JSON"
                rows="10"
                class="w-full rounded-md border bg-transparent p-2 font-mono text-xs"
              />
            </label>
            <div class="sm:col-span-2">
              <Button
                :disabled="createRiskMutation.isPending.value"
                @click="createRiskMutation.mutate()"
              >
                <Plus class="mr-1 size-3.5" /> Create risk policy
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  </div>
</template>
