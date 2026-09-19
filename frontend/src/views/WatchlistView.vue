<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { AlertCircle, ListChecks, Plus } from '@lucide/vue'
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
import { Card, CardContent } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import InstrumentSelector from '@/components/market-data/InstrumentSelector.vue'
import SectionState from '@/components/market-data/SectionState.vue'
import WatchlistRow from '@/components/watchlist/WatchlistRow.vue'
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
    <div
      class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between"
    >
      <div class="space-y-1">
        <h1 class="text-2xl font-semibold tracking-tight">Watchlist</h1>
        <p class="text-sm text-muted-foreground">
          Up to {{ capacity }} canonical instruments with live market state.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <Badge
          :variant="isFull ? 'destructive' : 'secondary'"
          class="tabular-nums"
        >
          {{ count }} / {{ capacity }} instruments
        </Badge>
        <Button size="sm" :disabled="isFull" @click="addOpen = true">
          <Plus class="size-4" aria-hidden="true" />
          Add instrument
        </Button>
      </div>
    </div>

    <p v-if="isFull" class="text-xs text-muted-foreground">
      Maximum reached. Remove an instrument to add another ({{ remaining }}
      remaining).
    </p>

    <SectionState
      v-if="watchlistQuery.isError.value"
      title="Watchlist unavailable"
      :error="watchlistQuery.error.value"
      @retry="watchlistQuery.refetch()"
    />

    <Card v-else-if="watchlistQuery.isPending.value">
      <CardContent class="space-y-2 pt-6">
        <Skeleton v-for="n in 6" :key="n" class="h-10 w-full" />
      </CardContent>
    </Card>

    <Card v-else-if="entries.length === 0">
      <CardContent class="flex flex-col items-center gap-3 py-16 text-center">
        <div class="grid size-10 place-items-center rounded-full bg-muted">
          <ListChecks class="size-4 text-muted-foreground" aria-hidden="true" />
        </div>
        <div class="space-y-1">
          <p class="text-sm font-medium">Your watchlist is empty</p>
          <p class="mx-auto max-w-md text-sm text-muted-foreground">
            Add up to {{ capacity }} instruments to track live price, session
            statistics and more.
          </p>
        </div>
        <Button size="sm" @click="addOpen = true">
          <Plus class="size-4" aria-hidden="true" />
          Add instrument
        </Button>
      </CardContent>
    </Card>

    <Card v-else>
      <CardContent class="px-0 pt-0">
        <div class="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Instrument</TableHead>
                <TableHead>Market</TableHead>
                <TableHead class="text-right">LTP</TableHead>
                <TableHead class="text-right">Chg</TableHead>
                <TableHead class="text-right">Chg%</TableHead>
                <TableHead class="text-right">Open</TableHead>
                <TableHead class="text-right">High</TableHead>
                <TableHead class="text-right">Low</TableHead>
                <TableHead class="text-right">Prev</TableHead>
                <TableHead class="text-right">Volume</TableHead>
                <TableHead class="text-right">Bid / Ask</TableHead>
                <TableHead class="text-right">Updated</TableHead>
                <TableHead class="text-right">Status</TableHead>
                <TableHead class="w-[120px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              <WatchlistRow
                v-for="(entry, index) in entries"
                :key="entry.instrumentId"
                :entry="entry"
                :first="index === 0"
                :last="index === entries.length - 1"
                @remove="remove"
                @move="move"
              />
            </TableBody>
          </Table>
        </div>
      </CardContent>
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
