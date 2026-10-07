<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { AlertCircle, ListChecks, Plus, RefreshCw, Search } from '@lucide/vue'
import { ApiError } from '@/api/http'
import type { BrokerInstrument } from '@/api/types'
import {
  addWatchlistItem,
  getWatchlist,
  removeWatchlistItem,
  reorderWatchlist,
  watchlistKeys,
  type AddWatchlistItemRequest,
} from '@/api/watchlist'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import InstrumentSelector from '@/components/market-data/InstrumentSelector.vue'
import SectionState from '@/components/market-data/SectionState.vue'
import WatchlistRow from '@/components/watchlist/WatchlistRow.vue'
import WatchlistDecisionRow from '@/components/watchlist/WatchlistDecisionRow.vue'
import SegmentedTabs from '@/components/common/SegmentedTabs.vue'
import {
  featureKeys,
  getFeatureDashboard,
  type FeatureDashboardRow,
} from '@/api/features'
import { useInstrumentSearch } from '@/composables/useInstrumentSearch'
import { isDerivative } from '@/lib/instrument'

const queryClient = useQueryClient()

const watchlistQuery = useQuery(() => ({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
  staleTime: 30_000,
  retry: 1,
}))

const response = computed(() => watchlistQuery.data.value ?? null)
const entries = computed(() => response.value?.entries ?? [])
const capacity = computed(() => response.value?.capacity ?? 20)
const count = computed(() => response.value?.count ?? entries.value.length)
const remaining = computed(() => Math.max(0, capacity.value - count.value))
const isFull = computed(() => remaining.value <= 0)

const filter = ref('')
const filteredEntries = computed(() => {
  const term = filter.value.trim().toLowerCase()
  if (!term) {
    return entries.value
  }
  return entries.value.filter((entry) =>
    `${entry.symbol} ${entry.name ?? ''}`.toLowerCase().includes(term),
  )
})

// --- view: decision-first columns (default) vs full market anatomy ------------
const view = ref<'decision' | 'market'>('decision')
const viewTabs = [
  { value: 'decision', label: 'Decision' },
  { value: 'market', label: 'Market' },
] as const
const tableColspan = computed(() => (view.value === 'market' ? 14 : 15))

const dashboardQuery = useQuery(() => ({
  queryKey: featureKeys.dashboard(),
  queryFn: ({ signal }) => getFeatureDashboard(signal),
  staleTime: 15_000,
  refetchInterval: 30_000,
  retry: 1,
}))
const featuresById = computed(() => {
  const map = new Map<number, FeatureDashboardRow>()
  for (const row of dashboardQuery.data.value ?? []) {
    map.set(row.instrumentId, row)
  }
  return map
})

// --- add ---------------------------------------------------------------------
const addOpen = ref(false)
const search = ref('')
const searchOpen = ref(false)
const addError = ref<ApiError | Error | null>(null)
const { query: searchQuery } = useInstrumentSearch(search, searchOpen)

function onSearch(value: string) {
  search.value = value
}

const addMutation = useMutation({
  mutationFn: (request: AddWatchlistItemRequest) => addWatchlistItem(request),
  onSuccess: () => {
    addError.value = null
    addOpen.value = false
    search.value = ''
    queryClient.invalidateQueries({ queryKey: watchlistKeys.all })
  },
  onError: (error: unknown) => {
    addError.value = error instanceof Error ? error : new Error('Add failed')
  },
})

function toAddRequest(instrument: BrokerInstrument): AddWatchlistItemRequest {
  return {
    exchange: instrument.exchange,
    segment: instrument.segment ?? 'CASH',
    instrumentType: instrument.instrumentType,
    symbol: instrument.tradingSymbol,
    name: instrument.name,
    brokerSymbol: instrument.brokerSymbol,
    tickSize: instrument.tickSize,
    lotSize: instrument.lotSize,
  }
}

function onPick(instrument: BrokerInstrument) {
  if (isDerivative(instrument)) {
    addError.value = new Error(
      'Select the underlying equity, not a derivative contract.',
    )
    return
  }
  if (isFull.value) {
    addError.value = new Error(
      'The watchlist is full; remove an instrument first.',
    )
    return
  }
  addError.value = null
  addMutation.mutate(toAddRequest(instrument))
}

