<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { RefreshCw, RotateCcw, Search, SlidersHorizontal } from '@lucide/vue'
import {
  featureKeys,
  getFeatureDashboard,
  getFeatureDiagnostics,
  type FeatureDashboardRow,
} from '@/api/features'
import { useFeatureStreamStore } from '@/stores/feature-stream'
import { useFeatureStream } from '@/composables/useFeatureStream'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
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
import FeatureValueCell from '@/components/feature/FeatureValueCell.vue'
import FeatureStateBadge from '@/components/feature/FeatureStateBadge.vue'
import FeatureDiagnosticsPanel from '@/components/feature/FeatureDiagnosticsPanel.vue'
import FeatureHistoryPanel from '@/components/feature/FeatureHistoryPanel.vue'

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
  group: string
  primary: boolean
  numeric?: boolean
  sortable?: SortKey
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
    group: 'Price',
    primary: true,
    numeric: true,
    sortable: 'price',
    hint: 'Latest canonical close for the timeframe',
  },
  {
    id: 'changePct',
    label: 'Chg %',
    group: 'Price',
    primary: true,
    numeric: true,
    sortable: 'changePct',
    hint: 'Change versus the previous session close',
  },
  {
    id: 'rrsRaw',
    label: 'RRS',
    group: 'Relative strength',
    primary: true,
    numeric: true,
    sortable: 'rrsRaw',
    hint: 'Real Relative Strength: volatility-normalised excess movement versus the broad benchmark',
  },
  {
    id: 'rrsFast',
    label: 'Fast',
    group: 'Relative strength',
    primary: false,
    numeric: true,
    hint: 'Fast EMA of RRS raw',
  },
  {
    id: 'rrsSlow',
    label: 'Slow',
    group: 'Relative strength',
    primary: false,
    numeric: true,
    hint: 'Slow EMA of RRS raw',
  },
  {
    id: 'rrsPersistence',
    label: 'Persist',
    group: 'Relative strength',
    primary: false,
    numeric: true,
    sortable: 'rrsPersistence',
    hint: 'Share of recent bars agreeing with the RRS sign',
  },
  {
    id: 'dailyRrs',
    label: 'Daily',
    group: 'Relative strength',
    primary: false,
    hint: 'Daily RRS direction (higher-timeframe context)',
  },
  {
    id: 'rvolInterval',
    label: 'RVOL',
    group: 'Volume',
    primary: true,
    numeric: true,
    sortable: 'rvolInterval',
    hint: 'Interval RVOL: current slot versus the same slot in prior sessions',
  },
  {
    id: 'rvolCumulative',
    label: 'Cum RVOL',
    group: 'Volume',
    primary: false,
    numeric: true,
    hint: 'Cumulative RVOL to the same session-relative time',
  },
  {
    id: 'rve',
    label: 'RVE',
    group: 'Volume',
    primary: true,
    numeric: true,
    sortable: 'rve',
    hint: 'Relative Volume Expansion: fast minus slow log RVOL',
  },
  {
    id: 'atrPct',
    label: 'ATR %',
    group: 'Volatility',
    primary: false,
    numeric: true,
    hint: 'ATR as a percentage of price',
  },
  {
    id: 'vwapDist',
    label: 'VWAP/ATR',
    group: 'Volatility',
    primary: false,
    numeric: true,
    hint: 'Distance from VWAP in ATR units',
  },
  {
    id: 'market',
    label: 'Market',
    group: 'Context',
    primary: false,
    hint: 'Broad-market price structure',
  },
  {
    id: 'sector',
    label: 'Sector',
    group: 'Context',
    primary: false,
    hint: 'Sector price structure',
  },
  {
    id: 'quality',
    label: 'Trust',
    group: 'Quality',
    primary: true,
    sortable: 'quality',
    hint: 'Aggregate feature quality and availability',
  },
  {
    id: 'age',
    label: 'Fresh',
    group: 'Quality',
    primary: true,
    numeric: true,
    sortable: 'age',
    hint: 'Age since the observation timestamp',
  },
  {
    id: 'version',
    label: 'Version',
    group: 'Quality',
    primary: false,
    hint: 'Active RRS feature version',
  },
]

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

