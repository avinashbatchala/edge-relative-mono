<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { MoreHorizontal, RefreshCw } from '@lucide/vue'
import {
  featureKeys,
  getFeatureDashboard,
  getFeatureDiagnostics,
  type FeatureDashboardRow,
} from '@/api/features'
import { useFeatureStreamStore } from '@/stores/feature-stream'
import { useFeatureStream } from '@/composables/useFeatureStream'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import {
  formatAge,
  formatIstDateTime,
  formatPercent,
  movementClass,
} from '@/lib/format'
import {
  directionMetaFromState,
  featureVersionTitle,
  formatFeatureVersion,
  isStale,
  isTrustworthy,
  metricDecimals,
  metricLabel,
  presentationState,
  qualityRank,
  structureMeta,
} from '@/lib/feature-presentation'
import FeatureValueCell from '@/components/feature/FeatureValueCell.vue'
import FeatureTrustCell from '@/components/feature/FeatureTrustCell.vue'
import FeatureDiagnosticsPanel from '@/components/feature/FeatureDiagnosticsPanel.vue'
import FeatureHistoryPanel from '@/components/feature/FeatureHistoryPanel.vue'
import FeatureFilterBar from '@/components/feature/FeatureFilterBar.vue'
import FeatureColumnSelector from '@/components/feature/FeatureColumnSelector.vue'
import FeatureSummaryCards from '@/components/feature/FeatureSummaryCards.vue'
import ConnectionStatus from '@/components/feature/ConnectionStatus.vue'

type SortKey =
  | 'symbol'
  | 'price'
  | 'changePct'
  | 'rrsRaw'
  | 'rrsAbs'
  | 'rrsPersistence'
  | 'rvolInterval'
  | 'rve'
  | 'age'
  | 'quality'

interface Column {
  id: string
  label: string
  accessibleLabel?: string
  group: string
  primary: boolean
  numeric?: boolean
  sortable?: SortKey
  metric?: string
  hint?: string
}

const COLUMNS: Column[] = [
  {
    id: 'symbol',
    label: 'Instrument',
    group: 'Instrument',
    primary: true,
    sortable: 'symbol',
  },
  {
    id: 'price',
    label: 'Last',
    accessibleLabel: 'Last price',
    group: 'Price',
    primary: true,
    numeric: true,
    sortable: 'price',
    metric: 'LAST_PRICE',
    hint: 'Latest canonical close for the timeframe',
  },
  {
    id: 'changePct',
    label: 'Chg %',
    accessibleLabel: 'Percent change',
    group: 'Price',
    primary: true,
    numeric: true,
    sortable: 'changePct',
    metric: 'PRICE_CHANGE_PERCENT',
    hint: 'Change versus the previous session close',
  },
  {
    id: 'rrsRaw',
    label: 'RRS',
    accessibleLabel: 'Real relative strength',
    group: 'Relative strength',
    primary: true,
    numeric: true,
    sortable: 'rrsRaw',
    metric: 'RRS_RAW',
    hint: 'Real Relative Strength: volatility-normalised excess movement versus the broad benchmark',
  },
  {
    id: 'rrsFast',
    label: 'Fast',
    accessibleLabel: 'RRS fast smoothing',
    group: 'Relative strength',
    primary: false,
    numeric: true,
    metric: 'RRS_FAST',
    hint: 'Fast EMA of RRS raw',
  },
  {
    id: 'rrsSlow',
    label: 'Slow',
    accessibleLabel: 'RRS slow smoothing',
    group: 'Relative strength',
    primary: false,
    numeric: true,
    metric: 'RRS_SLOW',
    hint: 'Slow EMA of RRS raw',
  },
  {
    id: 'rrsPersistence',
    label: 'Persist',
    accessibleLabel: 'RRS persistence',
    group: 'Relative strength',
    primary: false,
    numeric: true,
    sortable: 'rrsPersistence',
    metric: 'RRS_PERSISTENCE',
    hint: 'Share of recent bars agreeing with the RRS sign',
  },
  {
    id: 'dailyRrs',
    label: 'Daily',
    accessibleLabel: 'Daily RRS direction',
    group: 'Relative strength',
    primary: false,
    hint: 'Higher-timeframe RRS direction',
  },
  {
    id: 'rvolInterval',
    label: 'RVOL',
    accessibleLabel: 'Relative volume',
    group: 'Participation',
    primary: true,
    numeric: true,
    sortable: 'rvolInterval',
    metric: 'RVOL_INTERVAL',
    hint: 'Interval RVOL: current slot versus the same slot in prior sessions',
  },
  {
    id: 'rvolCumulative',
    label: 'Cum',
    accessibleLabel: 'Cumulative relative volume',
    group: 'Participation',
    primary: false,
    numeric: true,
    metric: 'RVOL_CUMULATIVE',
    hint: 'Cumulative RVOL to the same session-relative time',
  },
  {
    id: 'rve',
    label: 'RVE',
    accessibleLabel: 'Relative volume expansion',
    group: 'Participation',
    primary: true,
    numeric: true,
    sortable: 'rve',
    metric: 'RVE',
    hint: 'Relative Volume Expansion: fast minus slow log RVOL',
  },
  {
    id: 'atrPct',
    label: 'ATR %',
    accessibleLabel: 'ATR percent',
    group: 'Volatility',
    primary: false,
    numeric: true,
    metric: 'ATR_PERCENT',
    hint: 'ATR as a percentage of price',
  },
  {
    id: 'vwapDist',
    label: 'VWAP/ATR',
    accessibleLabel: 'VWAP distance in ATR units',
    group: 'Volatility',
    primary: false,
    numeric: true,
    metric: 'VWAP_DISTANCE_ATR',
    hint: 'Distance from VWAP in ATR units',
  },
  {
    id: 'market',
    label: 'Market',
    accessibleLabel: 'Market price structure',
    group: 'Context',
    primary: false,
    hint: 'Broad-market price structure',
  },
  {
    id: 'sector',
    label: 'Sector',
    accessibleLabel: 'Sector price structure',
    group: 'Context',
    primary: false,
    hint: 'Sector price structure',
  },
  {
    id: 'quality',
    label: 'Trust',
    accessibleLabel: 'Feature quality and trust',
    group: 'Health',
    primary: true,
    sortable: 'quality',
    hint: 'Aggregate feature quality and availability',
  },
  {
    id: 'age',
    label: 'Fresh',
    accessibleLabel: 'Data freshness',
    group: 'Health',
    primary: true,
    numeric: true,
    sortable: 'age',
    hint: 'Age since the observation timestamp',
  },
  {
    id: 'version',
    label: 'Version',
    accessibleLabel: 'Feature version',
    group: 'Health',
    primary: false,
    hint: 'Active RRS feature version',
  },
]

