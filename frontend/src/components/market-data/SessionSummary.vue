<script setup lang="ts">
import { computed } from 'vue'
import type { BrokerQuote } from '@/api/types'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCompact, formatPrice, formatQuantity } from '@/lib/format'

const props = withDefaults(
  defineProps<{ quote: BrokerQuote | null; loading?: boolean }>(),
  { loading: false },
)

const metrics = computed(() => {
  const quote = props.quote
  const previousClose =
    quote && quote.lastPrice !== null && quote.dayChange !== null
      ? quote.lastPrice - quote.dayChange
      : null
  return [
    { label: 'Open', value: formatPrice(quote?.ohlc?.open ?? null) },
    { label: 'High', value: formatPrice(quote?.ohlc?.high ?? null) },
    { label: 'Low', value: formatPrice(quote?.ohlc?.low ?? null) },
    { label: 'Prev close', value: formatPrice(previousClose) },
    { label: 'Volume', value: quote ? formatCompact(quote.volume) : '—' },
    {
      label: 'Open interest',
      value:
        quote?.openInterest === null || quote?.openInterest === undefined
          ? '—'
          : formatQuantity(quote.openInterest),
    },
  ]
})
</script>

<template>
  <Card class="flex h-full flex-col">
    <CardHeader class="pb-2">
      <CardDescription>Session</CardDescription>
      <CardTitle class="text-sm font-medium">
        <Skeleton v-if="loading && !quote" class="h-4 w-24" />
        <template v-else>Today</template>
      </CardTitle>
    </CardHeader>
    <CardContent class="flex-1">
      <Skeleton v-if="loading && !quote" class="h-24 w-full" />
      <dl v-else class="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
        <div v-for="metric in metrics" :key="metric.label" class="space-y-0.5">
          <dt class="text-xs text-muted-foreground">{{ metric.label }}</dt>
          <dd class="tabular-nums">{{ metric.value }}</dd>
        </div>
      </dl>
    </CardContent>
  </Card>
</template>
