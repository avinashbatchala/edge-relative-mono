<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { Play, RefreshCw, Square } from '@lucide/vue'
import {
  backtestKeys,
  cancelBacktest,
  getBacktestEquity,
  getBacktestRun,
  getBacktestRuns,
  getBacktestTrades,
  startBacktest,
  type BacktestRun,
} from '@/api/backtests'
import { ApiError } from '@/api/http'
import { getRiskPolicies, getStrategies } from '@/api/catalog'
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
import { formatInr, formatPercent, formatPrice } from '@/lib/format'

const queryClient = useQueryClient()
const router = useRouter()
const selected = ref<string | null>(null)

function openDetail(runKey: string) {
  void router.push({ name: 'backtest-run', params: { runKey } })
}
const tab = ref<'overview' | 'trades' | 'symbols'>('overview')
const symbolFilter = ref('')
const formError = ref<string | null>(null)

const form = reactive({
  symbols: 'SBIN, RELIANCE, TCS',
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
  queryKey: ['catalog', 'strategies', 'active'],
  queryFn: ({ signal }) => getStrategies(false, signal),
  staleTime: 30_000,
}))

const catalogRiskQuery = useQuery(() => ({
  queryKey: ['catalog', 'risk', 'active'],
  queryFn: ({ signal }) => getRiskPolicies(false, signal),
  staleTime: 30_000,
}))

const strategyOptions = computed(() => catalogStrategiesQuery.data.value ?? [])
const riskOptions = computed(() => catalogRiskQuery.data.value ?? [])

const runsQuery = useQuery(() => ({
  queryKey: backtestKeys.list(),
  queryFn: ({ signal }) => getBacktestRuns(signal),
  refetchInterval: 5000,
}))

const runQuery = useQuery(() => ({
  queryKey: backtestKeys.detail(selected.value ?? ''),
  queryFn: ({ signal }) => getBacktestRun(selected.value as string, signal),
  enabled: selected.value !== null,
  refetchInterval: 5000,
}))

const tradesQuery = useQuery(() => ({
  queryKey: backtestKeys.trades(selected.value ?? '', symbolFilter.value),
  queryFn: ({ signal }) =>
    getBacktestTrades(selected.value as string, symbolFilter.value, signal),
  enabled: selected.value !== null,
}))

const equityQuery = useQuery(() => ({
  queryKey: backtestKeys.equity(selected.value ?? ''),
  queryFn: ({ signal }) => getBacktestEquity(selected.value as string, signal),
  enabled: selected.value !== null,
}))

const runs = computed<BacktestRun[]>(() => runsQuery.data.value ?? [])
const run = computed<BacktestRun | null>(() => runQuery.data.value ?? null)
const trades = computed(() => tradesQuery.data.value ?? [])
const equity = computed(() => equityQuery.data.value ?? [])

