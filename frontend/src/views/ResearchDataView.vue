<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { Database, Download, RotateCw } from '@lucide/vue'
import type { BrokerCandleInterval } from '@/api/types'
import {
  getCandles,
  getCoverage,
  getRuns,
  historyKeys,
  retryRun,
  startBackfill,
  type BackfillRunResponse,
} from '@/api/history'
import { getWatchlist, watchlistKeys } from '@/api/watchlist'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import PriceChart from '@/components/market-data/PriceChart.vue'
import SectionState from '@/components/market-data/SectionState.vue'
import { formatAge, formatCompact, formatIstDateTime } from '@/lib/format'
import { exchangeDateToInstant, INTERVAL_OPTIONS } from '@/lib/market-time'

const queryClient = useQueryClient()

const watchlistQuery = useQuery(() => ({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
  staleTime: 30_000,
  retry: 1,
}))

const M1: BrokerCandleInterval = 'ONE_MINUTE'
const VIEW_LIMIT = 5000

const selectedInstrumentId = ref<number | null>(null)
// The persisted base is always M1; this selects which derived timeframe the chart shows.
const viewTimeframe = ref<BrokerCandleInterval>('ONE_DAY')

const entries = computed(() => watchlistQuery.data.value?.entries ?? [])

function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

// Persistence is limited to the active watchlist; default to its first instrument.
watch(
  entries,
  (value) => {
    if (selectedInstrumentId.value === null && value.length > 0) {
      selectedInstrumentId.value = value[0]?.instrumentId ?? null
    }
  },
  { immediate: true },
)

const coverageQuery = useQuery(() => ({
  queryKey: historyKeys.coverage(selectedInstrumentId.value ?? 0, M1),
  queryFn: ({ signal }) =>
    getCoverage(selectedInstrumentId.value ?? 0, M1, signal),
  enabled: selectedInstrumentId.value !== null,
  refetchInterval: 5_000,
  staleTime: 2_000,
  retry: 1,
}))

const runsQuery = useQuery(() => ({
  queryKey: historyKeys.runs(selectedInstrumentId.value ?? 0),
  queryFn: ({ signal }) => getRuns(selectedInstrumentId.value ?? 0, 20, signal),
  enabled: selectedInstrumentId.value !== null,
  refetchInterval: (query: { state: { data?: BackfillRunResponse[] } }) => {
    const active = (query.state.data ?? []).some(
      (run) => run.status === 'RUNNING' || run.status === 'QUEUED',
    )
    return active ? 2_000 : 10_000
  },
  staleTime: 1_000,
  retry: 1,
}))

// Download range: which M1 history to persist from the broker.
const downloadFromDate = ref('')
const downloadToDate = ref('')

const DOWNLOAD_PRESETS: { label: string; years: number | null }[] = [
  { label: '1Y', years: 1 },
  { label: '3Y', years: 3 },
  { label: '5Y', years: 5 },
  { label: 'MAX', years: null },
]

function applyDownloadPreset(years: number | null) {
  const end = new Date()
  const start =
    years === null
      ? new Date('2015-01-01T00:00:00Z')
      : new Date(end.getTime() - years * 365 * 24 * 60 * 60 * 1000)
  downloadFromDate.value = isoDate(start)
  downloadToDate.value = isoDate(end)
}

applyDownloadPreset(5)

// Chart controls: a display-only window over the persisted M1 base.
const chartFromDate = ref('')
const chartToDate = ref('')

const CHART_PRESETS: { label: string; days: number | null }[] = [
  { label: '1D', days: 1 },
  { label: '1W', days: 7 },
  { label: '1M', days: 30 },
  { label: '3M', days: 90 },
  { label: '1Y', days: 365 },
  { label: 'MAX', days: null },
]

function applyChartPreset(days: number | null) {
  const end = new Date()
  const start =
    days === null
      ? new Date('2015-01-01T00:00:00Z')
      : new Date(end.getTime() - days * 24 * 60 * 60 * 1000)
  chartFromDate.value = isoDate(start)
  chartToDate.value = isoDate(end)
}

applyChartPreset(365)

const candleRange = computed(() => ({
  from: exchangeDateToInstant(chartFromDate.value, false),
  to: exchangeDateToInstant(chartToDate.value, true),
}))

const candlesQuery = useQuery(() => ({
  queryKey: historyKeys.candles(
    selectedInstrumentId.value ?? 0,
    viewTimeframe.value,
    candleRange.value.from,
    candleRange.value.to,
  ),
  queryFn: ({ signal }) =>
    getCandles(
      selectedInstrumentId.value ?? 0,
      viewTimeframe.value,
      candleRange.value.from,
      candleRange.value.to,
      VIEW_LIMIT,
      signal,
    ),
  enabled: selectedInstrumentId.value !== null,
  staleTime: 10_000,
  retry: 1,
}))

