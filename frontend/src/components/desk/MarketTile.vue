<script setup lang="ts">
import { computed } from 'vue'
import type { FeatureDashboardRow } from '@/api/features'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatPercent } from '@/lib/format'
import { marketSummary } from '@/lib/desk'

const props = withDefaults(
  defineProps<{ rows: FeatureDashboardRow[]; loading?: boolean }>(),
  { loading: false },
)

const summary = computed(() => marketSummary(props.rows))
</script>

<template>
  <Card>
    <CardHeader class="pb-3">
      <CardDescription>Market</CardDescription>
      <CardTitle class="text-2xl">
        <Skeleton v-if="loading" class="h-7 w-24" />
        <template v-else>{{ summary.marketState ?? 'Unknown' }}</template>
      </CardTitle>
    </CardHeader>
    <CardContent class="space-y-1 text-xs text-muted-foreground">
      <div class="flex justify-between">
        <span>Breadth (RRS &gt; 0)</span>
        <span class="tabular-nums">{{
          summary.breadth === null ? '—' : formatPercent(summary.breadth * 100)
        }}</span>
      </div>
      <div class="flex justify-between">
        <span>Advancers / decliners</span>
        <span class="tabular-nums"
          >{{ summary.positives }} / {{ summary.negatives }}</span
        >
      </div>
      <div class="flex justify-between">
        <span>Avg ATR %</span>
        <span class="tabular-nums">{{
          summary.avgAtrPercent === null
            ? '—'
            : formatPercent(summary.avgAtrPercent)
        }}</span>
      </div>
    </CardContent>
  </Card>
</template>
