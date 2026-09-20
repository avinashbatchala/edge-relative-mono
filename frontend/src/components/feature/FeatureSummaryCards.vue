<script setup lang="ts">
import { computed } from 'vue'
import { Activity, Layers, TrendingDown, TrendingUp, Waves } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardAction,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import type { FeatureDashboardRow } from '@/api/features'

const props = defineProps<{ rows: FeatureDashboardRow[]; timeframe: string }>()

const total = computed(() => props.rows.length)
const strong = computed(
  () => props.rows.filter((row) => (row.rrsRaw ?? 0) > 0).length,
)
const weak = computed(
  () => props.rows.filter((row) => (row.rrsRaw ?? 0) < 0).length,
)
const expanding = computed(
  () => props.rows.filter((row) => (row.rve ?? 0) > 0).length,
)
const contracting = computed(
  () => props.rows.filter((row) => (row.rve ?? 0) < 0).length,
)
</script>

<template>
  <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
    <Card
      class="gap-2 overflow-hidden bg-gradient-to-t from-primary/5 to-card py-5 shadow-xs"
    >
      <CardHeader>
        <CardDescription>Watchlist</CardDescription>
        <CardTitle class="text-2xl font-semibold tabular-nums">{{
          total
        }}</CardTitle>
        <CardAction>
          <Badge variant="outline"
            ><Layers class="size-3" /> {{ timeframe }}</Badge
          >
        </CardAction>
      </CardHeader>
      <CardFooter class="flex-col items-start gap-1 text-sm">
        <div class="flex items-center gap-2 font-medium">
          Active instruments <Activity class="size-4" />
        </div>
        <div class="text-muted-foreground">Point-in-time feature state</div>
      </CardFooter>
    </Card>

    <Card
      class="gap-2 overflow-hidden bg-gradient-to-t from-primary/5 to-card py-5 shadow-xs"
    >
      <CardHeader>
        <CardDescription>Relative strength</CardDescription>
        <CardTitle class="text-2xl font-semibold tabular-nums">{{
          strong
        }}</CardTitle>
        <CardAction>
          <Badge variant="outline">
            <TrendingDown class="size-3" /> {{ weak }}
          </Badge>
        </CardAction>
      </CardHeader>
      <CardFooter class="flex-col items-start gap-1 text-sm">
        <div class="flex items-center gap-2 font-medium">
          Strong versus weak <TrendingUp class="size-4 text-emerald-600" />
        </div>
        <div class="text-muted-foreground">Positive vs negative RRS</div>
      </CardFooter>
    </Card>

    <Card
      class="gap-2 overflow-hidden bg-gradient-to-t from-primary/5 to-card py-5 shadow-xs"
    >
      <CardHeader>
        <CardDescription>Participation</CardDescription>
        <CardTitle class="text-2xl font-semibold tabular-nums">{{
          expanding
        }}</CardTitle>
        <CardAction>
          <Badge variant="outline">
            <TrendingDown class="size-3" /> {{ contracting }}
          </Badge>
        </CardAction>
      </CardHeader>
      <CardFooter class="flex-col items-start gap-1 text-sm">
        <div class="flex items-center gap-2 font-medium">
          Volume expanding <Waves class="size-4 text-sky-600" />
        </div>
        <div class="text-muted-foreground">RVE expanding vs contracting</div>
      </CardFooter>
    </Card>
  </div>
</template>
