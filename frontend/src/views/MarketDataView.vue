<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { refDebounced } from '@vueuse/core'
import { BarChart3, Inbox, Search } from '@lucide/vue'
import { ApiError } from '@/api/http'
import {
  getHistoricalCandles,
  getQuote,
  listInstruments,
  marketDataKeys,
  type HistoricalCandlesRequest,
  type QuoteRequest,
} from '@/api/market-data'
import type { BrokerCandleInterval } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbList,
  BreadcrumbPage,
} from '@/components/ui/breadcrumb'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import ChartToolbar from '@/components/market-data/ChartToolbar.vue'
import DataFreshness from '@/components/market-data/DataFreshness.vue'
import HistoricalTable from '@/components/market-data/HistoricalTable.vue'
import InstrumentDetails from '@/components/market-data/InstrumentDetails.vue'
import InstrumentHeader from '@/components/market-data/InstrumentHeader.vue'
import InstrumentSelector from '@/components/market-data/InstrumentSelector.vue'
import MarketDepthTable from '@/components/market-data/MarketDepthTable.vue'
import PriceChart from '@/components/market-data/PriceChart.vue'
import QuoteSummary from '@/components/market-data/QuoteSummary.vue'
import RawMarketData from '@/components/market-data/RawMarketData.vue'
import SectionState from '@/components/market-data/SectionState.vue'
import { resolveRange } from '@/lib/market-time'
import { useMarketDataStore, type RangeKey } from '@/stores/market-data'

const store = useMarketDataStore()

const instrumentSearch = ref('')
const instrumentSearchOpen = ref(false)
const debouncedInstrumentSearch = refDebounced(instrumentSearch, 250)

function onInstrumentSearch(value: string) {
  instrumentSearch.value = value
}

const instrumentsQuery = useQuery(() => {
  const term = debouncedInstrumentSearch.value.trim()
  const searching = term.length >= 2
  return {
    queryKey: [...marketDataKeys.instruments(), term] as const,
    // When the selector is opened with no query, load the first page so there is something to show.
    queryFn: ({ signal }) =>
      listInstruments(searching ? term : undefined, 50, signal),
    enabled: instrumentSearchOpen.value || searching,
    staleTime: 5 * 60 * 1000,
    gcTime: 30 * 60 * 1000,
    retry: 1,
  }
})

const quoteRequest = computed<QuoteRequest | null>(() => {
  const instrument = store.selectedInstrument
  if (!instrument || !instrument.segment) {
    return null
  }
  return {
    exchange: instrument.exchange,
    segment: instrument.segment,
    tradingSymbol: instrument.tradingSymbol,
  }
})

const quoteQuery = useQuery(() => ({
  queryKey: quoteRequest.value
    ? marketDataKeys.quote(quoteRequest.value)
    : [...marketDataKeys.all, 'quote', 'none'],
  queryFn: ({ signal }) => getQuote(quoteRequest.value as QuoteRequest, signal),
  enabled: quoteRequest.value !== null,
  refetchInterval: 10_000,
  staleTime: 2_000,
  retry: (failureCount: number, error: unknown) => {
    if (
      error instanceof ApiError &&
      (error.isRateLimited || error.isAuthFailure)
    ) {
      return false
    }
    return failureCount < 1
  },
}))

const resolvedRange = computed(() =>
  resolveRange(store.range, store.customStart, store.customEnd),
)

const historyRequest = computed<HistoricalCandlesRequest | null>(() => {
  const instrument = store.selectedInstrument
  const range = resolvedRange.value
  if (
    !instrument ||
    !instrument.segment ||
    !instrument.brokerSymbol ||
    !range
  ) {
    return null
  }
  return {
    exchange: instrument.exchange,
    segment: instrument.segment,
    brokerSymbol: instrument.brokerSymbol,
    start: range.start,
    end: range.end,
    interval: store.interval,
  }
})