const router = useRouter()
const store = useFeatureStreamStore()
useFeatureStream()

const dashboardQuery = useQuery(() => ({
  queryKey: featureKeys.dashboard(),
  queryFn: ({ signal }) => getFeatureDashboard(signal),
  staleTime: 15_000,
  refetchInterval: 60_000,
}))

const diagnosticsQuery = useQuery(() => ({
  queryKey: featureKeys.diagnostics(),
  queryFn: ({ signal }) => getFeatureDiagnostics(signal),
  staleTime: 15_000,
}))

watch(
  () => dashboardQuery.data.value,
  (rows) => {
    if (rows && store.lastSequence === null) {
      store.setAuthoritative(rows)
    }
  },
  { immediate: true },
)

const search = ref('')
const timeframeFilter = ref('all')
const rsFilter = ref('all')
const rveFilter = ref('all')
const alignmentFilter = ref('all')
const qualityFilter = ref('all')
const freshnessFilter = ref('all')
const minRvol = ref('')

const sortKey = ref<SortKey>('rrsAbs')
const sortDir = ref<'asc' | 'desc'>('desc')
const visible = ref<Record<string, boolean>>(
  Object.fromEntries(COLUMNS.map((column) => [column.id, column.primary])),
)

const selected = ref<FeatureDashboardRow | null>(null)

