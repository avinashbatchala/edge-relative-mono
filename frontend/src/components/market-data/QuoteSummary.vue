<script setup lang="ts">
import { computed } from 'vue'
import type { BrokerQuote } from '@/api/types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import {
  computeSpread,
  formatCompact,
  formatInr,
  formatPrice,
  formatQuantity,
} from '@/lib/format'

const props = defineProps<{ quote: BrokerQuote }>()

interface Metric {
  label: string
  value: string
  hint?: string
}

const session = computed<Metric[]>(() => {
  const quote = props.quote
  const ohlc = quote.ohlc
  const previousClose =
    quote.lastPrice !== null && quote.dayChange !== null
      ? quote.lastPrice - quote.dayChange
      : null
  return [
    { label: 'Open', value: formatPrice(ohlc?.open ?? null) },
    { label: 'High', value: formatPrice(ohlc?.high ?? null) },
    { label: 'Low', value: formatPrice(ohlc?.low ?? null) },
    {
      label: 'Prev Close',
      value: formatPrice(previousClose),
      hint: 'Derived from last price and day change',
    },
    { label: 'Volume', value: formatCompact(quote.volume) },
    {
      label: 'Open Interest',
      value:
        quote.openInterest === null ? '—' : formatQuantity(quote.openInterest),
    },
  ]
})

const range = computed<Metric[]>(() => {
  const quote = props.quote
  return [
    { label: 'Upper Circuit', value: formatPrice(quote.upperCircuitLimit) },
    { label: 'Lower Circuit', value: formatPrice(quote.lowerCircuitLimit) },
    { label: '52W High', value: formatPrice(quote.week52High) },
    { label: '52W Low', value: formatPrice(quote.week52Low) },
    {
      label: 'Avg Price',
      value: formatPrice(quote.averagePrice),
    },
    {
      label: 'Last Qty',
      value: formatQuantity(quote.lastTradeQuantity),
    },
  ]
})

const spread = computed(() =>
  computeSpread(props.quote.bidPrice, props.quote.offerPrice),
)
</script>

<template>
  <Card>
    <CardHeader class="pb-3">
      <CardTitle class="text-sm font-medium">Session</CardTitle>
    </CardHeader>
    <CardContent class="space-y-5">
      <dl class="grid grid-cols-2 gap-x-6 gap-y-3">
        <div v-for="metric in session" :key="metric.label" class="space-y-0.5">
          <dt class="text-xs text-muted-foreground">
            <Tooltip v-if="metric.hint">
              <TooltipTrigger
                class="cursor-help underline decoration-dotted underline-offset-2"
              >
                {{ metric.label }}
              </TooltipTrigger>
              <TooltipContent>{{ metric.hint }}</TooltipContent>
            </Tooltip>
            <template v-else>{{ metric.label }}</template>
          </dt>
          <dd class="text-sm tabular-nums">{{ metric.value }}</dd>
        </div>
      </dl>

      <Separator />

      <dl class="grid grid-cols-2 gap-x-6 gap-y-3">
        <div v-for="metric in range" :key="metric.label" class="space-y-0.5">
          <dt class="text-xs text-muted-foreground">{{ metric.label }}</dt>
          <dd class="text-sm tabular-nums">{{ metric.value }}</dd>
        </div>
      </dl>

      <Separator />

      <div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <p class="text-xs text-muted-foreground">Bid</p>
            <p class="text-sm tabular-nums">{{ formatInr(quote.bidPrice) }}</p>
            <p class="text-xs text-muted-foreground tabular-nums">
              {{ formatQuantity(quote.bidQuantity) }} qty
            </p>
          </div>
          <div class="text-right">
            <p class="text-xs text-muted-foreground">Ask</p>
            <p class="text-sm tabular-nums">
              {{ formatInr(quote.offerPrice) }}
            </p>
            <p class="text-xs text-muted-foreground tabular-nums">
              {{ formatQuantity(quote.offerQuantity) }} qty
            </p>
          </div>
        </div>
        <p
          v-if="spread"
          class="mt-2 text-center text-xs text-muted-foreground tabular-nums"
        >
          Spread {{ formatPrice(spread.absolute) }} ·
          {{ formatPrice(spread.bps, 1) }} bps
        </p>
      </div>
    </CardContent>
  </Card>
</template>
