<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Activity, ArrowRight, ListChecks } from '@lucide/vue'
import { getLtp, marketDataKeys } from '@/api/market-data'
import type { BrokerSegment } from '@/api/types'
import { getWatchlist, watchlistKeys } from '@/api/watchlist'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

const watchlistQuery = useQuery(() => ({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
  staleTime: 30_000,
  retry: 1,
}))

const response = computed(() => watchlistQuery.data.value ?? null)
const entries = computed(() => response.value?.entries ?? [])
const capacity = computed(() => response.value?.capacity ?? 20)
const count = computed(() => response.value?.count ?? 0)

const liveQuery = useQuery(() => {
  const list = entries.value
  const groups = new Map<string, string[]>()
  for (const entry of list) {
    if (!entry.segment) {
      continue
    }
    const key = entry.segment
    const symbols = groups.get(key) ?? []
    symbols.push(`${entry.exchange}_${entry.symbol}`)
    groups.set(key, symbols)
  }
  const grouped = [...groups.entries()]
  return {
    queryKey: [...marketDataKeys.all, 'overview-ltp', grouped],
    queryFn: async ({ signal }) => {
      const prices: Record<string, number | null> = {}
      for (const [segment, symbols] of grouped) {
        const batch = await getLtp(segment as BrokerSegment, symbols, signal)
        for (const item of batch) {
          prices[item.exchangeSymbol] = item.lastPrice
        }
      }
      return prices
    },
    enabled: grouped.length > 0,
    refetchInterval: 30_000,
    staleTime: 10_000,
    retry: 1,
  }
})

const liveCount = computed(
  () =>
    Object.values(liveQuery.data.value ?? {}).filter(
      (price) => price !== null && price !== undefined,
    ).length,
)

const brokerStatus = computed(() => {
  if (entries.value.length === 0) {
    return { label: 'Idle', variant: 'outline' as const }
  }
  if (liveQuery.isError.value) {
    return { label: 'Unavailable', variant: 'destructive' as const }
  }
  if (liveQuery.isSuccess.value) {
    return { label: 'Connected', variant: 'secondary' as const }
  }
  return { label: 'Checking', variant: 'outline' as const }
})
</script>

<template>
  <main
    class="mx-auto w-full max-w-[1600px] flex-1 space-y-4 px-4 py-6 lg:px-6"
  >
    <div class="space-y-1">
      <h1 class="text-2xl font-semibold tracking-tight">Overview</h1>
      <p class="text-sm text-muted-foreground">
        A compact view of your watchlist and market-data health.
      </p>
    </div>

    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <Card>
        <CardHeader class="pb-3">
          <CardDescription>Watchlist</CardDescription>
          <CardTitle class="text-2xl tabular-nums">
            <Skeleton v-if="watchlistQuery.isPending.value" class="h-7 w-20" />
            <template v-else>{{ count }} / {{ capacity }}</template>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p class="text-xs text-muted-foreground">
            Canonical instruments tracked
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="pb-3">
          <CardDescription>Live data</CardDescription>
          <CardTitle class="text-2xl tabular-nums">
            <Skeleton v-if="liveQuery.isPending.value" class="h-7 w-20" />
            <template v-else>{{ liveCount }} / {{ count }}</template>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p class="text-xs text-muted-foreground">
            Instruments with a current price
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="pb-3">
          <CardDescription>Broker</CardDescription>
          <CardTitle class="text-2xl">
            <Badge :variant="brokerStatus.variant"
              >Groww · {{ brokerStatus.label }}</Badge
            >
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p class="text-xs text-muted-foreground">
            Derived from recent market-data calls
          </p>
        </CardContent>
      </Card>
    </div>

    <div class="flex flex-wrap gap-2">
      <Button as-child variant="outline" size="sm">
        <RouterLink to="/watchlist">
          <ListChecks class="size-4" aria-hidden="true" />
          Open watchlist
          <ArrowRight class="size-4" aria-hidden="true" />
        </RouterLink>
      </Button>
      <Button as-child variant="outline" size="sm">
        <RouterLink to="/market">
          <Activity class="size-4" aria-hidden="true" />
          Inspect market data
          <ArrowRight class="size-4" aria-hidden="true" />
        </RouterLink>
      </Button>
    </div>
  </main>
</template>