const runs = computed(() => runsQuery.data.value ?? [])
const persistedCandles = computed(() => candlesQuery.data.value ?? [])
const candlesCapped = computed(
  () => persistedCandles.value.length >= VIEW_LIMIT,
)
const partialCandles = computed(
  () => persistedCandles.value.filter((candle) => candle.partial).length,
)
const coverage = computed(() => coverageQuery.data.value ?? null)
const plannedChunks = computed(() =>
  coverage.value
    ? coverage.value.completedChunks +
      coverage.value.pendingChunks +
      coverage.value.failedChunks
    : 0,
)

const startMutation = useMutation({
  mutationFn: () =>
    startBackfill({
      instrumentId: selectedInstrumentId.value ?? 0,
      timeframe: M1,
      from: exchangeDateToInstant(downloadFromDate.value, false),
      to: exchangeDateToInstant(downloadToDate.value, true),
    }),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: historyKeys.all })
  },
})

const retryMutation = useMutation({
  mutationFn: (runKey: string) => retryRun(runKey),
  onSuccess: () => queryClient.invalidateQueries({ queryKey: historyKeys.all }),
})

function statusVariant(
  status: string,
): 'secondary' | 'outline' | 'destructive' {
  if (status === 'COMPLETE' || status === 'COMPLETED') {
    return 'secondary'
  }
  if (status === 'PARTIAL' || status === 'FAILED') {
    return 'destructive'
  }
  return 'outline'
}
</script>