const startMutation = useMutation({
  mutationFn: () =>
    startBacktest({
      symbols: form.symbols
        .split(',')
        .map((s) => s.trim().toUpperCase())
        .filter(Boolean),
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

function metric(key: string): string {
  const value = run.value?.metrics?.[key]
  if (value === null || value === undefined) {
    return '—'
  }
  if (typeof value === 'number') {
    return Number.isInteger(value) ? String(value) : value.toFixed(2)
  }
  return String(value)
}

function metricNote(key: string): string | null {
  const notes = run.value?.metrics?.notes
  return notes && typeof notes === 'object' && key in notes
    ? String((notes as Record<string, unknown>)[key])
    : null
}

const equityPath = computed(() => {
  const points = equity.value
  if (points.length < 2) {
    return null
  }
  const values = points.map((p) => p.equity)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  return values
    .map((value, index) => {
      const x = (index / (values.length - 1)) * 100
      const y = 100 - ((value - min) / span) * 100
      return `${index === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(' ')
})

const drawdownPath = computed(() => {
  const points = equity.value
  if (points.length < 2) {
    return null
  }
  const max = Math.max(...points.map((p) => p.drawdown), 1)
  return points
    .map((point, index) => {
      const x = (index / (points.length - 1)) * 100
      const y = (point.drawdown / max) * 100
      return `${index === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(' ')
})

const symbolStats = computed(() => {
  const groups = new Map<
    string,
    {
      symbol: string
      trades: number
      wins: number
      gross: number
      costs: number
      net: number
    }
  >()
  for (const trade of trades.value) {
    const entry = groups.get(trade.symbol) ?? {
      symbol: trade.symbol,
      trades: 0,
      wins: 0,
      gross: 0,
      costs: 0,
      net: 0,
    }
    entry.trades += 1
    entry.wins += trade.netPnl > 0 ? 1 : 0
    entry.gross += trade.grossPnl
    entry.costs += trade.explicitCosts
    entry.net += trade.netPnl
    groups.set(trade.symbol, entry)
  }
  return [...groups.values()].sort((a, b) => b.net - a.net)
})

const openRun = computed(
  () => run.value?.status === 'CREATED' || run.value?.status === 'RUNNING',
)

const netPnlValue = computed(() => Number(run.value?.metrics?.netPnl ?? 0))
const explicitCostsValue = computed(() =>
  Number(run.value?.metrics?.explicitCosts ?? 0),
)
const costModelLabel = computed(() => {
  const parameters = run.value?.parameters as
    Record<string, unknown> | undefined
  return String(parameters?.costModel ?? 'user-supplied assumption')
})

function selectRun(runKey: string) {
  selected.value = runKey
  tab.value = 'overview'
  symbolFilter.value = ''
}

const failureMessage = computed(() => {
  const failure = run.value?.failure as Record<string, unknown> | undefined
  const message = failure?.message
  return typeof message === 'string' && message.length > 0 ? message : null
})

const progressPct = computed(() => {
  const total = run.value?.progressTotal
  if (!total || total <= 0) {
    return null
  }
  return Math.min(
    100,
    Math.round(((run.value?.progressEvents ?? 0) / total) * 100),
  )
})

function cloneRun(item: BacktestRun) {
  const parameters = (item.parameters ?? {}) as Record<string, unknown>
  const symbols = Array.isArray(parameters.symbols)
    ? (parameters.symbols as string[])
    : []
  if (symbols.length > 0) {
    form.symbols = symbols.join(', ')
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

function configurable(key: string): boolean {
  return (
    run.value?.parameters !== undefined &&
    key in (run.value.parameters as Record<string, unknown>)
  )
}

function parameter(key: string): string {
  const value = (
    run.value?.parameters as Record<string, unknown> | undefined
  )?.[key]
  return value === null || value === undefined ? '—' : String(value)
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
        <label class="space-y-1 text-sm">
          <span class="text-muted-foreground">Symbols</span>
          <input
            v-model="form.symbols"
            aria-label="Symbols"
            class="w-full rounded-md border bg-transparent px-2 py-1"
          />
        </label>
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
              v-for="strategy in strategyOptions"
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
              v-for="policy in riskOptions"
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
          <input
            v-model="form.marketSymbol"
            aria-label="Market symbol"
            class="w-full rounded-md border bg-transparent px-2 py-1"
          />
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
                  @click.stop="selectRun(item.runKey)"
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

    <Card
      v-if="run"
      class="gap-0 overflow-hidden py-0"
      data-testid="backtest-detail"
    >
      <CardHeader class="gap-2 px-5 py-5">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <CardTitle class="text-base"
            >Run {{ run.runKey.slice(0, 8) }}</CardTitle
          >
          <div class="flex items-center gap-2 text-xs text-muted-foreground">
            <Badge variant="outline">{{ run.status }}</Badge>
            <span v-if="openRun">
              {{ run.progressEvents }} / {{ run.progressTotal ?? '—' }} events
            </span>
          </div>
        </div>
        <CardDescription class="text-xs">
          Dataset {{ run.datasetCode }} · checksum
          {{ run.datasetChecksum?.slice(0, 12) ?? '—' }} · capital
          {{ formatInr(run.startingCapital) }} · warm-up
          {{ parameter('warmup') }} · seed {{ parameter('seed') }} ·
          {{ run.progressThrough ?? 'not started' }}
        </CardDescription>
        <div v-if="progressPct !== null && openRun" class="space-y-1">
          <div
            class="h-1.5 w-full overflow-hidden rounded-full bg-muted"
            role="progressbar"
            :aria-valuenow="progressPct"
            aria-valuemin="0"
            aria-valuemax="100"
          >
            <div
              class="h-full bg-primary"
              :style="{ width: `${progressPct}%` }"
            />
          </div>
          <p class="text-xs text-muted-foreground">
            Processing historical events — {{ progressPct }}%
          </p>
        </div>
        <p
          v-if="failureMessage"
          class="rounded-md border border-negative/40 p-2 text-xs text-negative"
          role="alert"
        >
          Run failed: {{ failureMessage }}
        </p>
      </CardHeader>

      <div
        class="flex gap-1 border-y px-5 py-2"
        role="tablist"
        aria-label="Backtest results"
      >
        <button
          v-for="name in ['overview', 'trades', 'symbols'] as const"
          :key="name"
          type="button"
          role="tab"
          :aria-selected="tab === name"
          class="rounded-md px-3 py-1 text-sm font-medium capitalize focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          :class="tab === name ? 'bg-muted' : 'text-muted-foreground'"
          @click="tab = name"
        >
          {{ name }}
        </button>
      </div>

      <CardContent class="space-y-4 p-5">
        <template v-if="tab === 'overview'">
          <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
            <div>
              <p class="text-xs text-muted-foreground">Net return</p>
              <p class="text-lg font-semibold tabular-nums">
                {{ metric('netReturnPct') }}%
              </p>
              <p
                v-if="metricNote('netReturnPct')"
                class="text-[10px] text-muted-foreground"
              >
                {{ metricNote('netReturnPct') }}
              </p>
            </div>
            <div>
              <p class="text-xs text-muted-foreground">Net P&amp;L</p>
              <p class="text-lg font-semibold tabular-nums">
                {{ formatPrice(netPnlValue) }}
              </p>
              <p class="text-[10px] text-muted-foreground">
                costs {{ formatPrice(explicitCostsValue) }}
              </p>
            </div>
            <div>
              <p class="text-xs text-muted-foreground">Max drawdown</p>
              <p class="text-lg font-semibold tabular-nums">
                {{ metric('maxDrawdownPct') }}%
              </p>
            </div>
            <div>
              <p class="text-xs text-muted-foreground">Completed trades</p>
              <p class="text-lg font-semibold tabular-nums">
                {{ metric('completedTrades') }}
              </p>
              <p class="text-[10px] text-muted-foreground">
                open {{ metric('openPositions') }}
              </p>
            </div>
            <div>
              <p class="text-xs text-muted-foreground">Win rate</p>
              <p class="text-lg font-semibold tabular-nums">
                {{ metric('winRatePct') }}%
              </p>
              <p
                v-if="metricNote('winRatePct')"
                class="text-[10px] text-muted-foreground"
              >
                {{ metricNote('winRatePct') }}
              </p>
            </div>
            <div>
              <p class="text-xs text-muted-foreground">Profit factor</p>
              <p class="text-lg font-semibold tabular-nums">
                {{ metric('profitFactor') }}
              </p>
              <p
                v-if="metricNote('profitFactor')"
                class="text-[10px] text-muted-foreground"
              >
                {{ metricNote('profitFactor') }}
              </p>
            </div>
          </div>

          <div v-if="equityPath" class="space-y-2">
            <p class="text-xs text-muted-foreground">
              Net equity and drawdown (same time axis)
            </p>
            <svg
              viewBox="0 0 100 100"
              preserveAspectRatio="none"
              class="h-40 w-full rounded-md border"
              role="img"
              aria-label="Equity curve and drawdown"
            >
              <path
                :d="equityPath"
                fill="none"
                stroke="currentColor"
                stroke-width="1"
              />
              <path
                v-if="drawdownPath"
                :d="drawdownPath"
                fill="none"
                stroke="currentColor"
                stroke-dasharray="2 2"
                stroke-width="0.6"
                class="text-negative"
              />
            </svg>
          </div>
          <p v-else class="text-sm text-muted-foreground">
            No equity samples for this run.
          </p>

          <div class="space-y-1 text-xs text-muted-foreground">
            <p>
              No-trade or zero-risk outcomes report null metrics with
              explanations, never zero. Slippage is reflected in fill prices and
              never deducted twice. Open positions are marked to market, not
              counted as completed trades.
            </p>
            <p v-if="run.metrics.samplingAssumption">
              {{ run.metrics.samplingAssumption }}
            </p>
          </div>
        </template>

        <template v-else-if="tab === 'trades'">
          <label class="flex items-center gap-2 text-sm">
            <span class="text-muted-foreground">Symbol</span>
            <input
              v-model="symbolFilter"
              aria-label="Filter by symbol"
              class="rounded-md border bg-transparent px-2 py-1"
            />
          </label>
          <div class="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow class="hover:bg-transparent">
                  <TableHead>Symbol</TableHead>
                  <TableHead>Dir</TableHead>
                  <TableHead>Entry</TableHead>
                  <TableHead>Exit</TableHead>
                  <TableHead class="text-right">Qty</TableHead>
                  <TableHead class="text-right">Gross</TableHead>
                  <TableHead class="text-right">Costs</TableHead>
                  <TableHead class="text-right">Net</TableHead>
                  <TableHead class="text-right">R</TableHead>
                  <TableHead>Reason</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-for="trade in trades" :key="trade.tradeKey">
                  <TableCell class="text-xs">{{ trade.symbol }}</TableCell>
                  <TableCell class="text-xs">{{ trade.direction }}</TableCell>
                  <TableCell class="text-xs tabular-nums">
                    {{ formatPrice(trade.entryPrice) }}
                  </TableCell>
                  <TableCell class="text-xs tabular-nums">
                    {{
                      trade.exitPrice === null
                        ? 'open'
                        : formatPrice(trade.exitPrice)
                    }}
                  </TableCell>
                  <TableCell class="text-right text-xs tabular-nums">
                    {{ trade.quantity }}
                  </TableCell>
                  <TableCell class="text-right text-xs tabular-nums">
                    {{ formatPrice(trade.grossPnl) }}
                  </TableCell>
                  <TableCell class="text-right text-xs tabular-nums">
                    {{ formatPrice(trade.explicitCosts) }}
                  </TableCell>
                  <TableCell
                    class="text-right text-xs tabular-nums"
                    :class="
                      trade.netPnl >= 0 ? 'text-positive' : 'text-negative'
                    "
                  >
                    {{ formatPrice(trade.netPnl) }}
                  </TableCell>
                  <TableCell class="text-right text-xs tabular-nums">
                    {{
                      trade.realizedR === null
                        ? '—'
                        : trade.realizedR.toFixed(2)
                    }}
                  </TableCell>
                  <TableCell class="text-xs">
                    {{ trade.exitReason }}
                    <span v-if="trade.ambiguousBars > 0" class="text-amber-600">
                      · {{ trade.ambiguousBars }} ambiguous
                    </span>
                  </TableCell>
                </TableRow>
                <TableRow v-if="trades.length === 0">
                  <TableCell
                    colspan="10"
                    class="py-8 text-center text-sm text-muted-foreground"
                  >
                    No simulated trades for this run.
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </template>

        <template v-else>
          <p class="text-xs text-muted-foreground">
            Filtered symbol statistics are separate from full-run portfolio
            results.
          </p>
          <Table>
            <TableHeader>
              <TableRow class="hover:bg-transparent">
                <TableHead>Symbol</TableHead>
                <TableHead class="text-right">Trades</TableHead>
                <TableHead class="text-right">Win rate</TableHead>
                <TableHead class="text-right">Gross</TableHead>
                <TableHead class="text-right">Costs</TableHead>
                <TableHead class="text-right">Net</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="stat in symbolStats" :key="stat.symbol">
                <TableCell class="text-xs">{{ stat.symbol }}</TableCell>
                <TableCell class="text-right text-xs tabular-nums">{{
                  stat.trades
                }}</TableCell>
                <TableCell class="text-right text-xs tabular-nums">
                  {{ formatPercent((stat.wins / stat.trades) * 100) }}
                </TableCell>
                <TableCell class="text-right text-xs tabular-nums">{{
                  formatPrice(stat.gross)
                }}</TableCell>
                <TableCell class="text-right text-xs tabular-nums">{{
                  formatPrice(stat.costs)
                }}</TableCell>
                <TableCell
                  class="text-right text-xs tabular-nums"
                  :class="stat.net >= 0 ? 'text-positive' : 'text-negative'"
                >
                  {{ formatPrice(stat.net) }}
                </TableCell>
              </TableRow>
              <TableRow v-if="symbolStats.length === 0">
                <TableCell
                  colspan="6"
                  class="py-8 text-center text-sm text-muted-foreground"
                >
                  No symbol contribution.
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </template>

        <details class="text-xs">
          <summary class="cursor-pointer font-medium">
            Configuration and assumptions
          </summary>
          <p v-if="configurable('strict')" class="mt-1 text-muted-foreground">
            Strict producers: {{ parameter('strict') }}
          </p>
          <p class="text-muted-foreground">
            Execution: conservative OHLC-only fills, latency applied, ambiguous
            stop/target counted (stop-first). Costs:
            {{ costModelLabel }}.
          </p>
        </details>
      </CardContent>
    </Card>
  </div>
</template>
