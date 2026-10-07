<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { Download, Loader2, RotateCcw } from '@lucide/vue'
import {
  getBackfillRuns,
  getCandles,
  getCoverage,
  HISTORY_TIMEFRAMES,
  historyKeys,
  retryBackfill,
  startBackfill,
  type BackfillRunResponse,
} from '@/api/history'
import { getWatchlist, watchlistKeys } from '@/api/watchlist'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'
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
import HistoricalTable from '@/components/market-data/HistoricalTable.vue'
import SectionState from '@/components/market-data/SectionState.vue'
import SegmentedTabs from '@/components/common/SegmentedTabs.vue'
import { formatAge, formatCompact, formatIstDateTime } from '@/lib/format'
import { exchangeDateToInstant } from '@/lib/market-time'

const queryClient = useQueryClient()

const watchlistQuery = useQuery(() => ({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
  staleTime: 30_000,
  retry: 1,
}))

const M1 = 'M1'
const VIEW_LIMIT = 5000

const selectedInstrumentId = ref<number | null>(null)
// The persisted base is always M1; this selects which derived timeframe the chart shows.
const viewTimeframe = ref('D1')
const historyView = ref<'chart' | 'table'>('chart')
const historyViewTabs = [
  { value: 'chart', label: 'Chart' },
  { value: 'table', label: 'Table' },
] as const

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
  // Poll faster while a download is active, including when the tab is in the background.
  refetchInterval: (query: { state: { data?: { status?: string } } }) =>
    query.state.data?.status === 'RUNNING' ? 2_000 : 15_000,
  refetchIntervalInBackground: true,
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

const runsPage = ref(1)
const runsPageSize = ref(20)
const runsStatus = ref('ALL')
const runsOffset = computed(() => (runsPage.value - 1) * runsPageSize.value)
const RUN_STATUS_OPTIONS = [
  'ALL',
  'QUEUED',
  'RUNNING',
  'PARTIAL',
  'COMPLETED',
  'CANCELLED',
] as const

const runsQuery = useQuery(() => ({
  queryKey: historyKeys.backfillRuns(
    selectedInstrumentId.value ?? 0,
    runsStatus.value,
    runsPageSize.value,
    runsOffset.value,
  ),
  queryFn: ({ signal }) =>
    getBackfillRuns(
      selectedInstrumentId.value ?? 0,
      {
        limit: runsPageSize.value,
        offset: runsOffset.value,
        status: runsStatus.value,
      },
      signal,
    ),
  enabled: selectedInstrumentId.value !== null,
  // Poll faster only while the visible page has active work.
  refetchInterval: (query: {
    state: { data?: { items?: BackfillRunResponse[] } }
  }) =>
    (query.state.data?.items ?? []).some(
      (run) => run.status === 'RUNNING' || run.status === 'QUEUED',
    )
      ? 3_000
      : 15_000,
  staleTime: 5_000,
  retry: 1,
}))

const runs = computed(() => runsQuery.data.value?.items ?? [])
const runsTotal = computed(() => runsQuery.data.value?.total ?? 0)
const runsFirst = computed(() =>
  runsTotal.value === 0 ? 0 : runsOffset.value + 1,
)
const runsLast = computed(() =>
  Math.min(runsOffset.value + runs.value.length, runsTotal.value),
)
const runsHasPrev = computed(() => runsPage.value > 1)
const runsHasNext = computed(
  () => runsOffset.value + runsPageSize.value < runsTotal.value,
)

watch([selectedInstrumentId, runsStatus, runsPageSize], () => {
  runsPage.value = 1
})

const expandedRunKey = ref<string | null>(null)
function toggleRun(runKey: string) {
  expandedRunKey.value = expandedRunKey.value === runKey ? null : runKey
}

function prevRunsPage() {
  runsPage.value = Math.max(1, runsPage.value - 1)
}

function nextRunsPage() {
  runsPage.value += 1
}

const retryMutation = useMutation({
  mutationFn: (runKey: string) => retryBackfill(runKey),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: historyKeys.all })
  },
})

