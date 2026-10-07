<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { Play, RefreshCw, Square } from '@lucide/vue'
import {
  backtestKeys,
  cancelBacktest,
  getBacktestRuns,
  startBacktest,
  type BacktestRun,
} from '@/api/backtests'
import { ApiError } from '@/api/http'
import { catalogKeys, getRiskPolicies, getStrategies } from '@/api/catalog'
import { getWatchlist, watchlistKeys } from '@/api/watchlist'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

const queryClient = useQueryClient()
const router = useRouter()

function openDetail(runKey: string) {
  void router.push({ name: 'backtest-run', params: { runKey } })
}
const formError = ref<string | null>(null)

const form = reactive({
  symbols: [] as string[],
  startDate: '2026-08-01',
  endDate: '2026-09-18',
  timeframe: 'M5',
  startingCapital: 1000000,
  strategyPreset: 'ER_RS_CONTINUATION_V1_RESEARCH',
  riskPreset: 'RESEARCH_PERMISSIVE',
  strategyVersionId: '' as number | '',
  riskPolicyVersionId: '' as number | '',
  marketSymbol: 'NIFTY',
  contextSource: 'DERIVED_RESEARCH',
  strictProducers: true,
  warmupBars: 30,
  seed: 1,
  endOfRun: 'MARK_TO_MARKET',
})

const catalogStrategiesQuery = useQuery(() => ({
  queryKey: catalogKeys.activeStrategies(),
  queryFn: ({ signal }) => getStrategies(false, signal),
  staleTime: 30_000,
}))

const catalogRiskQuery = useQuery(() => ({
  queryKey: catalogKeys.activeRiskPolicies(),
  queryFn: ({ signal }) => getRiskPolicies(false, signal),
  staleTime: 30_000,
}))

const strategyOptions = computed(() => catalogStrategiesQuery.data.value ?? [])
const riskOptions = computed(() => catalogRiskQuery.data.value ?? [])

// Only versions with complete, resolvable parameters can be run; catalog placeholders are hidden.
const usableStrategies = computed(() =>
  strategyOptions.value
    .map((strategy) => ({
      ...strategy,
      versions: strategy.versions.filter(
        (version) =>
          version.parametersError === null && version.parameters !== null,
      ),
    }))
    .filter((strategy) => strategy.versions.length > 0),
)
const usableRiskPolicies = computed(() =>
  riskOptions.value
    .map((policy) => ({
      ...policy,
      versions: policy.versions.filter(
        (version) =>
          version.parametersError === null && version.parameters !== null,
      ),
    }))
    .filter((policy) => policy.versions.length > 0),
)

const watchlistQuery = useQuery(() => ({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
  staleTime: 60_000,
}))

const universe = computed(
  () => watchlistQuery.data.value?.entries.map((entry) => entry.symbol) ?? [],
)

// The watched universe is finite: preselect it and default the benchmark to NIFTY.
watch(
  universe,
  (symbols) => {
    if (form.symbols.length === 0 && symbols.length > 0) {
      form.symbols = [...symbols]
    }
    if (
      symbols.length > 0 &&
      !symbols.includes(form.marketSymbol) &&
      symbols.includes('NIFTY')
    ) {
      form.marketSymbol = 'NIFTY'
    }
  },
  { immediate: true },
)

function toggleSymbol(symbol: string) {
  form.symbols = form.symbols.includes(symbol)
    ? form.symbols.filter((value) => value !== symbol)
    : [...form.symbols, symbol]
}

const runsQuery = useQuery(() => ({
  queryKey: backtestKeys.list(),
  queryFn: ({ signal }) => getBacktestRuns(signal),
  refetchInterval: 5000,
}))

const runs = computed<BacktestRun[]>(() => runsQuery.data.value ?? [])