const QUALITY_RANK: Record<string, number> = {
  GOOD: 0,
  CORRECTED: 1,
  DEGRADED: 2,
  SUSPECT: 3,
  STALE: 4,
  INCOMPLETE: 5,
  UNAVAILABLE: 6,
}

function qualityRank(row: FeatureDashboardRow): number {
  return QUALITY_RANK[row.quality] ?? 7
}

function isTrustworthy(row: FeatureDashboardRow): boolean {
  return (
    (row.quality === 'GOOD' || row.quality === 'CORRECTED') &&
    row.availability === 'VALID'
  )
}

function isStale(row: FeatureDashboardRow): boolean {
  return row.availability === 'STALE' || row.quality === 'STALE'
}

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
  if (qualityFilter.value === 'trustworthy' && !isTrustworthy(row)) {
    return false
  }
  if (qualityFilter.value === 'degraded' && isTrustworthy(row)) {
    return false
  }
  if (qualityFilter.value === 'unavailable' && row.availability === 'VALID') {
    return false
  }
  if (freshnessFilter.value === 'fresh' && isStale(row)) {
    return false
  }
  if (freshnessFilter.value === 'stale' && !isStale(row)) {
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
      return qualityRank(row)
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

// Keep the order stable between explicit actions so rows do not jump while values update live.
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

function isGroupStart(index: number): boolean {
  if (index === 0) {
    return false
  }
  return (
    visibleColumns.value[index]?.group !==
    visibleColumns.value[index - 1]?.group
  )
}

function rowKey(row: FeatureDashboardRow): string {
  return `${row.instrumentId}:${row.timeframe}`
}

function stateFor(row: FeatureDashboardRow): string {
  const instrument = diagnosticsQuery.data.value?.instruments.find(
    (candidate) => candidate.instrumentId === row.instrumentId,
  )
  return instrument
    ? instrument.state
    : isTrustworthy(row)
      ? 'HEALTHY'
      : 'DEGRADED'
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

interface ActiveFilter {
  key: string
  label: string
  clear: () => void
}

const activeFilters = computed<ActiveFilter[]>(() => {
  const filters: ActiveFilter[] = []
  if (search.value) {
    filters.push({
      key: 'search',
      label: `Search: ${search.value}`,
      clear: () => (search.value = ''),
    })
  }
  if (timeframeFilter.value !== 'all') {
    filters.push({
      key: 'timeframe',
      label: `Timeframe: ${timeframeFilter.value}`,
      clear: () => (timeframeFilter.value = 'all'),
    })
  }
  if (rsFilter.value !== 'all') {
    filters.push({
      key: 'rs',
      label: `RS: ${rsFilter.value}`,
      clear: () => (rsFilter.value = 'all'),
    })
  }
  if (rveFilter.value !== 'all') {
    filters.push({
      key: 'rve',
      label: `RVE: ${rveFilter.value}`,
      clear: () => (rveFilter.value = 'all'),
    })
  }
  if (alignmentFilter.value !== 'all') {
    filters.push({
      key: 'alignment',
      label: `Alignment: ${alignmentFilter.value}`,
      clear: () => (alignmentFilter.value = 'all'),
    })
  }
  if (qualityFilter.value !== 'all') {
    filters.push({
      key: 'quality',
      label: `Quality: ${qualityFilter.value}`,
      clear: () => (qualityFilter.value = 'all'),
    })
  }
  if (freshnessFilter.value !== 'all') {
    filters.push({
      key: 'freshness',
      label: `Freshness: ${freshnessFilter.value}`,
      clear: () => (freshnessFilter.value = 'all'),
    })
  }
  if (minRvol.value !== '') {
    filters.push({
      key: 'minRvol',
      label: `RVOL ≥ ${minRvol.value}`,
      clear: () => (minRvol.value = ''),
    })
  }
  return filters
})

function resync() {
  store.setAuthoritative(store.rowList)
  diagnosticsQuery.refetch()
  dashboardQuery.refetch()
}

function structureLabel(state: string | null): string {
  switch (state) {
    case 'BULL_STRUCTURE':
      return 'Bull'
    case 'BEAR_STRUCTURE':
      return 'Bear'
    case 'MIXED':
      return 'Mixed'
    default:
      return '—'
  }
}

function directionLabel(state: string | null): string {
  switch (state) {
    case 'POSITIVE':
      return '▲ Positive'
    case 'NEGATIVE':
      return '▼ Negative'
    case 'NEUTRAL':
      return '■ Neutral'
    default:
      return '—'
  }
}

function dailyTone(state: string | null): string {
  if (state === 'POSITIVE') {
    return 'text-positive'
  }
  if (state === 'NEGATIVE') {
    return 'text-negative'
  }
  return 'text-muted-foreground'
}

const hasRows = computed(() => store.rowList.length > 0)
const showingEmptyWatchlist = computed(
  () => !dashboardQuery.isPending.value && !hasRows.value,
)
</script>

<template>
  <div class="flex h-full flex-col gap-4 p-4 lg:p-6">
    <header class="flex flex-wrap items-start justify-between gap-3">
      <div class="space-y-1">
        <h1 class="text-xl font-semibold tracking-tight">Feature Dashboard</h1>
        <p class="max-w-2xl text-sm text-muted-foreground">
          Point-in-time relative strength, volume and volatility for the active
          watchlist. Observational only — no strategy, risk or execution
          decisions.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <span class="hidden text-xs text-muted-foreground sm:inline">
          Synced {{ formatAge(store.lastUpdatedAt) }}
        </span>
        <Button variant="outline" size="sm" @click="resync">
          <RefreshCw class="mr-1 size-3.5" aria-hidden="true" /> Refresh
        </Button>
      </div>
    </header>

    <FeatureDiagnosticsPanel
      :diagnostics="diagnosticsQuery.data.value ?? null"
      :connection="store.connection"
      :last-updated-at="store.lastUpdatedAt"
      :last-sequence="store.lastSequence"
      :gap-detected="store.gapDetected"
    />

    <Card class="overflow-hidden">
      <div class="flex flex-wrap items-center gap-2 border-b p-3">
        <div class="relative">
          <Search
            class="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <Input
            v-model="search"
            placeholder="Search symbol"
            aria-label="Search watchlist symbols"
            class="h-9 w-56 pl-8"
          />
        </div>

        <Popover>
          <PopoverTrigger as-child>
            <Button variant="outline" size="sm" class="h-9">
              <SlidersHorizontal class="mr-1 size-3.5" aria-hidden="true" />
              Filters
              <span
                v-if="activeFilters.length"
                class="ml-1 grid size-4 place-items-center rounded-full bg-primary text-[10px] font-semibold text-primary-foreground"
              >
                {{ activeFilters.length }}
              </span>
            </Button>
          </PopoverTrigger>
          <PopoverContent align="start" class="w-80 space-y-3 p-3">
            <div class="grid grid-cols-2 gap-3">
              <label class="space-y-1 text-xs">
                <span class="text-muted-foreground">Timeframe</span>
                <NativeSelect
                  v-model="timeframeFilter"
                  aria-label="Timeframe filter"
                >
                  <NativeSelectOption value="all">All</NativeSelectOption>
                  <NativeSelectOption value="M5">5m</NativeSelectOption>
                  <NativeSelectOption value="D1">1D</NativeSelectOption>
                </NativeSelect>
              </label>
              <label class="space-y-1 text-xs">
                <span class="text-muted-foreground">Relative strength</span>
                <NativeSelect
                  v-model="rsFilter"
                  aria-label="Relative strength filter"
                >
                  <NativeSelectOption value="all">All</NativeSelectOption>
                  <NativeSelectOption value="positive"
                    >Positive</NativeSelectOption
                  >
                  <NativeSelectOption value="negative"
                    >Negative</NativeSelectOption
                  >
                  <NativeSelectOption value="neutral"
                    >Neutral</NativeSelectOption
                  >
                </NativeSelect>
              </label>
              <label class="space-y-1 text-xs">
                <span class="text-muted-foreground">RVE</span>
                <NativeSelect v-model="rveFilter" aria-label="RVE filter">
                  <NativeSelectOption value="all">All</NativeSelectOption>
                  <NativeSelectOption value="expanding"
                    >Expanding</NativeSelectOption
                  >
                  <NativeSelectOption value="stable">Stable</NativeSelectOption>
                  <NativeSelectOption value="contracting"
                    >Contracting</NativeSelectOption
                  >
                </NativeSelect>
              </label>
              <label class="space-y-1 text-xs">
                <span class="text-muted-foreground">Alignment</span>
                <NativeSelect
                  v-model="alignmentFilter"
                  aria-label="Alignment filter"
                >
                  <NativeSelectOption value="all">All</NativeSelectOption>
                  <NativeSelectOption value="market">Market</NativeSelectOption>
                  <NativeSelectOption value="sector">Sector</NativeSelectOption>
                  <NativeSelectOption value="both">Both</NativeSelectOption>
                </NativeSelect>
              </label>
              <label class="space-y-1 text-xs">
                <span class="text-muted-foreground">Quality</span>
                <NativeSelect
                  v-model="qualityFilter"
                  aria-label="Quality filter"
                >
                  <NativeSelectOption value="all">All</NativeSelectOption>
                  <NativeSelectOption value="trustworthy"
                    >Trustworthy</NativeSelectOption
                  >
                  <NativeSelectOption value="degraded"
                    >Degraded</NativeSelectOption
                  >
                  <NativeSelectOption value="unavailable"
                    >Unavailable</NativeSelectOption
                  >
                </NativeSelect>
              </label>
              <label class="space-y-1 text-xs">
                <span class="text-muted-foreground">Freshness</span>
                <NativeSelect
                  v-model="freshnessFilter"
                  aria-label="Freshness filter"
                >
                  <NativeSelectOption value="all">All</NativeSelectOption>
                  <NativeSelectOption value="fresh">Fresh</NativeSelectOption>
                  <NativeSelectOption value="stale">Stale</NativeSelectOption>
                </NativeSelect>
              </label>
              <label class="col-span-2 space-y-1 text-xs">
                <span class="text-muted-foreground">Minimum interval RVOL</span>
                <Input
                  v-model="minRvol"
                  type="number"
                  min="0"
                  step="0.1"
                  placeholder="e.g. 1.5"
                  aria-label="Minimum interval RVOL"
                  class="h-9"
                />
              </label>
            </div>
          </PopoverContent>
        </Popover>

        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <Button variant="outline" size="sm" class="h-9">Columns</Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            align="start"
            class="max-h-80 w-52 overflow-auto"
          >
            <DropdownMenuLabel>Visible columns</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuCheckboxItem
              v-for="column in COLUMNS.filter((c) => c.id !== 'symbol')"
              :key="column.id"
              :model-value="visible[column.id]"
              @update:model-value="
                (value: boolean) => (visible[column.id] = value)
              "
            >
              {{ column.label }}
            </DropdownMenuCheckboxItem>
          </DropdownMenuContent>
        </DropdownMenu>

        <span class="ml-auto text-xs text-muted-foreground">
          Showing {{ displayRows.length }} of
          {{ store.rowList.length }} watchlist rows
        </span>
      </div>

      <div
        v-if="activeFilters.length"
        class="flex flex-wrap items-center gap-1.5 border-b bg-muted/20 px-3 py-2"
      >
        <span
          v-for="filter in activeFilters"
          :key="filter.key"
          class="inline-flex items-center gap-1 rounded-full border bg-background px-2 py-0.5 text-xs"
        >
          {{ filter.label }}
          <button
            type="button"
            class="text-muted-foreground hover:text-foreground"
            :aria-label="`Remove filter ${filter.label}`"
            @click="filter.clear()"
          >
            ×
          </button>
        </span>
        <Button
          variant="ghost"
          size="sm"
          class="h-6 px-2 text-xs"
          @click="clearFilters"
        >
          <RotateCcw class="mr-1 size-3" aria-hidden="true" /> Clear
        </Button>
      </div>

      <div
        v-if="dashboardQuery.isPending.value && !hasRows"
        class="space-y-2 p-4"
      >
        <Skeleton class="h-9 w-full" />
        <Skeleton class="h-9 w-full" />
        <Skeleton class="h-9 w-full" />
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

      <div v-else class="overflow-x-auto">
        <Table>
          <TableHeader class="sticky top-0 z-10 bg-card">
            <TableRow class="hover:bg-transparent">
              <TableHead
                v-for="(column, index) in visibleColumns"
                :key="column.id"
                :class="[
                  column.numeric ? 'text-right' : '',
                  isGroupStart(index) ? 'border-l' : '',
                  column.primary ? '' : 'hidden lg:table-cell',
                ]"
                :title="column.hint"
              >
                <button
                  v-if="column.sortable"
                  type="button"
                  class="inline-flex items-center gap-1 font-medium hover:text-foreground"
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
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="row in displayRows"
              :key="rowKey(row)"
              class="cursor-pointer transition-colors hover:bg-muted/50"
              :class="
                selected?.instrumentId === row.instrumentId ? 'bg-muted/50' : ''
              "
              @click="selected = row"
            >
              <TableCell
                v-for="(column, index) in visibleColumns"
                :key="column.id"
                :class="[
                  column.numeric ? 'text-right tabular-nums' : '',
                  isGroupStart(index) ? 'border-l' : '',
                  column.primary ? '' : 'hidden lg:table-cell',
                ]"
              >
                <button
                  v-if="column.id === 'symbol'"
                  type="button"
                  class="flex items-center gap-2 text-left"
                  @click.stop="selected = row"
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
                </button>
                <FeatureValueCell
                  v-else-if="column.id === 'price'"
                  :value="row.lastPrice"
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
                  signed
                  :reason="row.unavailableReasons['RRS_RAW']"
                />
                <FeatureValueCell
                  v-else-if="column.id === 'rrsFast'"
                  :value="row.rrsFast"
                  signed
                />
                <FeatureValueCell
                  v-else-if="column.id === 'rrsSlow'"
                  :value="row.rrsSlow"
                  signed
                />
                <FeatureValueCell
                  v-else-if="column.id === 'rrsPersistence'"
                  :value="row.rrsPersistence"
                />
                <span
                  v-else-if="column.id === 'dailyRrs'"
                  class="text-xs"
                  :class="dailyTone(row.dailyRrsState)"
                >
                  {{ directionLabel(row.dailyRrsState) }}
                </span>
                <FeatureValueCell
                  v-else-if="column.id === 'rvolInterval'"
                  :value="row.rvolInterval"
                  :reason="row.unavailableReasons['RVOL_INTERVAL']"
                />
                <FeatureValueCell
                  v-else-if="column.id === 'rvolCumulative'"
                  :value="row.rvolCumulative"
                />
                <FeatureValueCell
                  v-else-if="column.id === 'rve'"
                  :value="row.rve"
                  signed
                  :reason="row.unavailableReasons['RVE']"
                />
                <FeatureValueCell
                  v-else-if="column.id === 'atrPct'"
                  :value="row.atrPercent"
                  suffix="%"
                />
                <FeatureValueCell
                  v-else-if="column.id === 'vwapDist'"
                  :value="row.vwapDistanceAtr"
                  :reason="row.unavailableReasons['VWAP_DISTANCE_ATR']"
                />
                <span v-else-if="column.id === 'market'" class="text-xs">
                  {{ structureLabel(row.marketState) }}
                </span>
                <span v-else-if="column.id === 'sector'" class="text-xs">
                  {{ structureLabel(row.sectorState) }}
                </span>
                <FeatureStateBadge
                  v-else-if="column.id === 'quality'"
                  :state="stateFor(row)"
                  :reason="row.qualityReason"
                />
                <span
                  v-else-if="column.id === 'age'"
                  class="text-xs"
                  :title="formatIstDateTime(row.observationTime)"
                >
                  {{ formatAge(row.observationTime) }}
                </span>
                <span
                  v-else-if="column.id === 'version'"
                  class="text-[10px] text-muted-foreground"
                >
                  {{
                    row.featureVersions['RRS_RAW'] ?? row.featureSchemaVersion
                  }}
                </span>
              </TableCell>
            </TableRow>
            <TableRow v-if="displayRows.length === 0">
              <TableCell
                :colspan="visibleColumns.length"
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
                {{ formatIstDateTime(selected?.observationTime) }}
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
