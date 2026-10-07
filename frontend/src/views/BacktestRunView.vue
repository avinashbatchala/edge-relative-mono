<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, RefreshCw } from '@lucide/vue'
import {
  backtestKeys,
  getBacktestEquity,
  getBacktestRun,
  getBacktestTrades,
} from '@/api/backtests'
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
import FeatureHistoryPanel from '@/components/feature/FeatureHistoryPanel.vue'
import SegmentedTabs from '@/components/common/SegmentedTabs.vue'
import type { ChartMarker } from '@/lib/chart-markers'
import { formatInr, formatIstDateTime, formatPrice } from '@/lib/format'

const route = useRoute()
const router = useRouter()
const runKey = computed(() => String(route.params.runKey))

const tab = ref<'overview' | 'trades' | 'chart'>('overview')
const tabs = [
  { value: 'overview', label: 'overview' },
  { value: 'trades', label: 'trades' },
  { value: 'chart', label: 'chart' },
] as const
const selectedSymbol = ref<string | null>(null)

const runQuery = useQuery(() => ({
  queryKey: backtestKeys.detail(runKey.value),
  queryFn: ({ signal }) => getBacktestRun(runKey.value, signal),
  refetchInterval: 5000,
}))

const tradesQuery = useQuery(() => ({
  queryKey: backtestKeys.trades(runKey.value, ''),
  queryFn: ({ signal }) => getBacktestTrades(runKey.value, undefined, signal),
}))

const equityQuery = useQuery(() => ({
  queryKey: backtestKeys.equity(runKey.value),
  queryFn: ({ signal }) => getBacktestEquity(runKey.value, signal),
}))

const run = computed(() => runQuery.data.value ?? null)
const trades = computed(() => tradesQuery.data.value ?? [])
const equity = computed(() => equityQuery.data.value ?? [])

const symbols = computed(() => [...new Set(trades.value.map((t) => t.symbol))])
const activeSymbol = computed(
  () => selectedSymbol.value ?? symbols.value[0] ?? null,
)

const activeInstrumentId = computed(
  () =>
    trades.value.find((t) => t.symbol === activeSymbol.value)?.instrumentId ??
    0,
)

const windowFrom = computed(() =>
  run.value?.startDate ? `${run.value.startDate}T00:00:00+05:30` : undefined,
)
const windowTo = computed(() =>
  run.value?.endDate ? `${run.value.endDate}T23:59:59+05:30` : undefined,
)

const markers = computed<ChartMarker[]>(() =>
  trades.value
    .filter((trade) => trade.symbol === activeSymbol.value)
    .flatMap((trade) => {
      const long = trade.direction === 'LONG'
      const result: ChartMarker[] = [
        {
          time: trade.entryAt,
          position: long ? 'belowBar' : 'aboveBar',
          color: long ? '#15803d' : '#b91c1c',
          shape: long ? 'arrowUp' : 'arrowDown',
          text: `Entry ${trade.direction}`,
        },
      ]
      if (trade.exitAt) {
        result.push({
          time: trade.exitAt,
          position: long ? 'aboveBar' : 'belowBar',
          color: '#525252',
          shape: 'circle',
          text: `Exit ${trade.exitReason ?? ''}`.trim(),
        })
      }
      return result
    })
    .sort((a, b) => Date.parse(a.time) - Date.parse(b.time)),
)

function metric(key: string): string {
  const value = run.value?.metrics?.[key]
  if (value === null || value === undefined) {
    return '—'
  }
  return typeof value === 'number' ? value.toFixed(2) : String(value)
}