function runProgress(run: BackfillRunResponse): number {
  if (run.totalChunks <= 0) {
    return 0
  }
  return Math.round((run.completedChunks / run.totalChunks) * 100)
}

function canRetry(status: string): boolean {
  return status === 'FAILED' || status === 'PARTIAL'
}

function runDuration(run: BackfillRunResponse): string {
  const start = run.createdAt ? Date.parse(run.createdAt) : NaN
  const end = run.completedAt
    ? Date.parse(run.completedAt)
    : Date.parse(run.updatedAt ?? '')
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) {
    return '—'
  }
  const seconds = Math.round((end - start) / 1000)
  if (seconds < 60) {
    return `${seconds}s`
  }
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) {
    return `${minutes}m ${seconds % 60}s`
  }
  const hours = Math.floor(minutes / 60)
  return `${hours}h ${minutes % 60}m`
}

const persistedCandles = computed(() => candlesQuery.data.value ?? [])
const candlesCapped = computed(
  () => persistedCandles.value.length >= VIEW_LIMIT,
)
const partialCandles = computed(
  () => persistedCandles.value.filter((candle) => candle.partial).length,
)
const incompleteCandles = computed(
  () =>
    persistedCandles.value.filter((c) => c.qualityState === 'INCOMPLETE')
      .length,
)
const coverage = computed(() => coverageQuery.data.value ?? null)
const plannedChunks = computed(() =>
  coverage.value
    ? coverage.value.completedChunks +
      coverage.value.pendingChunks +
      coverage.value.failedChunks
    : 0,
)
const coverageProgress = computed(() => {
  if (!coverage.value || plannedChunks.value <= 0) {
    return coverage.value?.status === 'COMPLETE' ? 100 : 0
  }
  return Math.round(
    (coverage.value.completedChunks / plannedChunks.value) * 100,
  )
})

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

const isDownloading = computed(
  () => startMutation.isPending.value || coverage.value?.status === 'RUNNING',
)

