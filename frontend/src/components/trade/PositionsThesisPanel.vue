<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { featureKeys, getFeatureDashboard } from '@/api/features'
import type { BrokerPosition } from '@/api/portfolio'
import { Badge } from '@/components/ui/badge'
import { formatInr, formatQuantity } from '@/lib/format'

const props = defineProps<{ positions: BrokerPosition[] }>()

const dashboard = useQuery({
  queryKey: featureKeys.dashboard(),
  queryFn: ({ signal }) => getFeatureDashboard(signal),
})

const open = computed(() =>
  props.positions.filter(
    (position) =>
      position.quantity !== 0 && featureFor(position.tradingSymbol) !== null,
  ),
)

function featureFor(symbol: string) {
  return dashboard.data.value?.find((row) => row.symbol === symbol) ?? null
}

function direction(position: BrokerPosition): 'LONG' | 'SHORT' {
  return position.quantity >= 0 ? 'LONG' : 'SHORT'
}

function unrealized(position: BrokerPosition): number | null {
  const last = featureFor(position.tradingSymbol)?.lastPrice ?? null
  if (last === null || position.netPrice === null) return null
  return (last - position.netPrice) * position.quantity
}

function thesis(position: BrokerPosition) {
  const feature = featureFor(position.tradingSymbol)
  if (!feature) {
    return {
      state: 'No measurement',
      tone: 'text-muted-foreground',
      aligned: false,
    }
  }
  const long = direction(position) === 'LONG'
  const rrsAligned =
    feature.rrsRaw !== null && (long ? feature.rrsRaw > 0 : feature.rrsRaw < 0)
  const structureLabel = (feature.marketState ?? '').toUpperCase()
  const structureOpposed = long
    ? structureLabel === 'BEAR_STRUCTURE'
    : structureLabel === 'BULL_STRUCTURE'
  if (rrsAligned && !structureOpposed) {
    return { state: 'Thesis intact', tone: 'text-positive', aligned: true }
  }
  if (!rrsAligned || structureOpposed) {
    return {
      state: 'Thesis weakening',
      tone: 'text-amber-600 dark:text-amber-400',
      aligned: false,
    }
  }
  return { state: 'Mixed', tone: 'text-muted-foreground', aligned: false }
}
</script>

<template>
  <div v-if="open.length" class="mb-3 grid gap-3 lg:grid-cols-2">
    <div
      v-for="position in open"
      :key="`${position.tradingSymbol}:${position.product}`"
      class="rounded-lg border p-3 text-sm"
    >
      <div class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <span class="font-medium">{{ position.tradingSymbol }}</span>
          <Badge variant="outline">{{ direction(position) }}</Badge>
          <span class="text-xs text-muted-foreground">{{
            position.product
          }}</span>
        </div>
        <span class="text-xs font-medium" :class="thesis(position).tone">
          {{ thesis(position).state }}
        </span>
      </div>
      <div class="mt-2 grid grid-cols-3 gap-2 text-xs">
        <span class="text-muted-foreground"
          >Qty
          <span class="text-foreground tabular-nums">{{
            formatQuantity(position.quantity)
          }}</span></span
        >
        <span class="text-muted-foreground"
          >Avg
          <span class="text-foreground tabular-nums">{{
            formatInr(position.netPrice)
          }}</span></span
        >
        <span class="text-muted-foreground"
          >Unreal.
          <span
            class="tabular-nums"
            :class="
              (unrealized(position) ?? 0) >= 0
                ? 'text-positive'
                : 'text-negative'
            "
            >{{ formatInr(unrealized(position)) }}</span
          ></span
        >
      </div>
      <p class="mt-2 text-[11px] text-muted-foreground">
        Thesis validity is derived from market structure and the stock's RRS
        (advisory). Protective stop and structural invalidation come from the
        trade plan once the canonical position link is wired.
      </p>
    </div>
  </div>
</template>