const metrics = computed(() => [
  { label: 'Net return', value: `${metric('netReturnPct')}%` },
  {
    label: 'Net P&L',
    value: formatPrice(run.value?.metrics?.netPnl as number),
  },
  {
    label: 'Costs',
    value: formatPrice(run.value?.metrics?.explicitCosts as number),
  },
  { label: 'Max drawdown', value: `${metric('maxDrawdownPct')}%` },
  { label: 'Completed trades', value: metric('completedTrades') },
  { label: 'Open positions', value: metric('openPositions') },
  { label: 'Win rate', value: `${metric('winRatePct')}%` },
  { label: 'Profit factor', value: metric('profitFactor') },
  {
    label: 'Expectancy',
    value: formatPrice(run.value?.metrics?.expectancy as number),
  },
  { label: 'Avg R', value: metric('averageRealizedR') },
  { label: 'Rejections', value: metric('rejectionCount') },
  { label: 'Ambiguous bars', value: metric('ambiguousBarCount') },
])

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
    { symbol: string; trades: number; wins: number; net: number; costs: number }
  >()
  for (const trade of trades.value) {
    const entry = groups.get(trade.symbol) ?? {
      symbol: trade.symbol,
      trades: 0,
      wins: 0,
      net: 0,
      costs: 0,
    }
    entry.trades += 1
    entry.wins += trade.netPnl > 0 ? 1 : 0
    entry.net += trade.netPnl
    entry.costs += trade.explicitCosts
    groups.set(trade.symbol, entry)
  }
  return [...groups.values()].sort((a, b) => b.net - a.net)
})
</script>

