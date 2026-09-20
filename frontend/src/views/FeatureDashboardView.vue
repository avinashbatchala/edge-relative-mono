<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { RefreshCw, RotateCcw, SlidersHorizontal } from '@lucide/vue'
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
import { Card, CardContent } from '@/components/ui/card'
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
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { formatAge, formatIstDateTime, formatPercent } from '@/lib/format'
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
  primary: boolean
  sortable?: SortKey
}

const COLUMNS: Column[] = [
  { id: 'symbol', label: 'Symbol', primary: true, sortable: 'symbol' },
  { id: 'price', label: 'Price', primary: true, sortable: 'price' },
  { id: 'changePct', label: 'Chg %', primary: true, sortable: 'changePct' },
  { id: 'rrsRaw', label: 'RRS', primary: true, sortable: 'rrsRaw' },
  { id: 'rrsFast', label: 'RRS fast', primary: false },
  { id: 'rrsSlow', label: 'RRS slow', primary: false },
  {
    id: 'rrsPersistence',
    label: 'RRS pers',
    primary: false,
    sortable: 'rrsPersistence',
  },
  { id: 'dailyRrs', label: 'D-RRS', primary: false },
  {
    id: 'rvolInterval',
    label: 'RVOL',
    primary: true,
    sortable: 'rvolInterval',
  },
  { id: 'rvolCumulative', label: 'RVOL cum', primary: false },
  { id: 'rve', label: 'RVE', primary: true, sortable: 'rve' },
  { id: 'atrPct', label: 'ATR %', primary: false },
  { id: 'vwapDist', label: 'VWAP/ATR', primary: false },
  { id: 'market', label: 'Market', primary: false },
  { id: 'sector', label: 'Sector', primary: false },
  { id: 'quality', label: 'Quality', primary: true, sortable: 'quality' },
  { id: 'age', label: 'Age', primary: true, sortable: 'age' },
  { id: 'version', label: 'Version', primary: false },
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

// Seed the store from the authoritative REST snapshot only until the stream has produced state.
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
    const expanding = row.rve > 0
    const contracting = row.rve < 0
    if (rveFilter.value === 'expanding' && !expanding) {
      return false
    }
    if (rveFilter.value === 'contracting' && !contracting) {
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

// Freeze the order between explicit user actions: a rapidly changing feature must not make rows
// jump under the operator's cursor. Values still update live; only the order is held until the
// operator changes a filter/sort or presses Re-sort.
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

function rowKey(row: FeatureDashboardRow): string {
  return `${row.instrumentId}:${row.timeframe}`
}

function stateFor(row: FeatureDashboardRow): string {
  const instrument = diagnosticsQuery.data.value?.instruments.find(
    (candidate) => candidate.instrumentId === row.instrumentId,
  )
  if (instrument) {
    return instrument.state
  }
  return isTrustworthy(row) ? 'HEALTHY' : 'DEGRADED'
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
  <div class="space-y-4 p-4">
    <header class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h1 class="text-lg font-semibold">Feature Dashboard</h1>
        <p class="text-xs text-muted-foreground">
          Observational measurements. Strategy, risk and execution decisions are
          not made here.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-xs text-muted-foreground">
          Last update {{ formatAge(store.lastUpdatedAt) }}
        </span>
        <Button variant="outline" size="sm" @click="resync">
          <RefreshCw class="mr-1 size-3.5" aria-hidden="true" /> Resync
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

    <div class="flex flex-wrap items-end gap-2">
      <Input
        v-model="search"
        placeholder="Search symbol"
        aria-label="Search watchlist symbols"
        class="w-48"
      />
      <NativeSelect
        v-model="timeframeFilter"
        aria-label="Timeframe filter"
        class="w-24"
      >
        <NativeSelectOption value="all">All TF</NativeSelectOption>
        <NativeSelectOption value="M5">5m</NativeSelectOption>
        <NativeSelectOption value="D1">1D</NativeSelectOption>
      </NativeSelect>
      <NativeSelect
        v-model="rsFilter"
        aria-label="Relative strength filter"
        class="w-32"
      >
        <NativeSelectOption value="all">All RS</NativeSelectOption>
        <NativeSelectOption value="positive">Positive RS</NativeSelectOption>
        <NativeSelectOption value="negative">Negative RS</NativeSelectOption>
        <NativeSelectOption value="neutral">Neutral RS</NativeSelectOption>
      </NativeSelect>
      <NativeSelect v-model="rveFilter" aria-label="RVE filter" class="w-32">
        <NativeSelectOption value="all">All RVE</NativeSelectOption>
        <NativeSelectOption value="expanding">Expanding</NativeSelectOption>
        <NativeSelectOption value="stable">Stable</NativeSelectOption>
        <NativeSelectOption value="contracting">Contracting</NativeSelectOption>
      </NativeSelect>
      <NativeSelect
        v-model="alignmentFilter"
        aria-label="Alignment filter"
        class="w-32"
      >
        <NativeSelectOption value="all">All alignment</NativeSelectOption>
        <NativeSelectOption value="market">Market aligned</NativeSelectOption>
        <NativeSelectOption value="sector">Sector aligned</NativeSelectOption>
        <NativeSelectOption value="both">Both aligned</NativeSelectOption>
      </NativeSelect>
      <NativeSelect
        v-model="qualityFilter"
        aria-label="Quality filter"
        class="w-32"
      >
        <NativeSelectOption value="all">All quality</NativeSelectOption>
        <NativeSelectOption value="trustworthy">Trustworthy</NativeSelectOption>
        <NativeSelectOption value="degraded">Degraded</NativeSelectOption>
        <NativeSelectOption value="unavailable">Unavailable</NativeSelectOption>
      </NativeSelect>
      <NativeSelect
        v-model="freshnessFilter"
        aria-label="Freshness filter"
        class="w-28"
      >
        <NativeSelectOption value="all">All freshness</NativeSelectOption>
        <NativeSelectOption value="fresh">Fresh</NativeSelectOption>
        <NativeSelectOption value="stale">Stale</NativeSelectOption>
      </NativeSelect>
      <Input
        v-model.number="minRvol"
        type="number"
        min="0"
        step="0.1"
        placeholder="Min RVOL"
        aria-label="Minimum interval RVOL"
        class="w-28"
      />
      <Button variant="ghost" size="sm" @click="clearFilters">
        <RotateCcw class="mr-1 size-3.5" aria-hidden="true" /> Clear
      </Button>

      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <Button variant="outline" size="sm">
            <SlidersHorizontal class="mr-1 size-3.5" aria-hidden="true" />
            Columns
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" class="max-h-80 overflow-auto">
          <DropdownMenuLabel>Visible columns</DropdownMenuLabel>
          <DropdownMenuCheckboxItem
            v-for="column in COLUMNS.filter((c) => !c.primary)"
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
    </div>

    <p class="text-xs text-muted-foreground" aria-live="polite">
      Showing {{ displayRows.length }} of {{ store.rowList.length }} watchlist
      rows
    </p>

    <Card v-if="dashboardQuery.isPending.value && !hasRows">
      <CardContent class="space-y-2 pt-6">
        <Skeleton class="h-8 w-full" />
        <Skeleton class="h-8 w-full" />
        <Skeleton class="h-8 w-full" />
      </CardContent>
    </Card>

    <Card v-else-if="dashboardQuery.isError.value && !hasRows" role="alert">
      <CardContent class="pt-6 text-sm text-muted-foreground">
        Feature dashboard is unavailable.
        {{ dashboardQuery.error.value?.message }}
      </CardContent>
    </Card>

    <Card v-else-if="showingEmptyWatchlist">
      <CardContent class="pt-6 text-sm text-muted-foreground">
        No active watchlist instruments. Add instruments on the Watchlist page
        to see feature state.
      </CardContent>
    </Card>

    <div v-else class="overflow-x-auto rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead
              v-for="column in COLUMNS.filter((c) => visible[c.id])"
              :key="column.id"
            >
              <button
                v-if="column.sortable"
                type="button"
                class="inline-flex items-center gap-1 font-medium hover:underline"
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
                      : ''
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
            class="cursor-pointer"
            @click="selected = row"
          >
            <TableCell
              v-for="column in COLUMNS.filter((c) => visible[c.id])"
              :key="column.id"
            >
              <button
                v-if="column.id === 'symbol'"
                type="button"
                class="text-left font-medium hover:underline"
                @click.stop="selected = row"
              >
                <span class="block">{{ row.symbol }}</span>
                <span class="block text-[10px] text-muted-foreground">{{
                  row.exchange
                }}</span>
              </button>
              <FeatureValueCell
                v-else-if="column.id === 'price'"
                :value="row.lastPrice"
              />
              <span v-else-if="column.id === 'changePct'" class="tabular-nums">
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
              <span v-else-if="column.id === 'dailyRrs'" class="text-xs">
                {{ row.dailyRrsState ?? '—' }}
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
                {{ row.marketState ?? '—' }}
              </span>
              <span v-else-if="column.id === 'sector'" class="text-xs">
                {{ row.sectorState ?? '—' }}
              </span>
              <FeatureStateBadge
                v-else-if="column.id === 'quality'"
                :state="stateFor(row)"
                :reason="row.qualityReason"
              />
              <span
                v-else-if="column.id === 'age'"
                class="text-xs tabular-nums"
              >
                {{ formatAge(row.observationTime) }}
              </span>
              <span
                v-else-if="column.id === 'version'"
                class="text-[10px] text-muted-foreground"
              >
                {{ row.featureVersions['RRS_RAW'] ?? row.featureSchemaVersion }}
              </span>
            </TableCell>
          </TableRow>
          <TableRow v-if="displayRows.length === 0">
            <TableCell
              :colspan="COLUMNS.filter((c) => visible[c.id]).length"
              class="text-center text-sm text-muted-foreground"
            >
              No rows match the current filters. Rows with missing features are
              never hidden silently — clear filters to see them.
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>

    <Sheet
      :open="selected !== null"
      @update:open="(open: boolean) => !open && (selected = null)"
    >
      <SheetContent class="w-full overflow-y-auto sm:max-w-3xl">
        <SheetHeader>
          <SheetTitle>{{ selected?.symbol }} feature history</SheetTitle>
          <SheetDescription>
            {{ selected?.displayName ?? selected?.symbol }} ·
            {{ selected?.timeframe }} · observation
            {{ formatIstDateTime(selected?.observationTime) }}
          </SheetDescription>
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