const startMutation = useMutation({
  mutationFn: () =>
    startBacktest({
      symbols: form.symbols,
      startDate: form.startDate,
      endDate: form.endDate,
      timeframe: form.timeframe,
      dailyTimeframe: 'D1',
      startingCapital: form.startingCapital,
      currency: 'INR',
      strategyPreset: form.strategyPreset,
      riskPreset: form.riskPreset,
      strategyVersionId:
        form.strategyVersionId === '' ? null : form.strategyVersionId,
      riskPolicyVersionId:
        form.riskPolicyVersionId === '' ? null : form.riskPolicyVersionId,
      marketSymbol: form.marketSymbol || null,
      contextSource: form.contextSource,
      strictProducers: form.strictProducers,
      warmupBars: form.warmupBars,
      seed: form.seed,
      endOfRun: form.endOfRun,
    }),
  onSuccess: () => {
    formError.value = null
    void queryClient.invalidateQueries({ queryKey: backtestKeys.list() })
  },
  onError: (error) => {
    formError.value =
      error instanceof ApiError
        ? error.message
        : 'Could not start the backtest.'
  },
})

const cancelMutation = useMutation({
  mutationFn: (runKey: string) => cancelBacktest(runKey),
  onSuccess: () =>
    queryClient.invalidateQueries({ queryKey: backtestKeys.all }),
})

function cloneRun(item: BacktestRun) {
  const parameters = (item.parameters ?? {}) as Record<string, unknown>
  const symbols = Array.isArray(parameters.symbols)
    ? (parameters.symbols as string[])
    : []
  if (symbols.length > 0) {
    form.symbols = [...symbols]
  }
  if (typeof parameters.start === 'string') {
    form.startDate = parameters.start
  }
  if (typeof parameters.end === 'string') {
    form.endDate = parameters.end
  }
  if (typeof parameters.timeframe === 'string') {
    form.timeframe = parameters.timeframe
  }
  if (typeof parameters.capital === 'number') {
    form.startingCapital = parameters.capital
  }
  if (typeof parameters.riskPolicy === 'string') {
    form.riskPreset = parameters.riskPolicy.split('/')[0] ?? form.riskPreset
  }
  if (typeof parameters.strategy === 'string') {
    form.strategyPreset =
      parameters.strategy.split('/')[0] ?? form.strategyPreset
  }
  form.strategyVersionId =
    typeof parameters.strategyVersionId === 'number'
      ? parameters.strategyVersionId
      : ''
  form.riskPolicyVersionId =
    typeof parameters.riskPolicyVersionId === 'number'
      ? parameters.riskPolicyVersionId
      : ''
  if (typeof parameters.strict === 'boolean') {
    form.strictProducers = parameters.strict
  }
  if (typeof parameters.warmup === 'number') {
    form.warmupBars = parameters.warmup
  }
  if (typeof parameters.seed === 'number') {
    form.seed = parameters.seed
  }
}
</script>