// --- remove ------------------------------------------------------------------
const removeMutation = useMutation({
  mutationFn: (instrumentId: number) => removeWatchlistItem(instrumentId),
  onSuccess: () =>
    queryClient.invalidateQueries({ queryKey: watchlistKeys.all }),
})

function remove(instrumentId: number) {
  removeMutation.mutate(instrumentId)
}

// --- reorder -----------------------------------------------------------------
const reorderMutation = useMutation({
  mutationFn: (instrumentIds: number[]) => reorderWatchlist(instrumentIds),
  onSuccess: (data) => queryClient.setQueryData(watchlistKeys.all, data),
})

function move(instrumentId: number, direction: -1 | 1) {
  const ids = entries.value.map((entry) => entry.instrumentId)
  const index = ids.indexOf(instrumentId)
  const target = index + direction
  if (index < 0 || target < 0 || target >= ids.length) {
    return
  }
  const current = ids[index] as number
  ids[index] = ids[target] as number
  ids[target] = current
  reorderMutation.mutate(ids)
}
</script>

<template>
  <main
    class="mx-auto w-full max-w-[1600px] flex-1 space-y-4 px-4 py-6 lg:px-6"
  >
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="space-y-1">
        <div class="flex items-center gap-2">
          <h1 class="text-xl font-semibold tracking-tight">Watchlist</h1>
          <Badge
            :variant="isFull ? 'destructive' : 'secondary'"
            class="tabular-nums"
          >
            {{ count }} / {{ capacity }} instruments
          </Badge>
        </div>
        <p class="max-w-2xl text-sm text-muted-foreground">
          Up to {{ capacity }} canonical instruments with live market state.
          Select an instrument to open its detail page.
        </p>
      </div>
      <Button size="sm" :disabled="isFull" @click="addOpen = true">
        <Plus class="mr-1 size-4" aria-hidden="true" />
        Add instrument
      </Button>
    </div>

    <p v-if="isFull" class="text-xs text-muted-foreground">
      Maximum reached. Remove an instrument to add another.
    </p>

    <SectionState
      v-if="watchlistQuery.isError.value"
      title="Watchlist unavailable"
      :error="watchlistQuery.error.value"
      @retry="watchlistQuery.refetch()"
    />

    <Card v-else-if="watchlistQuery.isPending.value" class="space-y-2 p-4">
      <Skeleton v-for="n in 6" :key="n" class="h-10 w-full" />
    </Card>

    <Card
      v-else-if="entries.length === 0"
      class="flex flex-col items-center gap-3 py-16 text-center"
    >
      <div class="grid size-10 place-items-center rounded-full bg-muted">
        <ListChecks class="size-4 text-muted-foreground" aria-hidden="true" />
      </div>
      <div class="space-y-1">
        <p class="text-sm font-medium">Your watchlist is empty</p>
        <p class="mx-auto max-w-md text-sm text-muted-foreground">
          Add up to {{ capacity }} instruments to track live price, session
          statistics and feature state.
        </p>
      </div>
      <Button size="sm" @click="addOpen = true">
        <Plus class="mr-1 size-4" aria-hidden="true" />
        Add instrument
      </Button>
    </Card>

    <Card v-else class="overflow-hidden">
      <div class="flex flex-wrap items-center gap-2 border-b p-3">
        <div class="relative">
          <Search
            class="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <Input
            v-model="filter"
            placeholder="Search watchlist"
            aria-label="Search watchlist"
            class="h-9 w-56 pl-8"
          />
        </div>
        <Button
          variant="outline"
          size="sm"
          class="h-9"
          @click="watchlistQuery.refetch()"
        >
          <RefreshCw class="mr-1 size-3.5" aria-hidden="true" /> Refresh
        </Button>
        <SegmentedTabs v-model="view" :tabs="viewTabs" capitalize />
        <span class="ml-auto text-xs text-muted-foreground">
          Showing {{ filteredEntries.length }} of
          {{ entries.length }} instruments
        </span>
      </div>

      <div class="overflow-x-auto">
        <Table>
          <TableHeader class="sticky top-0 z-10 bg-card">
            <TableRow class="hover:bg-transparent">
              <template v-if="view === 'decision'">
                <TableHead>Instrument</TableHead>
                <TableHead class="border-l text-right">LTP</TableHead>
                <TableHead class="text-right">Chg%</TableHead>
                <TableHead class="border-l text-right">RRS</TableHead>
                <TableHead class="text-right">RVOL</TableHead>
                <TableHead class="text-right">RVE</TableHead>
                <TableHead class="border-l">Daily</TableHead>
                <TableHead>Trend</TableHead>
                <TableHead class="text-right">Vol</TableHead>
                <TableHead class="border-l">Market</TableHead>
                <TableHead>Sector</TableHead>
                <TableHead class="border-l">Quality</TableHead>
                <TableHead class="text-right">Status</TableHead>
                <TableHead class="text-right">Updated</TableHead>
                <TableHead class="w-[120px] text-right">Actions</TableHead>
              </template>
              <template v-else>
                <TableHead>Instrument</TableHead>
                <TableHead class="hidden xl:table-cell">Market</TableHead>
                <TableHead class="border-l text-right">LTP</TableHead>
                <TableHead class="text-right">Chg</TableHead>
                <TableHead class="text-right">Chg%</TableHead>
                <TableHead class="hidden border-l text-right lg:table-cell"
                  >Open</TableHead
                >
                <TableHead class="hidden text-right lg:table-cell"
                  >High</TableHead
                >
                <TableHead class="hidden text-right lg:table-cell"
                  >Low</TableHead
                >
                <TableHead class="hidden text-right xl:table-cell"
                  >Prev</TableHead
                >
                <TableHead class="hidden text-right lg:table-cell"
                  >Volume</TableHead
                >
                <TableHead class="hidden border-l text-right xl:table-cell"
                  >Bid / Ask</TableHead
                >
                <TableHead class="hidden border-l text-right xl:table-cell"
                  >Updated</TableHead
                >
                <TableHead class="text-right">Status</TableHead>
                <TableHead class="w-[120px] text-right">Actions</TableHead>
              </template>
            </TableRow>
          </TableHeader>
          <TableBody>
            <template v-if="view === 'decision'">
              <WatchlistDecisionRow
                v-for="(entry, index) in filteredEntries"
                :key="entry.instrumentId"
                :entry="entry"
                :feature="featuresById.get(entry.instrumentId) ?? null"
                :first="index === 0"
                :last="index === filteredEntries.length - 1"
                @remove="remove"
                @move="move"
              />
            </template>
            <template v-else>
              <WatchlistRow
                v-for="(entry, index) in filteredEntries"
                :key="entry.instrumentId"
                :entry="entry"
                :first="index === 0"
                :last="index === filteredEntries.length - 1"
                @remove="remove"
                @move="move"
              />
            </template>
            <TableRow v-if="filteredEntries.length === 0">
              <TableCell
                :colspan="tableColspan"
                class="py-10 text-center text-sm text-muted-foreground"
              >
                No instruments match “{{ filter }}”.
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </Card>

    <Dialog v-model:open="addOpen">
      <DialogContent class="sm:max-w-[520px]">
        <DialogHeader>
          <DialogTitle>Add instrument</DialogTitle>
          <DialogDescription>
            {{ remaining }} of {{ capacity }} slots remaining. Search by ticker
            or company name.
          </DialogDescription>
        </DialogHeader>

        <InstrumentSelector
          v-model:open="searchOpen"
          :instruments="searchQuery.data.value ?? []"
          :loading="searchQuery.isFetching.value"
          :model-value="null"
          :search="search"
          @update:model-value="onPick"
          @update:search="onSearch"
        />

        <Alert v-if="addError" variant="destructive">
          <AlertCircle class="size-4" aria-hidden="true" />
          <AlertTitle>Could not add instrument</AlertTitle>
          <AlertDescription>{{ addError.message }}</AlertDescription>
        </Alert>

        <DialogFooter>
          <Button variant="outline" @click="addOpen = false">Close</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </main>
</template>