function matches(row: FeatureDashboardRow): boolean {
  if (
    search.value &&
    !`${row.symbol} ${row.displayName ?? ''}`
      .toLowerCase()
      .includes(search.value.toLowerCase())
  ) {
    return false
  }
  if (
    timeframeFilter.value !== 'all' &&
    row.timeframe !== timeframeFilter.value
  ) {
    return false
  }
  if (rsFilter.value !== 'all') {
    if (row.rrsRaw === null) {
      return false
    }
    if (rsFilter.value === 'positive' && row.rrsRaw <= 0) {
      return false
    }
    if (rsFilter.value === 'negative' && row.rrsRaw >= 0) {
      return false
    }
    if (rsFilter.value === 'neutral' && row.rrsRaw !== 0) {
      return false
    }
  }
  if (rveFilter.value !== 'all') {
    if (row.rve === null) {
      return false
    }
    if (rveFilter.value === 'expanding' && !(row.rve > 0)) {
      return false
    }
    if (rveFilter.value === 'contracting' && !(row.rve < 0)) {
      return false
    }
    if (rveFilter.value === 'stable' && row.rve !== 0) {
      return false
    }
  }
  const minRvolValue = minRvol.value === '' ? null : Number(minRvol.value)
  if (
    minRvolValue !== null &&
    Number.isFinite(minRvolValue) &&
    (row.rvolInterval === null || row.rvolInterval < minRvolValue)
  ) {
    return false
  }
  if (alignmentFilter.value !== 'all') {
    const marketAligned =
      row.marketState === 'BULL_STRUCTURE'
        ? (row.rrsRaw ?? 0) > 0
        : row.marketState === 'BEAR_STRUCTURE'
          ? (row.rrsRaw ?? 0) < 0
          : false
    const sectorAligned =
      row.sectorState === 'BULL_STRUCTURE'
        ? (row.rrsRaw ?? 0) > 0
        : row.sectorState === 'BEAR_STRUCTURE'
          ? (row.rrsRaw ?? 0) < 0
          : false
    if (alignmentFilter.value === 'market' && !marketAligned) {
      return false
    }
    if (alignmentFilter.value === 'sector' && !sectorAligned) {
      return false
    }
    if (alignmentFilter.value === 'both' && !(marketAligned && sectorAligned)) {
      return false
    }
  }
  const trustworthy = isTrustworthy(row.quality, row.availability)
  if (qualityFilter.value === 'trustworthy' && !trustworthy) {
    return false
  }
  if (qualityFilter.value === 'degraded' && trustworthy) {
    return false
  }
  if (qualityFilter.value === 'unavailable' && row.availability === 'VALID') {
    return false
  }
  const stale = isStale(row.quality, row.availability)
  if (freshnessFilter.value === 'fresh' && stale) {
    return false
  }
  if (freshnessFilter.value === 'stale' && !stale) {
    return false
  }
  return true
}

const filtered = computed(() => store.rowList.filter(matches))

function sortValue(row: FeatureDashboardRow): number | string | null {
  switch (sortKey.value) {
    case 'symbol':
      return row.symbol
    case 'price':
      return row.lastPrice
    case 'changePct':
      return row.priceChangePercent
    case 'rrsRaw':
      return row.rrsRaw
    case 'rrsAbs':
      return row.rrsRaw === null ? null : Math.abs(row.rrsRaw)
    case 'rrsPersistence':
      return row.rrsPersistence
    case 'rvolInterval':
      return row.rvolInterval
    case 'rve':
      return row.rve
    case 'age':
      return row.staleSeconds
    case 'quality':
      return qualityRank(row.quality)
  }
}

function compare(a: FeatureDashboardRow, b: FeatureDashboardRow): number {
  const left = sortValue(a)
  const right = sortValue(b)
  if (left === null && right === null) {
    return 0
  }
  if (left === null) {
    return 1
  }
  if (right === null) {
    return -1
  }
  const result =
    typeof left === 'number' && typeof right === 'number'
      ? left - right
      : String(left).localeCompare(String(right))
  return sortDir.value === 'asc' ? result : -result
}

const frozenKeys = ref<string[]>([])
const filterSignature = computed(() =>
  [
    search.value,
    timeframeFilter.value,
    rsFilter.value,
    rveFilter.value,
    alignmentFilter.value,
    qualityFilter.value,
    freshnessFilter.value,
    minRvol.value,
    sortKey.value,
    sortDir.value,
  ].join('|'),
)

watch(
  [filtered, filterSignature],
  () => {
    frozenKeys.value = [...filtered.value].sort(compare).map(rowKey)
  },
  { immediate: true, deep: false },
)

const rowsByKey = computed(() => {
  const map: Record<string, FeatureDashboardRow> = {}
  for (const row of store.rowList) {
    map[rowKey(row)] = row
  }
  return map
})

const displayRows = computed(() =>
  frozenKeys.value
    .map((key) => rowsByKey.value[key])
    .filter(
      (row): row is FeatureDashboardRow => row !== undefined && matches(row),
    ),
)

