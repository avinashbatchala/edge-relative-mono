<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ArrowLeft, ExternalLink } from '@lucide/vue'
import { RouterLink } from 'vue-router'
import { getWatchlist, watchlistKeys } from '@/api/watchlist'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import FeatureHistoryPanel from '@/components/feature/FeatureHistoryPanel.vue'

const props = defineProps<{ symbol: string }>()

const watchlistQuery = useQuery(() => ({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
  staleTime: 30_000,
}))

const entry = computed(
  () =>
    watchlistQuery.data.value?.entries.find(
      (candidate) =>
        candidate.symbol.toUpperCase() === props.symbol.toUpperCase(),
    ) ?? null,
)

function openMarketData() {
  window.open(`/market/${props.symbol}`, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <main
    class="mx-auto w-full max-w-[1600px] flex-1 space-y-4 px-4 py-6 lg:px-6"
  >
    <header class="flex flex-wrap items-start justify-between gap-3">
      <div class="space-y-1">
        <RouterLink
          to="/features"
          class="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft class="size-3.5" aria-hidden="true" /> Feature Dashboard
        </RouterLink>
        <div class="flex items-center gap-2">
          <h1 class="text-xl font-semibold tracking-tight">
            {{ entry?.symbol ?? symbol }}
          </h1>
          <Badge v-if="entry" variant="outline">{{ entry.exchange }}</Badge>
        </div>
        <p class="text-sm text-muted-foreground">
          {{ entry?.name ?? 'Feature history, quality and diagnostics' }}
        </p>
      </div>
      <Button variant="outline" size="sm" @click="openMarketData">
        Open market data
        <ExternalLink class="ml-1 size-3.5" aria-hidden="true" />
      </Button>
    </header>

    <Card v-if="watchlistQuery.isPending.value" class="space-y-2 p-4">
      <Skeleton class="h-10 w-64" />
      <Skeleton class="h-[300px] w-full" />
      <Skeleton class="h-[140px] w-full" />
    </Card>

    <Card
      v-else-if="!entry"
      class="p-6 text-sm text-muted-foreground"
      role="status"
    >
      {{ symbol }} is not on the active watchlist, so its feature history is
      unavailable. Add it on the Watchlist page to collect and display features.
    </Card>

    <FeatureHistoryPanel
      v-else
      :instrument-id="entry.instrumentId"
      :symbol="entry.symbol"
    />
  </main>
</template>