function progressClass(status: string): string {
  if (status === 'COMPLETE' || status === 'COMPLETED') {
    return '[&>[data-slot=progress-indicator]]:bg-emerald-500'
  }
  if (status === 'PARTIAL' || status === 'FAILED') {
    return '[&>[data-slot=progress-indicator]]:bg-destructive'
  }
  return ''
}

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

    <div class="grid gap-4 lg:grid-cols-2">
      <Card>
        <CardHeader class="pb-3">
          <CardTitle class="text-sm font-medium">Download</CardTitle>
        </CardHeader>
        <CardContent class="space-y-3">
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

          <div class="grid grid-cols-2 gap-3">
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
              <Input
                id="download-to-date"
                v-model="downloadToDate"
                type="date"
              />
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-1">
            <Button
              v-for="preset in DOWNLOAD_PRESETS"
              :key="preset.label"
              variant="outline"
              size="sm"
              @click="applyDownloadPreset(preset.years)"
            >
              {{ preset.label }}
            </Button>
            <Button
              class="ml-auto"
              :disabled="selectedInstrumentId === null || isDownloading"
              @click="startMutation.mutate()"
            >
              <Loader2
                v-if="isDownloading"
                class="size-4 animate-spin"
                aria-hidden="true"
              />
              <Download v-else class="size-4" aria-hidden="true" />
              {{ isDownloading ? 'Downloading…' : 'Download / resume' }}
            </Button>
          </div>

          <SectionState
            v-if="startMutation.isError.value"
            title="Could not start download"
            :error="startMutation.error.value"
            @retry="startMutation.mutate()"
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="pb-3">
          <CardTitle class="text-sm font-medium">M1 coverage</CardTitle>
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
              <span
                v-if="coverage.status === 'RUNNING'"
                class="flex items-center gap-1 text-xs font-medium"
              >
                <Loader2 class="size-3.5 animate-spin" aria-hidden="true" />
                Downloading…
              </span>
              <span class="text-xs text-muted-foreground tabular-nums">
                {{ formatCompact(coverage.candleCount) }} candles
              </span>
              <span
                class="ml-auto text-xs text-muted-foreground tabular-nums"
                :title="
                  coverage.lastSyncedAt
                    ? `Last synced ${formatIstDateTime(coverage.lastSyncedAt)}`
                    : undefined
                "
              >
                {{
                  coverage.lastSyncedAt ? formatAge(coverage.lastSyncedAt) : '—'
                }}
              </span>
            </div>
            <Progress
              v-if="coverage.status === 'RUNNING'"
              :model-value="coverageProgress"
              :class="progressClass(coverage.status)"
              :aria-label="`Downloading — ${coverageProgress}%`"
              :title="`Downloading — ${coverageProgress}%`"
            />
            <p
              v-if="coverage.status === 'RUNNING'"
              class="text-xs text-muted-foreground"
            >
              Downloading…
            </p>
            <dl class="grid grid-cols-2 gap-x-6 text-sm">
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
            </dl>
            <p
              v-if="coverage.failedChunks > 0"
              class="text-xs text-muted-foreground"
            >
              Some sessions failed — retry from the Backfill runs table.
            </p>
          </template>
        </CardContent>
      </Card>
    </div>

    <Card>
      <CardHeader
        class="flex flex-wrap items-center justify-between gap-2 pb-3"
      >
        <CardTitle class="text-sm font-medium">Backfill runs</CardTitle>
        <div class="flex flex-wrap items-center gap-2">
          <Select v-model="runsStatus">
            <SelectTrigger class="w-[140px]" aria-label="Run status filter">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem
                v-for="option in RUN_STATUS_OPTIONS"
                :key="option"
                :value="option"
              >
                {{ option === 'ALL' ? 'All statuses' : option }}
              </SelectItem>
            </SelectContent>
          </Select>
          <Select v-model="runsPageSize">
            <SelectTrigger class="w-[120px]" aria-label="Runs per page">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem :value="10">10 / page</SelectItem>
              <SelectItem :value="20">20 / page</SelectItem>
              <SelectItem :value="50">50 / page</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </CardHeader>
      <CardContent>
        <SectionState
          v-if="runsQuery.isError.value"
          title="Backfill runs unavailable"
          :error="runsQuery.error.value"
          @retry="runsQuery.refetch()"
        />
        <p
          v-else-if="selectedInstrumentId === null"
          class="text-sm text-muted-foreground"
        >
          No instrument selected.
        </p>
        <div v-else-if="runsQuery.isPending.value" class="space-y-2">
          <Skeleton v-for="n in 3" :key="n" class="h-9 w-full" />
        </div>
        <p v-else-if="runs.length === 0" class="text-sm text-muted-foreground">
          No backfill runs{{
            runsStatus === 'ALL' ? '' : ` with status ${runsStatus}`
          }}.
        </p>
        <template v-else>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Range</TableHead>
                <TableHead>Status</TableHead>
                <TableHead class="text-right">Candles</TableHead>
                <TableHead class="text-right">Duration</TableHead>
                <TableHead>Completed</TableHead>
                <TableHead class="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <template v-for="run in runs" :key="run.runKey">
                <TableRow>
                  <TableCell class="text-xs text-muted-foreground">
                    {{ formatIstDateTime(run.requestedFrom) }} →
                    {{ formatIstDateTime(run.requestedTo) }}
                  </TableCell>
                  <TableCell>
                    <div class="space-y-1">
                      <Badge :variant="statusVariant(run.status)">{{
                        run.status
                      }}</Badge>
                      <span
                        v-if="run.failedChunks"
                        class="block text-xs text-negative"
                      >
                        {{ run.failedChunks }} failed
                      </span>
                      <Progress
                        v-if="
                          run.status === 'RUNNING' || run.status === 'QUEUED'
                        "
                        :model-value="runProgress(run)"
                        class="h-1 w-20"
                        :aria-label="`Progress ${runProgress(run)}%`"
                      />
                    </div>
                  </TableCell>
                  <TableCell class="text-right tabular-nums">{{
                    formatCompact(run.candlesWritten)
                  }}</TableCell>
                  <TableCell class="text-right tabular-nums">{{
                    runDuration(run)
                  }}</TableCell>
                  <TableCell class="text-muted-foreground">
                    {{
                      run.completedAt
                        ? formatIstDateTime(run.completedAt)
                        : formatIstDateTime(run.updatedAt)
                    }}
                  </TableCell>
                  <TableCell class="text-right whitespace-nowrap">
                    <Button
                      variant="ghost"
                      size="sm"
                      @click="toggleRun(run.runKey)"
                    >
                      {{ expandedRunKey === run.runKey ? 'Hide' : 'Details' }}
                    </Button>
                    <Button
                      v-if="canRetry(run.status)"
                      variant="outline"
                      size="sm"
                      :disabled="retryMutation.isPending.value"
                      @click="retryMutation.mutate(run.runKey)"
                    >
                      <RotateCcw class="mr-1 size-3.5" aria-hidden="true" />
                      Retry
                    </Button>
                  </TableCell>
                </TableRow>
                <TableRow v-if="expandedRunKey === run.runKey">
                  <TableCell colspan="6" class="bg-muted/40">
                    <dl
                      class="grid grid-cols-2 gap-x-6 gap-y-2 text-xs sm:grid-cols-4"
                    >
                      <div>
                        <dt class="text-muted-foreground">Updated</dt>
                        <dd class="tabular-nums">
                          {{ formatIstDateTime(run.updatedAt) }}
                        </dd>
                      </div>
                      <div>
                        <dt class="text-muted-foreground">Completed</dt>
                        <dd class="tabular-nums">
                          {{
                            run.completedAt
                              ? formatIstDateTime(run.completedAt)
                              : '—'
                          }}
                        </dd>
                      </div>
                      <div>
                        <dt class="text-muted-foreground">Chunks</dt>
                        <dd class="tabular-nums">
                          {{ run.completedChunks }} / {{ run.totalChunks }} ({{
                            run.failedChunks
                          }}
                          failed)
                        </dd>
                      </div>
                      <div class="min-w-0">
                        <dt class="text-muted-foreground">Run key</dt>
                        <dd class="truncate font-mono">{{ run.runKey }}</dd>
                      </div>
                      <div class="col-span-full">
                        <dt class="text-muted-foreground">Last error</dt>
                        <dd :class="run.lastError ? 'text-negative' : ''">
                          {{ run.lastError ?? '—' }}
                        </dd>
                      </div>
                    </dl>
                  </TableCell>
                </TableRow>
              </template>
            </TableBody>
          </Table>
          <div
            class="mt-3 flex items-center justify-between text-xs text-muted-foreground"
          >
            <span class="tabular-nums">
              Showing {{ runsFirst }}–{{ runsLast }} of {{ runsTotal }}
            </span>
            <div class="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                :disabled="!runsHasPrev"
                @click="prevRunsPage"
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                :disabled="!runsHasNext"
                @click="nextRunsPage"
              >
                Next
              </Button>
            </div>
          </div>
        </template>
      </CardContent>
    </Card>

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
                  v-for="option in HISTORY_TIMEFRAMES"
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

        <div class="flex items-center">
          <SegmentedTabs v-model="historyView" :tabs="historyViewTabs" />
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
            {{ viewTimeframe === 'M1' ? 'M1' : `derived ${viewTimeframe}` }}
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
            <span
              v-if="incompleteCandles > 0"
              class="text-amber-600 dark:text-amber-500"
            >
              · {{ incompleteCandles }} incomplete (missing minutes)
            </span>
            <span v-if="candlesCapped">
              · capped at the first {{ formatCompact(VIEW_LIMIT) }} in range
            </span>
          </p>
          <PriceChart
            v-if="historyView === 'chart'"
            :candles="persistedCandles"
          />
          <HistoricalTable v-else :candles="persistedCandles" />
        </template>
      </CardContent>
    </Card>
  </main>
</template>
