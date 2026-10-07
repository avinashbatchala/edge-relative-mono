<script setup lang="ts">
import { computed } from 'vue'
import type { FeatureDashboardRow } from '@/api/features'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { marketSummary } from '@/lib/desk'

const props = withDefaults(
  defineProps<{
    rows: FeatureDashboardRow[]
    session?: string | null
    freshness?: string | null
    loading?: boolean
  }>(),
  { session: null, freshness: null, loading: false },
)

const summary = computed(() => marketSummary(props.rows))

/** Derived directional lean from breadth. Explicitly non-authoritative. */
const lean = computed(() => {
  const breadth = summary.value.breadth
  if (breadth === null)
    return { label: 'Unknown', tone: 'text-muted-foreground' }
  if (breadth >= 0.6) return { label: 'Longs favoured', tone: 'text-positive' }
  if (breadth <= 0.4) return { label: 'Shorts favoured', tone: 'text-negative' }
  return { label: 'Two-sided', tone: 'text-muted-foreground' }
})

const structureLabel = computed(() =>
  (summary.value.marketState ?? 'UNKNOWN').replace(/_/g, ' ').toLowerCase(),
)
const volatility = computed(() => {
  const atr = summary.value.avgAtrPercent
  if (atr === null) return '—'
  if (atr >= 1.5) return `Elevated (${atr.toFixed(2)}%)`
  if (atr <= 0.6) return `Low (${atr.toFixed(2)}%)`
  return `Normal (${atr.toFixed(2)}%)`
})
const sessionLabel = computed(() =>
  (props.session ?? 'UNKNOWN').replace(/_/g, ' ').toLowerCase(),
)
</script>

<template>
  <Card class="gap-0 py-0">
    <CardHeader class="px-5 py-4">
      <CardTitle class="text-base">Market context</CardTitle>
      <CardDescription>
        Derived from feature measurements across the watchlist — not an
        execution recommendation.
      </CardDescription>
    </CardHeader>
    <CardContent class="grid gap-4 border-t p-5 sm:grid-cols-2">
      <div class="space-y-1">
        <p class="text-xs text-muted-foreground">Market structure</p>
        <p class="text-sm font-medium capitalize">{{ structureLabel }}</p>
      </div>
      <div class="space-y-1">
        <p class="text-xs text-muted-foreground">Directional lean</p>
        <p class="text-sm font-medium" :class="lean.tone">{{ lean.label }}</p>
      </div>
      <div class="space-y-1">
        <p class="text-xs text-muted-foreground">Breadth (positive RRS)</p>
        <p class="text-sm tabular-nums">
          {{ summary.positives }} / {{ summary.total }}
          <span v-if="summary.breadth !== null" class="text-muted-foreground">
            · {{ (summary.breadth * 100).toFixed(0) }}%
          </span>
        </p>
      </div>
      <div class="space-y-1">
        <p class="text-xs text-muted-foreground">Volatility (avg ATR%)</p>
        <p class="text-sm">{{ volatility }}</p>
      </div>
      <div class="flex items-center gap-2 sm:col-span-2">
        <Badge variant="outline" class="font-normal capitalize">{{
          sessionLabel
        }}</Badge>
        <Badge v-if="freshness" variant="outline" class="font-normal">
          Data {{ freshness.toLowerCase() }}
        </Badge>
        <span class="text-xs text-muted-foreground">Derived market state</span>
      </div>
    </CardContent>
  </Card>
</template>