const historyQuery = useQuery(() => ({
  queryKey: historyRequest.value
    ? marketDataKeys.history(historyRequest.value)
    : [...marketDataKeys.all, 'history', 'none'],
  queryFn: ({ signal }) =>
    getHistoricalCandles(
      historyRequest.value as HistoricalCandlesRequest,
      signal,
    ),
  enabled: historyRequest.value !== null,
  staleTime: 60_000,
  retry: (failureCount: number, error: unknown) => {
    if (
      error instanceof ApiError &&
      (error.isRateLimited || error.isAuthFailure)
    ) {
      return false
    }
    return failureCount < 1
  },
}))

const quote = computed(() => quoteQuery.data.value ?? null)
const history = computed(() => historyQuery.data.value ?? null)
const candles = computed(() => history.value?.candles ?? [])

const brokerStatus = computed(() => {
  if (quoteQuery.isError.value) {
    return { label: 'Unavailable', variant: 'destructive' as const }
  }
  if (quoteQuery.isSuccess.value) {
    return { label: 'Connected', variant: 'secondary' as const }
  }
  if (quoteQuery.isFetching.value) {
    return { label: 'Connecting', variant: 'outline' as const }
  }
  return { label: 'Idle', variant: 'outline' as const }
})

function onInterval(interval: BrokerCandleInterval) {
  store.interval = interval
}

function onRange(range: RangeKey) {
  store.setRange(range)
}

function onCustomRange(start: string, end: string) {
  store.setCustomRange(start, end)
}

