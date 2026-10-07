<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ArrowRight, Activity, ListChecks } from '@lucide/vue'
import {
  featureKeys,
  getFeatureDashboard,
  getFeatureDiagnostics,
} from '@/api/features'
import { getLtp, marketDataKeys } from '@/api/market-data'
import { getOpportunities, opportunityKeys } from '@/api/opportunities'
import type { BrokerSegment } from '@/api/types'
import { getWatchlist, watchlistKeys } from '@/api/watchlist'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import MarketContextPanel from '@/components/trade/MarketContextPanel.vue'
import OpportunityBoard from '@/components/trade/OpportunityBoard.vue'
import AttentionFeed from '@/components/trade/AttentionFeed.vue'
import SystemTile from '@/components/desk/SystemTile.vue'
import PortfolioTile from '@/components/desk/PortfolioTile.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { formatAge } from '@/lib/format'
import { recentRows } from '@/lib/desk'
import { useAttentionFeed } from '@/composables/useAttentionFeed'

const attention = useAttentionFeed()

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
    const symbols = groups.get(entry.segment) ?? []
    symbols.push(`${entry.exchange}_${entry.symbol}`)
    groups.set(entry.segment, symbols)
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

const dashboardQuery = useQuery(() => ({
  queryKey: featureKeys.dashboard(),
  queryFn: ({ signal }) => getFeatureDashboard(signal),
  staleTime: 15_000,
  refetchInterval: 30_000,
  retry: 1,
}))

const diagnosticsQuery = useQuery(() => ({
  queryKey: featureKeys.diagnostics(),
  queryFn: ({ signal }) => getFeatureDiagnostics(signal),
  staleTime: 15_000,
  retry: 1,
}))

const opportunitiesQuery = useQuery(() => ({
  queryKey: opportunityKeys.list(),
  queryFn: ({ signal }) => getOpportunities(signal),
  staleTime: 15_000,
  retry: 1,
}))

const featureRows = computed(() => dashboardQuery.data.value ?? [])
const session = computed(
  () => diagnosticsQuery.data.value?.freshness?.sessionContext ?? null,
)
const freshnessState = computed(
  () => diagnosticsQuery.data.value?.freshness?.state ?? null,
)
const opportunities = computed(() => opportunitiesQuery.data.value ?? [])
const recent = computed(() => recentRows(featureRows.value, 5))
const notices = computed(() =>
  (diagnosticsQuery.data.value?.metricAvailability ?? []).slice(0, 3),
)
</script>

<template>
  <main
    class="mx-auto w-full max-w-[1600px] flex-1 space-y-4 px-4 py-6 lg:px-6"
  >
    <div class="space-y-1">
      <h1 class="text-2xl font-semibold tracking-tight">Desk</h1>
      <p class="text-sm text-muted-foreground">
        Market, system and opportunity state at a glance. Advisory only — no
        execution.
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

      <MarketContextPanel
        :rows="featureRows"
        :session="session"
        :freshness="freshnessState"
        :loading="dashboardQuery.isPending.value"
      />
      <SystemTile />
      <PortfolioTile />
    </div>

    <div class="space-y-4">
      <OpportunityBoard
        :opportunities="opportunities"
        :features="featureRows"
        :loading="opportunitiesQuery.isPending.value"
        :limit="8"
      />
      <div class="flex flex-wrap gap-2">
        <Button as-child variant="outline" size="sm">
          <RouterLink to="/setups">
            Open opportunities
            <ArrowRight class="size-4" aria-hidden="true" />
          </RouterLink>
        </Button>
        <Button as-child variant="ghost" size="sm">
          <RouterLink to="/scanner">Scanner</RouterLink>
        </Button>
      </div>
    </div>

    <div class="grid gap-4 lg:grid-cols-2">
      <Card>
        <CardHeader class="pb-3">
          <CardTitle class="text-base">Attention</CardTitle>
          <CardDescription
            >Meaningful state changes from the live feature
            stream.</CardDescription
          >
        </CardHeader>
        <CardContent>
          <AttentionFeed :events="attention.events.value" />
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="pb-3">
          <CardTitle class="text-base">What changed</CardTitle>
          <CardDescription>Most recent feature observations.</CardDescription>
        </CardHeader>
        <CardContent>
          <div v-if="dashboardQuery.isPending.value" class="space-y-2">
            <Skeleton v-for="n in 3" :key="n" class="h-8 w-full" />
          </div>
          <ul v-else-if="recent.length" class="space-y-2 text-sm">
            <li
              v-for="row in recent"
              :key="`${row.instrumentId}:${row.timeframe}`"
              class="flex items-center justify-between gap-2"
            >
              <RouterLink
                :to="{ name: 'market-ticker', params: { symbol: row.symbol } }"
                class="font-medium hover:underline"
              >
                {{ row.symbol }}
              </RouterLink>
              <span class="flex items-center gap-3 text-xs tabular-nums">
                <span
                  :class="
                    row.rrsRaw !== null && row.rrsRaw > 0
                      ? 'text-positive'
                      : 'text-muted-foreground'
                  "
                >
                  RRS {{ row.rrsRaw === null ? '—' : row.rrsRaw.toFixed(2) }}
                </span>
                <span class="text-muted-foreground">{{
                  formatAge(row.observationTime)
                }}</span>
              </span>
            </li>
          </ul>
          <EmptyState
            v-else
            :icon="ListChecks"
            title="Nothing yet"
            description="Feature observations will appear here once data is ingested."
          />
        </CardContent>
      </Card>
    </div>

    <Card>
      <CardHeader class="pb-3">
        <CardTitle class="text-base">Notices</CardTitle>
        <CardDescription>Data-quality and coverage flags.</CardDescription>
      </CardHeader>
      <CardContent>
        <ul v-if="notices.length" class="space-y-1 text-sm">
          <li
            v-for="notice in notices"
            :key="`${notice.metric}:${notice.state}`"
            class="flex items-center justify-between gap-2"
          >
            <span>{{ notice.metric }}</span>
            <span class="text-xs text-muted-foreground">
              {{ notice.state }} · {{ notice.affectedCount }} affected
            </span>
          </li>
        </ul>
        <p v-else class="text-sm text-muted-foreground">
          No known data issues.
        </p>
      </CardContent>
    </Card>

    <div class="flex flex-wrap gap-2">
      <Button as-child variant="outline" size="sm">
        <RouterLink to="/watchlist">
          <ListChecks class="size-4" aria-hidden="true" />
          Open watchlist
          <ArrowRight class="size-4" aria-hidden="true" />
        </RouterLink>
      </Button>
      <Button as-child variant="outline" size="sm">
        <RouterLink to="/chart">
          <Activity class="size-4" aria-hidden="true" />
          Inspect a chart
          <ArrowRight class="size-4" aria-hidden="true" />
        </RouterLink>
      </Button>
    </div>
  </main>
</template>
