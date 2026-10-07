<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { backtestKeys, getBacktestTimeline } from '@/api/backtests'
import PriceChart from '@/components/market-data/PriceChart.vue'
import FeatureOverlayChart from '@/components/feature/FeatureOverlayChart.vue'
import {
  timelineCandles,
  timelineMarkers,
  timelineOverlays,
} from '@/lib/backtest-timeline'

const props = defineProps<{
  runKey: string
  instrumentId: number
  symbol: string
}>()

const timelineQuery = useQuery(() => ({
  queryKey: backtestKeys.timeline(props.runKey, props.instrumentId),
  queryFn: ({ signal }) =>
    getBacktestTimeline(props.runKey, props.instrumentId, signal),
  enabled: props.instrumentId > 0,
}))

const points = computed(() => timelineQuery.data.value?.points ?? [])
const trades = computed(() => timelineQuery.data.value?.trades ?? [])

const candles = computed(() => timelineCandles(points.value))
const overlays = computed(() => timelineOverlays(points.value))
const direction = computed<'long' | 'short'>(() =>
  trades.value.some((trade) => trade.direction === 'SHORT') ? 'short' : 'long',
)
const markers = computed(() =>
  timelineMarkers(points.value, trades.value, direction.value),
)

const syncKey = `backtest-timeline-${props.runKey}-${props.instrumentId}`
</script>

<template>
  <div class="space-y-3">
    <div
      v-if="timelineQuery.isError.value"
      class="text-sm text-destructive"
      role="alert"
    >
      Could not load the run timeline for {{ symbol }}.
    </div>
    <div
      v-else-if="points.length === 0 && !timelineQuery.isPending.value"
      class="text-sm text-muted-foreground"
    >
      No run anchors for {{ symbol }} in this window.
    </div>
    <template v-else>
      <PriceChart :candles="candles" :markers="markers" :sync-key="syncKey" />
      <FeatureOverlayChart :series="overlays" :sync-key="syncKey" />
    </template>
  </div>
</template>