<template>
  <main
    class="mx-auto w-full max-w-[1600px] flex-1 space-y-4 px-4 py-6 lg:px-6"
  >
    <div class="space-y-1">
      <h1 class="text-2xl font-semibold tracking-tight">Research Data</h1>
      <p class="text-sm text-muted-foreground">
        Persist the canonical M1 base for the active watchlist, then derive
        higher timeframes from it. Gap-aware, idempotent and resumable; the same
        aggregation feeds live, replay and backtests.
      </p>
    </div>

    <Card>
      <CardHeader class="pb-3">
        <CardTitle class="text-sm font-medium">Download</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="grid gap-4 lg:grid-cols-[2fr_1fr_1fr]">
          <div class="space-y-1.5">
            <Label>Instrument (watchlist)</Label>
            <Select v-model="selectedInstrumentId">
              <SelectTrigger aria-label="Instrument">
                <SelectValue placeholder="Select a watched instrument" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="entry in entries"
                  :key="entry.instrumentId"
                  :value="entry.instrumentId"
                >
                  {{ entry.symbol }}
                  <span v-if="entry.name" class="text-muted-foreground">
                    · {{ entry.name }}
                  </span>
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="space-y-1.5">
            <Label for="download-from-date">From</Label>
            <Input
              id="download-from-date"
              v-model="downloadFromDate"
              type="date"
            />
          </div>
          <div class="space-y-1.5">
            <Label for="download-to-date">To</Label>
            <Input id="download-to-date" v-model="downloadToDate" type="date" />
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <Button
            v-for="preset in DOWNLOAD_PRESETS"
            :key="preset.label"
            variant="outline"
            size="sm"
            @click="applyDownloadPreset(preset.years)"
          >
            {{ preset.label }}
          </Button>
          <div class="ml-auto">
            <Button
              :disabled="
                selectedInstrumentId === null || startMutation.isPending.value
              "
              @click="startMutation.mutate()"
            >
              <Download class="size-4" aria-hidden="true" />
              Download / resume
            </Button>
          </div>
        </div>

        <SectionState
          v-if="startMutation.isError.value"
          title="Could not start download"
          :error="startMutation.error.value"
          @retry="startMutation.mutate()"
        />
      </CardContent>
    </Card>

    <div class="grid gap-4 lg:grid-cols-2">
      <Card>
        <CardHeader class="pb-3">
          <CardTitle class="text-sm font-medium">M1 base coverage</CardTitle>
        </CardHeader>
        <CardContent class="space-y-3">
          <SectionState
            v-if="coverageQuery.isError.value"
            title="Coverage unavailable"
            :error="coverageQuery.error.value"
            @retry="coverageQuery.refetch()"
          />
          <div
            v-else-if="coverageQuery.isPending.value && selectedInstrumentId"
            class="space-y-2"
          >
            <Skeleton class="h-6 w-40" />
            <Skeleton class="h-6 w-full" />
          </div>
          <p
            v-else-if="selectedInstrumentId === null"
            class="text-sm text-muted-foreground"
          >
            No instrument selected.
          </p>
          <template v-else-if="coverage">
            <div class="flex items-center gap-2">
              <Badge :variant="statusVariant(coverage.status)">{{
                coverage.status
              }}</Badge>
              <span class="text-xs text-muted-foreground tabular-nums">
                {{ formatCompact(coverage.candleCount) }} candles
              </span>
            </div>
            <dl class="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
              <div>
                <dt class="text-xs text-muted-foreground">Earliest</dt>
                <dd class="tabular-nums">
                  {{ formatIstDateTime(coverage.earliest) }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-muted-foreground">Latest</dt>
                <dd class="tabular-nums">
                  {{ formatIstDateTime(coverage.latest) }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-muted-foreground">
                  Chunks (done / pending / failed)
                </dt>
                <dd class="tabular-nums">
                  {{ coverage.completedChunks }} /
                  {{ coverage.pendingChunks }} /
                  {{ coverage.failedChunks }}
                  <span class="text-muted-foreground">
                    ({{ plannedChunks }} planned)
                  </span>
                </dd>
              </div>
              <div>
                <dt class="text-xs text-muted-foreground">Last synced</dt>
                <dd class="tabular-nums">
                  {{
                    coverage.lastSyncedAt
                      ? formatAge(coverage.lastSyncedAt)
                      : '—'
                  }}
                </dd>
              </div>
            </dl>
          </template>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="pb-3">
          <CardTitle class="text-sm font-medium">Backfill runs</CardTitle>
        </CardHeader>
        <CardContent>
          <SectionState
            v-if="runsQuery.isError.value"
            title="Runs unavailable"
            :error="runsQuery.error.value"
            @retry="runsQuery.refetch()"
          />
          <p
            v-else-if="runs.length === 0"
            class="flex items-center gap-2 py-8 text-sm text-muted-foreground"
          >
            <Database class="size-4" aria-hidden="true" />
            No backfill runs yet.
          </p>
          <div v-else class="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Range</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead class="text-right">Progress</TableHead>
                  <TableHead class="text-right">Candles</TableHead>
                  <TableHead class="text-right">Updated</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-for="run in runs" :key="run.runKey">
                  <TableCell class="whitespace-nowrap text-xs tabular-nums">
                    {{ formatIstDateTime(run.requestedFrom) }} →
                    {{ formatIstDateTime(run.requestedTo) }}
                  </TableCell>
                  <TableCell>
                    <Badge :variant="statusVariant(run.status)">{{
                      run.status
                    }}</Badge>
                  </TableCell>
                  <TableCell class="text-right tabular-nums">
                    {{ run.completedChunks }} / {{ run.totalChunks }}
                  </TableCell>
                  <TableCell class="text-right tabular-nums">
                    {{ formatCompact(run.candlesWritten) }}
                  </TableCell>
                  <TableCell
                    class="text-right text-xs text-muted-foreground tabular-nums"
                  >
                    {{ formatAge(run.updatedAt) }}
                  </TableCell>
                  <TableCell class="text-right">
                    <Button
                      v-if="run.status === 'PARTIAL' || run.status === 'FAILED'"
                      variant="outline"
                      size="sm"
                      @click="retryMutation.mutate(run.runKey)"
                    >
                      <RotateCw class="size-3.5" aria-hidden="true" />
                      Retry
                    </Button>
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>
    </div>

    <Card>
      <CardHeader class="pb-3">
        <CardTitle class="text-sm font-medium"
          >Persisted history (database)</CardTitle
        >
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="flex flex-wrap items-end gap-3">
          <div class="w-44 space-y-1.5">
            <Label>Timeframe</Label>
            <Select v-model="viewTimeframe">
              <SelectTrigger aria-label="Timeframe">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="option in INTERVAL_OPTIONS"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div class="flex flex-wrap items-center gap-1">
            <Button
              v-for="preset in CHART_PRESETS"
              :key="preset.label"
              variant="outline"
              size="sm"
              @click="applyChartPreset(preset.days)"
            >
              {{ preset.label }}
            </Button>
          </div>

          <div class="ml-auto flex items-end gap-2">
            <div class="space-y-1.5">
              <Label for="chart-from-date">From</Label>
              <Input id="chart-from-date" v-model="chartFromDate" type="date" />
            </div>
            <div class="space-y-1.5">
              <Label for="chart-to-date">To</Label>
              <Input id="chart-to-date" v-model="chartToDate" type="date" />
            </div>
          </div>
        </div>

        <SectionState
          v-if="candlesQuery.isError.value"
          title="Persisted history unavailable"
          :error="candlesQuery.error.value"
          @retry="candlesQuery.refetch()"
        />
        <p
          v-else-if="selectedInstrumentId === null"
          class="text-sm text-muted-foreground"
        >
          No instrument selected.
        </p>
        <div v-else-if="candlesQuery.isPending.value" class="space-y-2">
          <Skeleton class="h-[320px] w-full" />
          <Skeleton class="h-6 w-64" />
        </div>
        <p
          v-else-if="persistedCandles.length === 0"
          class="text-sm text-muted-foreground"
        >
          No persisted candles for this instrument, timeframe and range.
          Download M1 above, then the charts appear here.
        </p>
        <template v-else>
          <p class="text-xs text-muted-foreground tabular-nums">
            {{ formatCompact(persistedCandles.length) }}
            {{
              viewTimeframe === 'ONE_MINUTE' ? 'M1' : `derived ${viewTimeframe}`
            }}
            candles ·
            {{ formatIstDateTime(persistedCandles[0]?.openTime) }} →
            {{
              formatIstDateTime(
                persistedCandles[persistedCandles.length - 1]?.openTime,
              )
            }}
            <span v-if="partialCandles > 0">
              · {{ partialCandles }} partial session bar(s)
            </span>
            <span v-if="candlesCapped">
              · capped at the first {{ formatCompact(VIEW_LIMIT) }} in range
            </span>
          </p>
          <PriceChart :candles="persistedCandles" />
        </template>
      </CardContent>
    </Card>
  </main>
</template>