const visibleColumns = computed(() =>
  COLUMNS.filter((column) => visible.value[column.id]),
)

const selectableColumns = computed(() =>
  COLUMNS.filter((column) => column.id !== 'symbol').map((column) => ({
    id: column.id,
    label: column.label,
  })),
)

function isGroupStart(index: number): boolean {
  if (index === 0) {
    return false
  }
  return (
    visibleColumns.value[index]?.group !==
    visibleColumns.value[index - 1]?.group
  )
}

function ariaSort(
  column: Column,
): 'ascending' | 'descending' | 'none' | undefined {
  if (!column.sortable) {
    return undefined
  }
  if (sortKey.value !== column.sortable) {
    return 'none'
  }
  return sortDir.value === 'asc' ? 'ascending' : 'descending'
}

function rowKey(row: FeatureDashboardRow): string {
  return `${row.instrumentId}:${row.timeframe}`
}

function isSelected(row: FeatureDashboardRow): boolean {
  return (
    selected.value?.instrumentId === row.instrumentId &&
    selected.value?.timeframe === row.timeframe
  )
}

function stateFor(row: FeatureDashboardRow): string {
  const instrument = diagnosticsQuery.data.value?.instruments.find(
    (candidate) => candidate.instrumentId === row.instrumentId,
  )
  return instrument
    ? instrument.state
    : presentationState(row.quality, row.availability)
}

function onSort(column: Column) {
  if (!column.sortable) {
    return
  }
  if (sortKey.value === column.sortable) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = column.sortable
    sortDir.value = column.sortable === 'symbol' ? 'asc' : 'desc'
  }
}

function clearFilters() {
  search.value = ''
  timeframeFilter.value = 'all'
  rsFilter.value = 'all'
  rveFilter.value = 'all'
  alignmentFilter.value = 'all'
  qualityFilter.value = 'all'
  freshnessFilter.value = 'all'
  minRvol.value = ''
}

function clearFilter(key: string) {
  switch (key) {
    case 'search':
      search.value = ''
      break
    case 'timeframe':
      timeframeFilter.value = 'all'
      break
    case 'rs':
      rsFilter.value = 'all'
      break
    case 'rve':
      rveFilter.value = 'all'
      break
    case 'alignment':
      alignmentFilter.value = 'all'
      break
    case 'quality':
      qualityFilter.value = 'all'
      break
    case 'freshness':
      freshnessFilter.value = 'all'
      break
    case 'minRvol':
      minRvol.value = ''
      break
  }
}

const activeFilters = computed(() => {
  const filters: { key: string; label: string }[] = []
  if (search.value) {
    filters.push({ key: 'search', label: `Search: ${search.value}` })
  }
  if (timeframeFilter.value !== 'all') {
    filters.push({
      key: 'timeframe',
      label: `Timeframe: ${timeframeFilter.value}`,
    })
  }
  if (rsFilter.value !== 'all') {
    filters.push({ key: 'rs', label: `Relative strength: ${rsFilter.value}` })
  }
  if (rveFilter.value !== 'all') {
    filters.push({ key: 'rve', label: `RVE: ${rveFilter.value}` })
  }
  if (alignmentFilter.value !== 'all') {
    filters.push({
      key: 'alignment',
      label: `Alignment: ${alignmentFilter.value}`,
    })
  }
  if (qualityFilter.value !== 'all') {
    filters.push({ key: 'quality', label: `Quality: ${qualityFilter.value}` })
  }
  if (freshnessFilter.value !== 'all') {
    filters.push({
      key: 'freshness',
      label: `Freshness: ${freshnessFilter.value}`,
    })
  }
  if (minRvol.value !== '') {
    filters.push({ key: 'minRvol', label: `RVOL ≥ ${minRvol.value}` })
  }
  return filters
})

function featureHref(row: FeatureDashboardRow): string {
  return router.resolve({
    name: 'feature-ticker',
    params: { symbol: row.symbol },
  }).href
}

/** A trader tracks several names at once: every stock opens in its own tab. */
function openInNewTab(row: FeatureDashboardRow) {
  window.open(featureHref(row), '_blank', 'noopener,noreferrer')
}

function openMarketDataInNewTab(row: FeatureDashboardRow) {
  window.open(`/market/${row.symbol}`, '_blank', 'noopener,noreferrer')
}

function metricLabels(row: FeatureDashboardRow): string[] {
  return Object.keys(row.unavailableReasons).map(metricLabel)
}

