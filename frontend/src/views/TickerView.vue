<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Inbox, Search } from '@lucide/vue'
import { getCandles, historyKeys } from '@/api/history'
import { ApiError } from '@/api/http'
import { getWatchlist, watchlistKeys } from '@/api/watchlist'
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
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import EmptyState from '@/components/common/EmptyState.vue'
import ChartToolbar from '@/components/market-data/ChartToolbar.vue'
import DataFreshness from '@/components/market-data/DataFreshness.vue'
import FeatureHistoryPanel from '@/components/feature/FeatureHistoryPanel.vue'
import FundamentalPanel from '@/components/fundamental/FundamentalPanel.vue'
import HistoricalTable from '@/components/market-data/HistoricalTable.vue'
import InstrumentDetails from '@/components/market-data/InstrumentDetails.vue'
import MarketDepthTable from '@/components/market-data/MarketDepthTable.vue'
import OptionChainPanel from '@/components/market-data/OptionChainPanel.vue'
import PriceChart from '@/components/market-data/PriceChart.vue'
import RawMarketData from '@/components/market-data/RawMarketData.vue'
import SectionState from '@/components/market-data/SectionState.vue'
import SessionSummary from '@/components/market-data/SessionSummary.vue'
import { useCommandPalette } from '@/composables/useCommandPalette'
import { pickUnderlying } from '@/lib/instrument'
import {
  formatInr,
  formatPercent,
  formatSigned,
  movementClass,
} from '@/lib/format'
import { resolveRange } from '@/lib/market-time'
import { useMarketDataStore, type RangeKey } from '@/stores/market-data'

const props = defineProps<{ symbol: string }>()
const store = useMarketDataStore()
const palette = useCommandPalette()

// --- Resolve the underlying for the route -----------------------------------
const underlyingQuery = useQuery(() => ({
  queryKey: [...marketDataKeys.instruments(), props.symbol] as const,
  queryFn: ({ signal }) => listInstruments(props.symbol, 50, signal),
  enabled: props.symbol.length > 0,
  staleTime: 5 * 60 * 1000,
  gcTime: 30 * 60 * 1000,
  retry: 1,
}))

const underlying = computed(() =>
  pickUnderlying(underlyingQuery.data.value ?? [], props.symbol),
)

const unresolved = computed(
  () => underlyingQuery.isSuccess.value && underlying.value === null,
)

