<script setup lang="ts">
import { computed } from 'vue'
import type { BrokerInstrument, BrokerQuote } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import DataFreshness from '@/components/market-data/DataFreshness.vue'
import {
  formatInr,
  formatPercent,
  formatSigned,
  movementClass,
} from '@/lib/format'

const props = defineProps<{
  instrument: BrokerInstrument
  quote: BrokerQuote | null
  loading: boolean
  updatedAt: number | null
}>()

const change = computed(() => props.quote?.dayChange ?? null)
const changePercent = computed(() => props.quote?.dayChangePercent ?? null)
</script>

<template>
  <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
    <div class="min-w-0 space-y-1.5">
      <div class="flex flex-wrap items-center gap-2">
        <h2 class="text-xl font-semibold tracking-tight">
          {{ instrument.tradingSymbol }}
        </h2>
        <Badge variant="secondary">{{ instrument.exchange }}</Badge>
        <Badge v-if="instrument.segment" variant="outline">{{
          instrument.segment
        }}</Badge>
        <Badge v-if="instrument.instrumentType" variant="outline">
          {{ instrument.instrumentType }}
        </Badge>
      </div>
      <p class="truncate text-sm text-muted-foreground">
        {{ instrument.name ?? 'Unnamed instrument' }}
      </p>
      <DataFreshness :updated-at="updatedAt" />
    </div>

    <div class="text-left sm:text-right">
      <Skeleton v-if="loading && !quote" class="ml-auto h-9 w-40" />
      <template v-else>
        <p class="text-3xl font-semibold tracking-tight tabular-nums">
          {{ formatInr(quote?.lastPrice ?? null) }}
        </p>
        <p
          class="mt-1 flex items-center gap-2 text-sm tabular-nums sm:justify-end"
        >
          <span :class="movementClass(change)">{{ formatSigned(change) }}</span>
          <span :class="movementClass(change)">{{
            formatPercent(changePercent)
          }}</span>
        </p>
      </template>
    </div>
  </div>
</template>