<template>
  <div class="flex flex-1 flex-col gap-4 p-4 lg:p-6">
    <header class="flex flex-wrap items-start justify-between gap-3">
      <div class="space-y-1">
        <Button
          variant="ghost"
          size="sm"
          class="-ml-2"
          @click="router.push({ name: 'backtests' })"
        >
          <ArrowLeft class="mr-1 size-3.5" /> All runs
        </Button>
        <h1 class="text-xl font-semibold tracking-tight">
          Run {{ runKey.slice(0, 8) }}
          <Badge v-if="run" variant="outline" class="ml-1">{{
            run.status
          }}</Badge>
        </h1>
        <p class="text-sm text-muted-foreground">
          <template v-if="run">
            {{ run.strategyId }} {{ run.strategyVersion }} ·
            {{ run.startDate }} – {{ run.endDate }} · universe
            {{ run.universeSize }} · capital
            {{ formatInr(run.startingCapital) }} · dataset {{ run.datasetCode }}
          </template>
        </p>
      </div>
      <Button variant="outline" size="sm" @click="runQuery.refetch()">
        <RefreshCw class="mr-1 size-3.5" /> Refresh
      </Button>
    </header>

    <div v-if="runQuery.isError.value" class="text-sm text-negative">
      Run not found.
    </div>

    <SegmentedTabs v-model="tab" :tabs="tabs" capitalize />

    <template v-if="run">
      <Card v-if="tab === 'overview'" class="gap-0 py-0">
        <CardHeader class="px-5 py-4">
          <CardTitle class="text-base">Performance</CardTitle>
          <CardDescription>
            Marked-to-market net equity. Undefined metrics show an explanation
            in the run notes.
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-4 border-t p-5">
          <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            <div v-for="entry in metrics" :key="entry.label">
              <p class="text-xs text-muted-foreground">{{ entry.label }}</p>
              <p class="text-lg font-semibold tabular-nums">
                {{ entry.value }}
              </p>
            </div>
          </div>

          <div v-if="equityPath">
            <p class="mb-1 text-xs text-muted-foreground">
              Net equity (solid) and drawdown (dashed) — same time axis
            </p>
            <svg
              viewBox="0 0 100 100"
              preserveAspectRatio="none"
              class="h-44 w-full rounded-md border"
              role="img"
              aria-label="Equity and drawdown"
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
            No equity samples for this run. This is expected when no candidates
            qualified.
          </p>

          <p
            v-if="run.metrics.samplingAssumption"
            class="text-xs text-muted-foreground"
          >
            {{ run.metrics.samplingAssumption }}
          </p>
        </CardContent>
      </Card>

      <Card v-else-if="tab === 'trades'" class="gap-0 overflow-hidden py-0">
        <CardHeader class="px-5 py-4">
          <CardTitle class="text-base">Trades</CardTitle>
          <CardDescription
            >{{ trades.length }} simulated trade(s).</CardDescription
          >
        </CardHeader>
        <div class="overflow-x-auto border-t">
          <Table class="min-w-[1000px]">
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
                <TableCell class="text-xs">
                  {{ formatPrice(trade.entryPrice) }}
                  <span class="block text-[10px] text-muted-foreground">
                    {{ formatIstDateTime(trade.entryAt) }}
                  </span>
                </TableCell>
                <TableCell class="text-xs">
                  {{
                    trade.exitPrice === null
                      ? 'open'
                      : formatPrice(trade.exitPrice)
                  }}
                  <span
                    v-if="trade.exitAt"
                    class="block text-[10px] text-muted-foreground"
                  >
                    {{ formatIstDateTime(trade.exitAt) }}
                  </span>
                </TableCell>
                <TableCell class="text-right text-xs tabular-nums">{{
                  trade.quantity
                }}</TableCell>
                <TableCell class="text-right text-xs tabular-nums">
                  {{ formatPrice(trade.grossPnl) }}
                </TableCell>
                <TableCell class="text-right text-xs tabular-nums">
                  {{ formatPrice(trade.explicitCosts) }}
                </TableCell>
                <TableCell
                  class="text-right text-xs tabular-nums"
                  :class="trade.netPnl >= 0 ? 'text-positive' : 'text-negative'"
                >
                  {{ formatPrice(trade.netPnl) }}
                </TableCell>
                <TableCell class="text-right text-xs tabular-nums">
                  {{
                    trade.realizedR === null ? '—' : trade.realizedR.toFixed(2)
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
                  No trades. With strict production context the engine fails
                  closed; choose the derived research context to populate
                  results.
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </div>
      </Card>

      <Card v-else class="gap-0 py-0">
        <CardHeader class="px-5 py-4">
          <CardTitle class="text-base">Chart</CardTitle>
          <CardDescription>
            Entry/exit markers over price with RRS, RVOL and RVE overlays for
            the selected instrument.
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-3 border-t p-5">
          <div
            v-if="symbols.length === 0"
            class="text-sm text-muted-foreground"
          >
            No instruments to chart for this run.
          </div>
          <template v-else>
            <label class="flex items-center gap-2 text-sm">
              <span class="text-muted-foreground">Instrument</span>
              <select
                v-model="selectedSymbol"
                aria-label="Chart instrument"
                class="rounded-md border bg-transparent px-2 py-1"
              >
                <option v-for="symbol in symbols" :key="symbol" :value="symbol">
                  {{ symbol }}
                </option>
              </select>
            </label>
            <FeatureHistoryPanel
              v-if="activeInstrumentId > 0"
              :key="activeSymbol ?? ''"
              :instrument-id="activeInstrumentId"
              :symbol="activeSymbol ?? ''"
              :window-from="windowFrom"
              :window-to="windowTo"
              :markers="markers"
            />
          </template>
        </CardContent>
      </Card>

      <Card v-if="tab === 'overview' && symbolStats.length" class="gap-0 py-0">
        <CardHeader class="px-5 py-4">
          <CardTitle class="text-base">By symbol</CardTitle>
          <CardDescription
            >Filtered symbol contribution, separate from full-run
            results.</CardDescription
          >
        </CardHeader>
        <CardContent class="border-t p-5">
          <Table>
            <TableHeader>
              <TableRow class="hover:bg-transparent">
                <TableHead>Symbol</TableHead>
                <TableHead class="text-right">Trades</TableHead>
                <TableHead class="text-right">Win rate</TableHead>
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
                  {{ ((stat.wins / stat.trades) * 100).toFixed(1) }}%
                </TableCell>
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
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </template>
  </div>
</template>