// --- Quote ------------------------------------------------------------------
const quoteRequest = computed<QuoteRequest | null>(() => {
  const instrument = underlying.value
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

// --- Historical candles -----------------------------------------------------
const resolvedRange = computed(() =>
  resolveRange(store.range, store.customStart, store.customEnd),
)

const historyRequest = computed<HistoricalCandlesRequest | null>(() => {
  const instrument = underlying.value
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
const change = computed(() => quote.value?.dayChange ?? null)
const changePercent = computed(() => quote.value?.dayChangePercent ?? null)

// --- Canonical history (preferred) vs live broker fallback ------------------
const watchlistQuery = useQuery(() => ({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
  staleTime: 30_000,
  retry: 1,
}))

// Only registered canonical timeframes can be served from the store.
const INTERVAL_TO_TIMEFRAME: Partial<Record<BrokerCandleInterval, string>> = {
  ONE_MINUTE: 'M1',
  THREE_MINUTE: 'M3',
  FIVE_MINUTE: 'M5',
  FIFTEEN_MINUTE: 'M15',
  THIRTY_MINUTE: 'M30',
  ONE_HOUR: 'H1',
  FOUR_HOUR: 'H4',
  ONE_DAY: 'D1',
  ONE_WEEK: 'W1',
}

const canonicalInstrumentId = computed(() => {
  const instrument = underlying.value
  if (!instrument) {
    return null
  }
  const entry = (watchlistQuery.data.value?.entries ?? []).find(
    (candidate) =>
      candidate.exchange === instrument.exchange &&
      candidate.symbol.toUpperCase() === instrument.tradingSymbol.toUpperCase(),
  )
  return entry?.instrumentId ?? null
})

const canonicalTimeframe = computed(
  () => INTERVAL_TO_TIMEFRAME[store.interval] ?? null,
)

const canonicalHistoryQuery = useQuery(() => {
  const instrumentId = canonicalInstrumentId.value
  const timeframe = canonicalTimeframe.value
  const range = resolvedRange.value
  const ready = instrumentId !== null && timeframe !== null && range !== null
  return {
    queryKey: ready
      ? historyKeys.candles(
          instrumentId as number,
          timeframe as string,
          (range as { start: string }).start,
          (range as { end: string }).end,
        )
      : [...historyKeys.all, 'candles', 'none'],
    queryFn: ({ signal }: { signal: AbortSignal }) =>
      getCandles(
        instrumentId as number,
        timeframe as string,
        (range as { start: string }).start,
        (range as { end: string }).end,
        5000,
        signal,
      ),
    enabled: ready,
    staleTime: 60_000,
    retry: 1,
  }
})

const canonicalCandles = computed(() => canonicalHistoryQuery.data.value ?? [])
const usingCanonicalHistory = computed(() => canonicalCandles.value.length > 0)
const candles = computed(() =>
  usingCanonicalHistory.value
    ? canonicalCandles.value
    : (history.value?.candles ?? []),
)

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
</script>

<template>
  <main
    class="mx-auto flex w-full max-w-[1900px] flex-1 flex-col gap-4 px-4 py-4 lg:px-6"
  >
    <Breadcrumb class="text-xs">
      <BreadcrumbList>
        <BreadcrumbItem>
          <BreadcrumbLink as-child>
            <RouterLink :to="{ name: 'market-search' }">Chart</RouterLink>
          </BreadcrumbLink>
        </BreadcrumbItem>
        <BreadcrumbSeparator />
        <BreadcrumbItem>
          <BreadcrumbPage>{{ symbol.toUpperCase() }}</BreadcrumbPage>
        </BreadcrumbItem>
      </BreadcrumbList>
    </Breadcrumb>

    <Card v-if="unresolved">
      <CardContent class="flex flex-col items-center gap-3 py-16 text-center">
        <div class="grid size-10 place-items-center rounded-full bg-muted">
          <Search class="size-4 text-muted-foreground" aria-hidden="true" />
        </div>
        <div class="space-y-1">
          <p class="text-sm font-medium">Instrument not found</p>
          <p class="mx-auto max-w-md text-sm text-muted-foreground">
            No listed instrument matches “{{ symbol }}”.
          </p>
        </div>
        <Button size="sm" @click="palette.show()">
          <Search class="size-3.5" aria-hidden="true" />
          Search instruments
        </Button>
      </CardContent>
    </Card>

    <Card v-else-if="!underlying">
      <CardContent class="space-y-4 pt-6">
        <Skeleton class="h-24 w-full" />
        <Skeleton class="h-[420px] w-full" />
      </CardContent>
    </Card>

    <template v-else>
      <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
        <h1 class="text-xl font-semibold tracking-tight">
          {{ underlying.tradingSymbol }}
        </h1>
        <Badge variant="secondary">{{ underlying.exchange }}</Badge>
        <Badge v-if="underlying.segment" variant="outline">{{
          underlying.segment
        }}</Badge>
        <Badge v-if="underlying.instrumentType" variant="outline">{{
          underlying.instrumentType
        }}</Badge>
        <span class="min-w-0 truncate text-sm text-muted-foreground">
          {{ underlying.name ?? 'Unnamed instrument' }}
        </span>
      </div>

      <div class="grid gap-3 sm:grid-cols-2">
        <Card class="flex h-full flex-col">
          <CardHeader class="pb-2">
            <CardDescription>Price</CardDescription>
            <CardTitle class="text-3xl tabular-nums">
              <Skeleton
                v-if="quoteQuery.isPending.value && !quote"
                class="h-8 w-32"
              />
              <template v-else>{{
                formatInr(quote?.lastPrice ?? null)
              }}</template>
            </CardTitle>
          </CardHeader>
          <CardContent class="space-y-2">
            <p class="flex items-center gap-2 text-sm tabular-nums">
              <span :class="movementClass(change)">{{
                formatSigned(change)
              }}</span>
              <span :class="movementClass(change)">{{
                formatPercent(changePercent)
              }}</span>
            </p>
            <div class="flex flex-wrap items-center gap-2">
              <Badge :variant="brokerStatus.variant"
                >● Groww · {{ brokerStatus.label }}</Badge
              >
              <DataFreshness
                v-if="quoteQuery.dataUpdatedAt.value"
                :updated-at="quoteQuery.dataUpdatedAt.value"
              />
            </div>
          </CardContent>
        </Card>

        <SessionSummary :quote="quote" :loading="quoteQuery.isPending.value" />
      </div>

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

          <div class="flex items-center gap-2 text-xs text-muted-foreground">
            <Badge :variant="usingCanonicalHistory ? 'secondary' : 'outline'">
              {{ usingCanonicalHistory ? 'Canonical' : 'Live broker' }}
            </Badge>
            <span>
              {{
                usingCanonicalHistory
                  ? 'From the canonical store'
                  : 'Live broker history (not yet downloaded)'
              }}
            </span>
          </div>

          <SectionState
            v-if="historyQuery.isError.value"
            title="Historical data unavailable"
            :error="historyQuery.error.value"
            @retry="historyQuery.refetch()"
          />

          <div v-else-if="historyQuery.isPending.value" class="h-[420px]">
            <Skeleton class="h-full w-full" />
          </div>

          <div
            v-else-if="candles.length === 0"
            class="flex h-[420px] flex-col items-center justify-center gap-2 rounded-md border border-dashed text-center"
          >
            <Inbox class="size-5 text-muted-foreground" aria-hidden="true" />
            <p class="text-sm font-medium">No candles in this range</p>
            <p class="text-xs text-muted-foreground">
              Try a wider range or a different interval.
            </p>
          </div>

          <div v-else class="h-[460px]">
            <PriceChart :candles="candles" fill />
          </div>
        </CardContent>
      </Card>

      <Card
        class="flex h-[600px] min-h-[420px] flex-col gap-0 overflow-hidden py-0"
      >
        <Tabs default-value="features" class="flex h-full min-h-0 flex-col">
          <div class="border-b p-2">
            <TabsList
              class="h-auto w-full flex-wrap justify-start gap-1 border-0 bg-transparent p-0"
            >
              <TabsTrigger
                value="features"
                class="h-8 flex-none border border-border px-3 data-[state=active]:border-transparent"
                >Features</TabsTrigger
              >
              <TabsTrigger
                value="depth"
                class="h-8 flex-none border border-border px-3 data-[state=active]:border-transparent"
                >Depth</TabsTrigger
              >
              <TabsTrigger
                value="options"
                class="h-8 flex-none border border-border px-3 data-[state=active]:border-transparent"
                >Options</TabsTrigger
              >
              <TabsTrigger
                value="fundamentals"
                class="h-8 flex-none border border-border px-3 data-[state=active]:border-transparent"
                >Fundamentals</TabsTrigger
              >
              <TabsTrigger
                value="historical"
                class="h-8 flex-none border border-border px-3 data-[state=active]:border-transparent"
                >Historical</TabsTrigger
              >
              <TabsTrigger
                value="details"
                class="h-8 flex-none border border-border px-3 data-[state=active]:border-transparent"
                >Details</TabsTrigger
              >
              <TabsTrigger
                value="raw"
                class="h-8 flex-none border border-border px-3 data-[state=active]:border-transparent"
                >Raw</TabsTrigger
              >
            </TabsList>
          </div>

          <div class="min-h-0 flex-1 overflow-auto p-3">
            <TabsContent value="features" class="mt-0">
              <FeatureHistoryPanel
                v-if="canonicalInstrumentId !== null"
                :instrument-id="canonicalInstrumentId"
                :symbol="underlying.tradingSymbol"
              />
              <EmptyState
                v-else
                :icon="Inbox"
                title="Not on the watchlist"
                description="Add this instrument to the watchlist to load its point-in-time features."
              />
            </TabsContent>

            <TabsContent value="depth" class="mt-0">
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
            </TabsContent>

            <TabsContent value="options" class="mt-0">
              <OptionChainPanel
                :exchange="underlying.exchange"
                :underlying="underlying.tradingSymbol"
              />
            </TabsContent>

            <TabsContent value="fundamentals" class="mt-0">
              <FundamentalPanel :instrument-id="canonicalInstrumentId" />
            </TabsContent>

            <TabsContent value="historical" class="mt-0">
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
            </TabsContent>

            <TabsContent value="details" class="mt-0">
              <InstrumentDetails :instrument="underlying" :quote="quote" />
            </TabsContent>

            <TabsContent value="raw" class="mt-0">
              <RawMarketData
                :instrument="underlying"
                :quote="quote"
                :history="history"
              />
            </TabsContent>
          </div>
        </Tabs>
      </Card>
    </template>
  </main>
</template>