function resync() {
  store.setAuthoritative(store.rowList)
  diagnosticsQuery.refetch()
  dashboardQuery.refetch()
}

const hasRows = computed(() => store.rowList.length > 0)
const showingEmptyWatchlist = computed(
  () => !dashboardQuery.isPending.value && !hasRows.value,
)
</script>

<template>
  <div class="flex flex-1 flex-col gap-4 p-4 lg:p-6">
    <header class="flex flex-wrap items-start justify-between gap-3">
      <div class="space-y-1">
        <h1 class="text-xl font-semibold tracking-tight">Feature Dashboard</h1>
        <p class="max-w-2xl text-sm text-muted-foreground">
          Point-in-time relative strength, participation and volatility for the
          active watchlist. Observational only.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <ConnectionStatus
          :connection="store.connection"
          :gap-detected="store.gapDetected"
          :last-updated-at="store.lastUpdatedAt"
        />
        <Button variant="outline" size="sm" @click="resync">
          <RefreshCw class="mr-1 size-3.5" aria-hidden="true" /> Refresh
        </Button>
      </div>
    </header>

    <FeatureSummaryCards :rows="store.rowList" timeframe="M5" />

    <Card class="gap-0 overflow-hidden py-0">
      <CardHeader class="px-6 py-5">
        <CardTitle class="text-base">Watchlist features</CardTitle>
        <CardDescription>
          Sorted and filtered without changing the underlying measurements.
        </CardDescription>
      </CardHeader>

      <div class="flex flex-wrap items-center gap-2 border-y px-6 py-3">
        <FeatureFilterBar
          v-model:search="search"
          v-model:timeframe="timeframeFilter"
          v-model:rs="rsFilter"
          v-model:rve="rveFilter"
          v-model:alignment="alignmentFilter"
          v-model:quality="qualityFilter"
          v-model:freshness="freshnessFilter"
          v-model:rvol="minRvol"
          :active-filters="activeFilters"
          :visible="displayRows.length"
          :total="store.rowList.length"
          @clear-filter="clearFilter"
          @clear-all="clearFilters"
        />
        <FeatureColumnSelector v-model="visible" :columns="selectableColumns" />
      </div>

      <div
        v-if="dashboardQuery.isPending.value && !hasRows"
        class="space-y-2 p-4"
        role="status"
      >
        <Skeleton class="h-9 w-full" />
        <Skeleton class="h-9 w-full" />
        <Skeleton class="h-9 w-full" />
        <span class="sr-only">Loading feature dashboard</span>
      </div>

      <div
        v-else-if="dashboardQuery.isError.value && !hasRows"
        class="p-6 text-sm text-muted-foreground"
        role="alert"
      >
        Feature dashboard is unavailable.
        {{ dashboardQuery.error.value?.message }}
      </div>

      <div
        v-else-if="showingEmptyWatchlist"
        class="p-6 text-sm text-muted-foreground"
      >
        No active watchlist instruments. Add instruments on the Watchlist page
        to see feature state.
      </div>

      <div v-else data-testid="feature-table" class="overflow-x-auto">
        <Table class="min-w-[900px]">
          <TableHeader class="sticky top-0 z-30 bg-muted">
            <TableRow class="hover:bg-transparent">
              <TableHead
                v-for="(column, index) in visibleColumns"
                :key="column.id"
                :aria-sort="ariaSort(column)"
                :title="column.hint"
                :class="[
                  column.numeric ? 'text-right' : '',
                  isGroupStart(index) ? 'border-l' : '',
                  column.primary ? '' : 'hidden lg:table-cell',
                  index === 0 ? 'sticky left-0 z-40 border-r bg-muted' : '',
                ]"
              >
                <button
                  v-if="column.sortable"
                  type="button"
                  class="inline-flex items-center gap-1 rounded-sm font-medium hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  :class="column.numeric ? 'flex-row-reverse' : ''"
                  :aria-label="`Sort by ${column.label}`"
                  @click="onSort(column)"
                >
                  {{ column.label }}
                  <span aria-hidden="true" class="text-[9px]">
                    {{
                      sortKey === column.sortable
                        ? sortDir === 'asc'
                          ? '▲'
                          : '▼'
                        : '⇅'
                    }}
                  </span>
                </button>
                <span v-else>{{ column.label }}</span>
                <span v-if="column.accessibleLabel" class="sr-only">
                  {{ column.accessibleLabel }}
                </span>
              </TableHead>
              <TableHead class="w-[48px] text-right">
                <span class="sr-only">Row actions</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="row in displayRows"
              :key="rowKey(row)"
              :data-state="isSelected(row) ? 'selected' : undefined"
              tabindex="0"
              class="group cursor-pointer focus-visible:bg-muted/50 focus-visible:outline-none"
              :aria-label="`Open ${row.symbol} in a new tab`"
              @click="openInNewTab(row)"
              @keydown.enter.prevent="openInNewTab(row)"
              @keydown.space.prevent="openInNewTab(row)"
            >
              <TableCell
                v-for="(column, index) in visibleColumns"
                :key="column.id"
                :class="[
                  column.numeric ? 'text-right tabular-nums' : '',
                  isGroupStart(index) ? 'border-l' : '',
                  column.primary ? '' : 'hidden lg:table-cell',
                  index === 0
                    ? 'sticky left-0 z-10 border-r bg-card transition-colors group-hover:bg-muted/50 group-data-[state=selected]:bg-muted'
                    : '',
                ]"
              >
                <a
                  v-if="column.id === 'symbol'"
                  :href="featureHref(row)"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="flex items-center gap-2 text-left focus-visible:outline-none"
                  @click.stop
                >
                  <span
                    class="grid size-7 shrink-0 place-items-center rounded-md bg-secondary text-[10px] font-semibold uppercase text-muted-foreground"
                    aria-hidden="true"
                  >
                    {{ row.symbol.slice(0, 2) }}
                  </span>
                  <span>
                    <span class="block font-medium leading-tight">{{
                      row.symbol
                    }}</span>
                    <span
                      class="block text-[10px] leading-tight text-muted-foreground"
                    >
                      {{ row.exchange }} · {{ row.timeframe }}
                    </span>
                  </span>
                </a>
                <FeatureValueCell
                  v-else-if="column.id === 'price'"
                  :value="row.lastPrice"
                  :decimals="metricDecimals('LAST_PRICE')"
                />
                <span
                  v-else-if="column.id === 'changePct'"
                  class="tabular-nums"
                  :class="movementClass(row.priceChangePercent)"
                >
                  {{ formatPercent(row.priceChangePercent) }}
                </span>
                <FeatureValueCell
                  v-else-if="column.id === 'rrsRaw'"
                  :value="row.rrsRaw"
                  :decimals="metricDecimals('RRS_RAW')"
                  signed
                  :reason="row.unavailableReasons['RRS_RAW']"
                />
                <FeatureValueCell
                  v-else-if="column.id === 'rrsFast'"
                  :value="row.rrsFast"
                  :decimals="metricDecimals('RRS_FAST')"
                  signed
                />
                <FeatureValueCell
                  v-else-if="column.id === 'rrsSlow'"
                  :value="row.rrsSlow"
                  :decimals="metricDecimals('RRS_SLOW')"
                  signed
                />
                <FeatureValueCell
                  v-else-if="column.id === 'rrsPersistence'"
                  :value="row.rrsPersistence"
                  :decimals="metricDecimals('RRS_PERSISTENCE')"
                />
                <span
                  v-else-if="column.id === 'dailyRrs'"
                  class="text-xs"
                  :class="
                    directionMetaFromState(row.dailyRrsState)?.tone ??
                    'text-muted-foreground'
                  "
                >
                  <template v-if="directionMetaFromState(row.dailyRrsState)">
                    {{ directionMetaFromState(row.dailyRrsState)?.glyph }}
                    {{ directionMetaFromState(row.dailyRrsState)?.label }}
                  </template>
                  <template v-else>—</template>
                </span>
                <FeatureValueCell
                  v-else-if="column.id === 'rvolInterval'"
                  :value="row.rvolInterval"
                  :decimals="metricDecimals('RVOL_INTERVAL')"
                  :reason="row.unavailableReasons['RVOL_INTERVAL']"
                />
                <FeatureValueCell
                  v-else-if="column.id === 'rvolCumulative'"
                  :value="row.rvolCumulative"
                  :decimals="metricDecimals('RVOL_CUMULATIVE')"
                />
                <FeatureValueCell
                  v-else-if="column.id === 'rve'"
                  :value="row.rve"
                  :decimals="metricDecimals('RVE')"
                  signed
                  :reason="row.unavailableReasons['RVE']"
                />
                <FeatureValueCell
                  v-else-if="column.id === 'atrPct'"
                  :value="row.atrPercent"
                  :decimals="metricDecimals('ATR_PERCENT')"
                  suffix="%"
                />
                <FeatureValueCell
                  v-else-if="column.id === 'vwapDist'"
                  :value="row.vwapDistanceAtr"
                  :decimals="metricDecimals('VWAP_DISTANCE_ATR')"
                  :reason="row.unavailableReasons['VWAP_DISTANCE_ATR']"
                />
                <span
                  v-else-if="column.id === 'market'"
                  class="text-xs"
                  :class="structureMeta(row.marketState).tone"
                >
                  {{ structureMeta(row.marketState).glyph }}
                  {{ structureMeta(row.marketState).label }}
                </span>
                <span
                  v-else-if="column.id === 'sector'"
                  class="text-xs"
                  :class="structureMeta(row.sectorState).tone"
                >
                  {{ structureMeta(row.sectorState).glyph }}
                  {{ structureMeta(row.sectorState).label }}
                </span>
                <FeatureTrustCell
                  v-else-if="column.id === 'quality'"
                  :state="stateFor(row)"
                  :reason="row.qualityReason"
                  :metrics="metricLabels(row)"
                />
                <span
                  v-else-if="column.id === 'age'"
                  class="text-xs"
                  :title="`${formatIstDateTime(row.observationTime)} IST`"
                >
                  {{ formatAge(row.observationTime) }}
                </span>
                <span
                  v-else-if="column.id === 'version'"
                  class="text-[10px] text-muted-foreground"
                  :title="featureVersionTitle(row.featureVersions['RRS_RAW'])"
                >
                  {{ formatFeatureVersion(row.featureVersions['RRS_RAW']) }}
                </span>
              </TableCell>
              <TableCell class="text-right">
                <DropdownMenu>
                  <DropdownMenuTrigger as-child>
                    <Button
                      variant="ghost"
                      size="icon"
                      class="size-7"
                      :aria-label="`Actions for ${row.symbol}`"
                      @click.stop
                    >
                      <MoreHorizontal class="size-4" aria-hidden="true" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end" @click.stop>
                    <DropdownMenuLabel>{{ row.symbol }}</DropdownMenuLabel>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem @select="selected = row">
                      Open feature history
                    </DropdownMenuItem>
                    <DropdownMenuItem @select="openInNewTab(row)">
                      Open feature view
                    </DropdownMenuItem>
                    <DropdownMenuItem @select="openMarketDataInNewTab(row)">
                      Open market data
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
            <TableRow v-if="displayRows.length === 0">
              <TableCell
                :colspan="visibleColumns.length + 1"
                class="py-10 text-center text-sm text-muted-foreground"
              >
                <p>No rows match the current filters.</p>
                <p class="mt-1 text-xs">
                  Rows with missing features are never hidden silently.
                </p>
                <Button
                  variant="outline"
                  size="sm"
                  class="mt-3"
                  @click="clearFilters"
                >
                  Clear filters
                </Button>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </Card>

    <FeatureDiagnosticsPanel
      :diagnostics="diagnosticsQuery.data.value ?? null"
      :connection="store.connection"
      :last-updated-at="store.lastUpdatedAt"
      :last-sequence="store.lastSequence"
      :gap-detected="store.gapDetected"
    />

    <Sheet
      :open="selected !== null"
      @update:open="(open: boolean) => !open && (selected = null)"
    >
      <SheetContent class="w-full overflow-y-auto sm:max-w-3xl">
        <SheetHeader>
          <div class="flex items-center gap-3">
            <span
              class="grid size-9 place-items-center rounded-md bg-secondary text-xs font-semibold uppercase text-muted-foreground"
              aria-hidden="true"
            >
              {{ selected?.symbol.slice(0, 2) }}
            </span>
            <div>
              <SheetTitle>{{ selected?.symbol }} feature history</SheetTitle>
              <SheetDescription>
                {{ selected?.displayName ?? selected?.symbol }} ·
                {{ selected?.timeframe }} · observation
                {{ formatIstDateTime(selected?.observationTime) }} IST
              </SheetDescription>
            </div>
          </div>
        </SheetHeader>
        <div v-if="selected" class="mt-4">
          <FeatureHistoryPanel
            :instrument-id="selected.instrumentId"
            :symbol="selected.symbol"
          />
        </div>
      </SheetContent>
    </Sheet>
  </div>
</template>
