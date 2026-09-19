<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronDown, ChevronUp, X } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { TableCell, TableRow } from '@/components/ui/table'
import { useQuote } from '@/composables/useQuote'
import type { WatchlistEntry } from '@/api/watchlist'
import {
  formatAge,
  formatCompact,
  formatInr,
  formatPercent,
  formatPrice,
  formatSigned,
  movementClass,
} from '@/lib/format'

const props = defineProps<{
  entry: WatchlistEntry
  first: boolean
  last: boolean
}>()

const emit = defineEmits<{
  remove: [number]
  move: [number, -1 | 1]
}>()

const router = useRouter()

const subject = computed(() => ({
  exchange: props.entry.exchange,
  segment: props.entry.segment,
  symbol: props.entry.symbol,
}))

const { data, isError, isPending, dataUpdatedAt } = useQuote(subject)

const quote = computed(() => data.value ?? null)
const previousClose = computed(() => {
  const value = quote.value
  if (!value || value.lastPrice === null || value.dayChange === null) {
    return null
  }
  return value.lastPrice - value.dayChange
})
const status = computed(() => {
  if (isError.value) {
    return { label: 'Unavailable', variant: 'destructive' as const }
  }
  if (isPending.value) {
    return { label: 'Loading', variant: 'outline' as const }
  }
  return { label: 'Live', variant: 'secondary' as const }
})
const updatedAt = computed(() =>
  dataUpdatedAt.value ? new Date(dataUpdatedAt.value).toISOString() : null,
)

function openDetail() {
  router.push({ name: 'market-ticker', params: { symbol: props.entry.symbol } })
}

function onRowKey(event: KeyboardEvent) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    openDetail()
  }
}

function stop(event: Event) {
  event.stopPropagation()
}
</script>

<template>
  <TableRow
    class="cursor-pointer"
    tabindex="0"
    :aria-label="`Open ${entry.symbol} details`"
    @click="openDetail"
    @keydown="onRowKey"
  >
    <TableCell class="max-w-[220px]">
      <div class="flex flex-col">
        <span class="truncate font-medium">{{ entry.symbol }}</span>
        <span class="truncate text-xs text-muted-foreground">
          {{ entry.name ?? '—' }}
        </span>
      </div>
    </TableCell>
    <TableCell class="whitespace-nowrap text-xs text-muted-foreground">
      {{ entry.exchange }} · {{ entry.segment ?? entry.instrumentType }}
    </TableCell>
    <TableCell class="text-right tabular-nums">
      {{ formatInr(quote?.lastPrice ?? null) }}
    </TableCell>
    <TableCell
      class="text-right tabular-nums"
      :class="movementClass(quote?.dayChange ?? null)"
    >
      {{ formatSigned(quote?.dayChange ?? null) }}
    </TableCell>
    <TableCell
      class="text-right tabular-nums"
      :class="movementClass(quote?.dayChangePercent ?? null)"
    >
      {{ formatPercent(quote?.dayChangePercent ?? null) }}
    </TableCell>
    <TableCell class="text-right tabular-nums">
      {{ formatPrice(quote?.ohlc?.open ?? null) }}
    </TableCell>
    <TableCell class="text-right tabular-nums">
      {{ formatPrice(quote?.ohlc?.high ?? null) }}
    </TableCell>
    <TableCell class="text-right tabular-nums">
      {{ formatPrice(quote?.ohlc?.low ?? null) }}
    </TableCell>
    <TableCell class="text-right tabular-nums">
      {{ formatPrice(previousClose) }}
    </TableCell>
    <TableCell class="text-right tabular-nums">
      {{ formatCompact(quote?.volume ?? null) }}
    </TableCell>
    <TableCell class="whitespace-nowrap text-right tabular-nums">
      <span class="text-muted-foreground">{{
        formatInr(quote?.bidPrice ?? null)
      }}</span>
      <span class="mx-1 text-muted-foreground">/</span>
      <span>{{ formatInr(quote?.offerPrice ?? null) }}</span>
    </TableCell>
    <TableCell
      class="whitespace-nowrap text-right text-xs text-muted-foreground tabular-nums"
    >
      {{ updatedAt ? formatAge(updatedAt) : '—' }}
    </TableCell>
    <TableCell class="text-right">
      <Badge :variant="status.variant">{{ status.label }}</Badge>
    </TableCell>
    <TableCell class="text-right" @click="stop">
      <div class="flex items-center justify-end gap-1">
        <Button
          variant="ghost"
          size="icon"
          class="size-7"
          :disabled="first"
          :aria-label="`Move ${entry.symbol} up`"
          @click="emit('move', entry.instrumentId, -1)"
        >
          <ChevronUp class="size-3.5" aria-hidden="true" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          class="size-7"
          :disabled="last"
          :aria-label="`Move ${entry.symbol} down`"
          @click="emit('move', entry.instrumentId, 1)"
        >
          <ChevronDown class="size-3.5" aria-hidden="true" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          class="size-7 text-muted-foreground hover:text-destructive"
          :aria-label="`Remove ${entry.symbol}`"
          @click="emit('remove', entry.instrumentId)"
        >
          <X class="size-3.5" aria-hidden="true" />
        </Button>
      </div>
    </TableCell>
  </TableRow>
</template>