function focusSearch() {
  window.dispatchEvent(
    new KeyboardEvent('keydown', { key: 'k', metaKey: true }),
  )
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
        <Breadcrumb>
          <BreadcrumbList>
            <BreadcrumbItem>
              <BreadcrumbPage>Market Data</BreadcrumbPage>
            </BreadcrumbItem>
          </BreadcrumbList>
        </Breadcrumb>
        <h1 class="text-2xl font-semibold tracking-tight">Market Data</h1>
        <p class="text-sm text-muted-foreground">
          Search and inspect live and historical market information from
          connected brokers.
        </p>
      </div>

      <div class="flex items-center gap-3">
        <Badge :variant="brokerStatus.variant"
          >● Groww · {{ brokerStatus.label }}</Badge
        >
        <DataFreshness
          v-if="quoteQuery.dataUpdatedAt.value"
          :updated-at="quoteQuery.dataUpdatedAt.value"
          label="Last refresh"
        />
      </div>
    </div>

    <Card>
      <CardContent class="p-3">
        <InstrumentSelector
          v-model:open="instrumentSearchOpen"
          :instruments="instrumentsQuery.data.value ?? []"
          :loading="instrumentsQuery.isFetching.value"
          :model-value="store.selectedInstrument"
          :search="instrumentSearch"
          @update:model-value="store.selectInstrument"
          @update:search="onInstrumentSearch"
        />
        <p
          v-if="instrumentsQuery.isError.value"
          class="mt-2 text-xs text-destructive"
        >
          Instrument master unavailable. Search may be incomplete.
        </p>
      </CardContent>
    </Card>

    <Card v-if="!store.selectedInstrument">
      <CardContent class="flex flex-col items-center gap-3 py-16 text-center">
        <div class="grid size-10 place-items-center rounded-full bg-muted">
          <Search class="size-4 text-muted-foreground" aria-hidden="true" />
        </div>
        <div class="space-y-1">
          <p class="text-sm font-medium">No instrument selected</p>
          <p class="mx-auto max-w-md text-sm text-muted-foreground">
            Search for an instrument to inspect live price, session statistics,
            depth and historical candles.
          </p>
        </div>
        <Button size="sm" @click="focusSearch">
          <Search class="size-3.5" aria-hidden="true" />
          Search instruments
        </Button>
      </CardContent>
    </Card>

    <template v-else>
      <Card>
        <CardContent class="pt-6">
          <InstrumentHeader
            :instrument="store.selectedInstrument"
            :quote="quote"
            :loading="quoteQuery.isPending.value"
            :updated-at="quoteQuery.dataUpdatedAt.value || null"
          />
        </CardContent>
      </Card>

      <div class="grid gap-4 lg:grid-cols-[2fr_1fr]">
        <Card>
          <CardContent class="space-y-3 pt-6">
            <ChartToolbar
              :interval="store.interval"
              :range="store.range"
              :custom-start="store.customStart"
              :custom-end="store.customEnd"
              @update:interval="onInterval"
              @update:range="onRange"
              @update:custom="onCustomRange"
            />

            <SectionState
              v-if="historyQuery.isError.value"
              title="Historical data unavailable"
              :error="historyQuery.error.value"
              @retry="historyQuery.refetch()"
            />

            <div v-else-if="historyQuery.isPending.value" class="space-y-2">
              <Skeleton class="h-[420px] w-full" />
            </div>

            <div
              v-else-if="candles.length === 0"
              class="flex h-[420px] flex-col items-center justify-center gap-2 rounded-md border border-dashed text-center"
            >
              <BarChart3
                class="size-5 text-muted-foreground"
                aria-hidden="true"
              />
              <p class="text-sm font-medium">No candles in this range</p>
              <p class="text-xs text-muted-foreground">
                Try a wider range or a different interval.
              </p>
            </div>

            <PriceChart v-else :candles="candles" />
          </CardContent>
        </Card>

        <div class="space-y-4">
          <QuoteSummary v-if="quote" :quote="quote" />
          <Card v-else-if="quoteQuery.isError.value">
            <CardContent class="pt-6">
              <SectionState
                title="Quote unavailable"
                :error="quoteQuery.error.value"
                @retry="quoteQuery.refetch()"
              />
            </CardContent>
          </Card>
          <Card v-else>
            <CardContent class="space-y-3 pt-6">
              <Skeleton class="h-4 w-24" />
              <div class="grid grid-cols-2 gap-4">
                <Skeleton v-for="n in 8" :key="n" class="h-8 w-full" />
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      <Tabs default-value="depth" class="space-y-3">
        <TabsList>
          <TabsTrigger value="depth">Depth</TabsTrigger>
          <TabsTrigger value="historical">Historical</TabsTrigger>
          <TabsTrigger value="details">Details</TabsTrigger>
          <TabsTrigger value="raw">Raw</TabsTrigger>
        </TabsList>

        <TabsContent value="depth">
          <Card>
            <CardContent class="pt-6">
              <SectionState
                v-if="quoteQuery.isError.value"
                title="Depth unavailable"
                :error="quoteQuery.error.value"
                @retry="quoteQuery.refetch()"
              />
              <div v-else-if="quoteQuery.isPending.value" class="space-y-2">
                <Skeleton v-for="n in 5" :key="n" class="h-9 w-full" />
              </div>
              <div
                v-else-if="
                  !quote || (quote.bids.length === 0 && quote.asks.length === 0)
                "
                class="flex flex-col items-center gap-2 py-12 text-center"
              >
                <Inbox
                  class="size-5 text-muted-foreground"
                  aria-hidden="true"
                />
                <p class="text-sm text-muted-foreground">
                  No order book depth available.
                </p>
              </div>
              <MarketDepthTable v-else :bids="quote.bids" :asks="quote.asks" />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="historical">
          <Card>
            <CardContent class="pt-6">
              <SectionState
                v-if="historyQuery.isError.value"
                title="Historical data unavailable"
                :error="historyQuery.error.value"
                @retry="historyQuery.refetch()"
              />
              <div v-else-if="historyQuery.isPending.value" class="space-y-2">
                <Skeleton v-for="n in 6" :key="n" class="h-9 w-full" />
              </div>
              <p
                v-else-if="candles.length === 0"
                class="py-12 text-center text-sm text-muted-foreground"
              >
                No candles in the selected range.
              </p>
              <HistoricalTable v-else :candles="candles" />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="details">
          <InstrumentDetails :instrument="store.selectedInstrument" />
        </TabsContent>

        <TabsContent value="raw">
          <Card>
            <CardContent class="pt-6">
              <RawMarketData
                :instrument="store.selectedInstrument"
                :quote="quote"
                :history="history"
              />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </template>
  </main>
</template>
