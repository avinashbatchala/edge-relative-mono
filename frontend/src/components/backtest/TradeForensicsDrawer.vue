<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import {
  backtestKeys,
  getBacktestTimeline,
  type BacktestTimelinePoint,
  type BacktestTradeRow,
} from '@/api/backtests'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { formatIstDateTime, formatPrice } from '@/lib/format'

const props = defineProps<{
  runKey: string
  trade: BacktestTradeRow | null
}>()
const open = defineModel<boolean>('open', { default: false })

const timelineQuery = useQuery(() => ({
  queryKey: backtestKeys.timeline(props.runKey, props.trade?.instrumentId ?? 0),
  queryFn: ({ signal }) =>
    getBacktestTimeline(props.runKey, props.trade?.instrumentId ?? 0, signal),
  enabled: open.value && !!props.trade,
}))

/** Nearest anchor at or before entry, so the panel shows the state the decision saw. */
const anchor = computed<BacktestTimelinePoint | null>(() => {
  const points = timelineQuery.data.value?.points ?? []
  const trade = props.trade
  if (!trade || points.length === 0) {
    return null
  }
  const entry = Date.parse(trade.entryAt)
  let candidate: BacktestTimelinePoint | null = null
  for (const point of points) {
    if (Date.parse(point.at) <= entry) {
      candidate = point
    } else {
      break
    }
  }
  return candidate ?? points[0] ?? null
})

const setupState = computed(() => {
  if (!props.trade) {
    return null
  }
  return props.trade.direction === 'LONG'
    ? (anchor.value?.longState ?? null)
    : (anchor.value?.shortState ?? null)
})

const reasons = computed(() => {
  if (!props.trade) {
    return []
  }
  return props.trade.direction === 'LONG'
    ? (anchor.value?.longReasons ?? [])
    : (anchor.value?.shortReasons ?? [])
})

function value(input: number | null | undefined): string {
  return input === null || input === undefined ? '—' : input.toFixed(3)
}
</script>

<template>
  <Sheet v-model:open="open">
    <SheetContent side="right" class="w-full sm:max-w-md">
      <SheetHeader>
        <SheetTitle>Trade forensics — {{ trade?.symbol ?? '' }}</SheetTitle>
        <SheetDescription>
          Exact run inputs at the entry anchor, plus realized execution.
        </SheetDescription>
      </SheetHeader>
      <div
        v-if="trade"
        class="flex-1 space-y-4 overflow-y-auto px-4 pb-6 text-sm"
      >
        <section>
          <h3 class="mb-1 font-medium">Execution</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
            <dt class="text-muted-foreground">Direction</dt>
            <dd>{{ trade.direction }}</dd>
            <dt class="text-muted-foreground">Pattern</dt>
            <dd>{{ trade.entryPattern ?? '—' }}</dd>
            <dt class="text-muted-foreground">Entry</dt>
            <dd>
              {{ formatIstDateTime(trade.entryAt) }} @
              {{ formatPrice(trade.entryPrice) }}
            </dd>
            <dt class="text-muted-foreground">Exit</dt>
            <dd>
              {{
                trade.exitAt
                  ? `${formatIstDateTime(trade.exitAt)} @ ${formatPrice(trade.exitPrice ?? 0)}`
                  : 'open'
              }}
            </dd>
            <dt class="text-muted-foreground">Exit reason</dt>
            <dd>{{ trade.exitReason ?? '—' }}</dd>
            <dt class="text-muted-foreground">Quantity</dt>
            <dd>{{ trade.quantity }}</dd>
            <dt class="text-muted-foreground">Gross P&L</dt>
            <dd>{{ formatPrice(trade.grossPnl) }}</dd>
            <dt class="text-muted-foreground">Costs</dt>
            <dd>{{ formatPrice(trade.explicitCosts) }}</dd>
            <dt class="text-muted-foreground">Net P&L</dt>
            <dd>{{ formatPrice(trade.netPnl) }}</dd>
            <dt class="text-muted-foreground">Realized R</dt>
            <dd>{{ value(trade.realizedR) }}</dd>
            <dt class="text-muted-foreground">Ambiguous bars</dt>
            <dd>{{ trade.ambiguousBars }}</dd>
          </dl>
        </section>

        <section v-if="anchor">
          <h3 class="mb-1 font-medium">Market / sector</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
            <dt class="text-muted-foreground">Market structure</dt>
            <dd>{{ anchor.marketStructure ?? '—' }}</dd>
            <dt class="text-muted-foreground">Market efficiency</dt>
            <dd>{{ value(anchor.marketEfficiency) }}</dd>
            <dt class="text-muted-foreground">Sector RRS</dt>
            <dd>{{ value(anchor.sectorRrs) }}</dd>
            <dt class="text-muted-foreground">Sector structure</dt>
            <dd>{{ anchor.sectorStructure ?? '—' }}</dd>
          </dl>
        </section>

        <section v-if="anchor">
          <h3 class="mb-1 font-medium">Stock features</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
            <dt class="text-muted-foreground">RRS</dt>
            <dd>{{ value(anchor.rrsRaw) }}</dd>
            <dt class="text-muted-foreground">RRS fast</dt>
            <dd>{{ value(anchor.rrsFast) }}</dd>
            <dt class="text-muted-foreground">RRS slow</dt>
            <dd>{{ value(anchor.rrsSlow) }}</dd>
            <dt class="text-muted-foreground">Persistence</dt>
            <dd>{{ value(anchor.rrsPersistence) }}</dd>
            <dt class="text-muted-foreground">Slope</dt>
            <dd>{{ value(anchor.rrsSlope) }}</dd>
            <dt class="text-muted-foreground">Acceleration</dt>
            <dd>{{ value(anchor.rrsAcceleration) }}</dd>
            <dt class="text-muted-foreground">Percentile</dt>
            <dd>{{ value(anchor.rrsPercentile) }}</dd>
            <dt class="text-muted-foreground">RVOL daily</dt>
            <dd>{{ value(anchor.rvolDaily) }}</dd>
            <dt class="text-muted-foreground">RVOL interval</dt>
            <dd>{{ value(anchor.rvolInterval) }}</dd>
            <dt class="text-muted-foreground">RVOL cumulative</dt>
            <dd>{{ value(anchor.rvolCumulative) }}</dd>
            <dt class="text-muted-foreground">RVE</dt>
            <dd>{{ value(anchor.rve) }}</dd>
            <dt class="text-muted-foreground">ATR</dt>
            <dd>{{ value(anchor.atr) }}</dd>
          </dl>
        </section>

        <section>
          <h3 class="mb-1 font-medium">Setup</h3>
          <p class="text-xs">
            State at entry:
            <span class="font-medium">{{ setupState ?? '—' }}</span>
          </p>
          <ul class="mt-1 list-disc pl-4 text-xs text-muted-foreground">
            <li v-for="reason in reasons" :key="reason">{{ reason }}</li>
            <li v-if="reasons.length === 0">
              No failing reason codes recorded.
            </li>
          </ul>
        </section>
      </div>
    </SheetContent>
  </Sheet>
</template>