<template>
  <div class="flex flex-1 flex-col gap-4 p-4 lg:p-6">
    <header class="flex flex-wrap items-start justify-between gap-3">
      <div class="space-y-1">
        <h1 class="text-xl font-semibold tracking-tight">Backtests</h1>
        <p class="max-w-3xl text-sm text-muted-foreground">
          Deterministic chronological replay over canonical history. Simulated
          activity is isolated from production authority and never reaches a
          broker.
        </p>
      </div>
      <Button variant="outline" size="sm" @click="runsQuery.refetch()">
        <RefreshCw class="mr-1 size-3.5" aria-hidden="true" /> Refresh
      </Button>
    </header>

    <Card class="gap-0 overflow-hidden py-0">
      <CardHeader class="px-5 py-5">
        <CardTitle class="text-base">New run</CardTitle>
        <CardDescription>
          Runs the production strategy, risk, and plan engines. Without wired
          market/stock producers a run fails closed and reports no trades.
        </CardDescription>
      </CardHeader>
      <CardContent
        class="grid gap-3 border-t p-5 sm:grid-cols-2 lg:grid-cols-4"
      >
        <fieldset class="space-y-1 text-sm sm:col-span-2 lg:col-span-4">
          <legend class="text-muted-foreground">
            Symbols (active watchlist)
            <span class="text-xs">· {{ form.symbols.length }} selected</span>
          </legend>
          <div
            v-if="universe.length"
            class="flex flex-wrap gap-x-4 gap-y-1 rounded-md border p-2"
          >
            <label
              v-for="symbol in universe"
              :key="symbol"
              class="flex items-center gap-1.5 text-sm"
            >
              <input
                type="checkbox"
                :checked="form.symbols.includes(symbol)"
                :aria-label="`Include ${symbol}`"
                @change="toggleSymbol(symbol)"
              />
              <span>{{ symbol }}</span>
            </label>
          </div>
          <p v-else class="text-xs text-muted-foreground">
            No watched instruments. Add symbols on the Watchlist page first.
          </p>
          <p v-if="form.symbols.length === 0" class="text-xs text-amber-600">
            Select at least one symbol.
          </p>
        </fieldset>
        <label class="space-y-1 text-sm">
          <span class="text-muted-foreground">Start</span>
          <input
            v-model="form.startDate"
            type="date"
            aria-label="Start date"
            class="w-full rounded-md border bg-transparent px-2 py-1"
          />
        </label>
        <label class="space-y-1 text-sm">
          <span class="text-muted-foreground">End</span>
          <input
            v-model="form.endDate"
            type="date"
            aria-label="End date"
            class="w-full rounded-md border bg-transparent px-2 py-1"
          />
        </label>
        <label class="space-y-1 text-sm">
          <span class="text-muted-foreground">Strategy preset</span>
          <select
            v-model="form.strategyPreset"
            aria-label="Strategy preset"
            class="w-full rounded-md border bg-transparent px-2 py-1"
          >
            <option value="ER_RS_CONTINUATION_V1_RESEARCH">
              ER RS Continuation V1 (research)
            </option>
          </select>
        </label>
        <label class="space-y-1 text-sm">
          <span class="text-muted-foreground">Risk preset</span>
          <select
            v-model="form.riskPreset"
            aria-label="Risk preset"
            class="w-full rounded-md border bg-transparent px-2 py-1"
          >
            <option value="RESEARCH_PERMISSIVE">Research permissive</option>
            <option value="RESEARCH_CONSERVATIVE">Research conservative</option>
          </select>
        </label>
        <label class="space-y-1 text-sm">
          <span class="text-muted-foreground">Strategy version (catalog)</span>
          <select
            v-model="form.strategyVersionId"
            aria-label="Strategy version"
            class="w-full rounded-md border bg-transparent px-2 py-1"
          >
            <option value="">Use research preset</option>
            <optgroup
              v-for="strategy in usableStrategies"
              :key="strategy.code"
              :label="strategy.code"
            >
              <option
                v-for="entry in strategy.versions"
                :key="entry.strategyVersionId"
                :value="entry.strategyVersionId"
              >
                v{{ entry.version }} · {{ entry.lifecycleState }}
              </option>
            </optgroup>
          </select>
        </label>
        <label class="space-y-1 text-sm">
          <span class="text-muted-foreground"
            >Risk policy version (catalog)</span
          >
          <select
            v-model="form.riskPolicyVersionId"
            aria-label="Risk policy version"
            class="w-full rounded-md border bg-transparent px-2 py-1"
          >
            <option value="">Use research preset</option>
            <optgroup
              v-for="policy in usableRiskPolicies"
              :key="policy.code"
              :label="policy.code"
            >
              <option
                v-for="entry in policy.versions"
                :key="entry.riskPolicyVersionId"
                :value="entry.riskPolicyVersionId"
              >
                v{{ entry.version }} · {{ entry.lifecycleState }}
              </option>
            </optgroup>
          </select>
        </label>
        <label class="space-y-1 text-sm">
          <span class="text-muted-foreground">Starting capital</span>
          <input
            v-model.number="form.startingCapital"
            type="number"
            aria-label="Starting capital"
            class="w-full rounded-md border bg-transparent px-2 py-1"
          />
        </label>
        <label class="space-y-1 text-sm">
          <span class="text-muted-foreground">Warm-up bars</span>
          <input
            v-model.number="form.warmupBars"
            type="number"
            aria-label="Warm-up bars"
            class="w-full rounded-md border bg-transparent px-2 py-1"
          />
        </label>
        <label class="space-y-1 text-sm">
          <span class="text-muted-foreground">Context</span>
          <select
            v-model="form.contextSource"
            aria-label="Context source"
            class="w-full rounded-md border bg-transparent px-2 py-1"
          >
            <option value="DERIVED_RESEARCH">
              Derived from canonical data (research)
            </option>
            <option value="STRICT_PRODUCTION">
              Strict production (fails closed)
            </option>
          </select>
        </label>
        <label class="space-y-1 text-sm">
          <span class="text-muted-foreground">Market symbol (benchmark)</span>
          <select
            v-model="form.marketSymbol"
            aria-label="Market symbol"
            class="w-full rounded-md border bg-transparent px-2 py-1"
          >
            <option v-for="symbol in universe" :key="symbol" :value="symbol">
              {{ symbol }}
            </option>
          </select>
        </label>
        <label class="flex items-center gap-2 pt-5 text-sm">
          <input v-model="form.strictProducers" type="checkbox" />
          <span>Strict production producers (fail closed)</span>
        </label>
        <div class="flex items-end">
          <Button
            :disabled="startMutation.isPending.value"
            @click="startMutation.mutate()"
          >
            <Play class="mr-1 size-3.5" aria-hidden="true" /> Start run
          </Button>
        </div>
        <p
          v-if="formError"
          class="text-sm text-negative sm:col-span-2 lg:col-span-4"
          role="alert"
        >
          {{ formError }}
        </p>
      </CardContent>
    </Card>

    <Card class="gap-0 overflow-hidden py-0">
      <CardHeader class="px-5 py-5">
        <CardTitle class="text-base">Runs</CardTitle>
      </CardHeader>
      <div class="overflow-x-auto border-t" data-testid="backtest-runs">
        <Table class="min-w-[880px]">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead>Strategy</TableHead>
              <TableHead>Period</TableHead>
              <TableHead>Universe</TableHead>
              <TableHead>Status</TableHead>
              <TableHead class="text-right">Net return</TableHead>
              <TableHead class="text-right">Max DD</TableHead>
              <TableHead class="text-right">Trades</TableHead>
              <TableHead class="w-[150px] text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="item in runs"
              :key="item.runKey"
              tabindex="0"
              class="cursor-pointer focus-visible:bg-muted/50 focus-visible:outline-none"
              :aria-label="`Open backtest run ${item.runKey}`"
              @click="openDetail(item.runKey)"
              @keydown.enter.prevent="openDetail(item.runKey)"
            >
              <TableCell class="text-xs">
                {{ item.strategyId ?? '—' }} {{ item.strategyVersion ?? '' }}
              </TableCell>
              <TableCell class="text-xs">
                {{ item.startDate }} – {{ item.endDate }}
              </TableCell>
              <TableCell class="text-xs tabular-nums">
                {{ item.universeSize }}
              </TableCell>
              <TableCell>
                <Badge
                  variant="outline"
                  :class="
                    item.status === 'SUCCEEDED'
                      ? 'text-positive'
                      : item.status === 'FAILED' || item.status === 'CANCELLED'
                        ? 'text-negative'
                        : 'text-amber-600'
                  "
                  :data-testid="`run-status-${item.runKey}`"
                >
                  {{ item.status }}
                </Badge>
              </TableCell>
              <TableCell class="text-right text-xs tabular-nums">
                {{ item.metrics.netReturnPct ?? '—' }}%
              </TableCell>
              <TableCell class="text-right text-xs tabular-nums">
                {{ item.metrics.maxDrawdownPct ?? '—' }}%
              </TableCell>
              <TableCell class="text-right text-xs tabular-nums">
                {{ item.metrics.completedTrades ?? 0 }}
              </TableCell>
              <TableCell class="text-right">
                <Button
                  variant="ghost"
                  size="sm"
                  @click.stop="openDetail(item.runKey)"
                  >Open</Button
                >
                <Button
                  v-if="item.status === 'CREATED' || item.status === 'RUNNING'"
                  variant="ghost"
                  size="sm"
                  @click.stop="cancelMutation.mutate(item.runKey)"
                >
                  <Square class="size-3" aria-hidden="true" /> Cancel
                </Button>
                <Button variant="ghost" size="sm" @click.stop="cloneRun(item)">
                  Clone
                </Button>
              </TableCell>
            </TableRow>
            <TableRow v-if="runs.length === 0">
              <TableCell
                colspan="8"
                class="py-8 text-center text-sm text-muted-foreground"
              >
                No backtest runs yet.
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </Card>
  </div>
</template>
