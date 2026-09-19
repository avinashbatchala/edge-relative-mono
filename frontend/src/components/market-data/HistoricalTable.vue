<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { BrokerCandle } from '@/api/types'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { formatCompact, formatIstDateTime, formatPrice } from '@/lib/format'

const props = defineProps<{ candles: BrokerCandle[] }>()

const PAGE_SIZE = 50
const page = ref(1)

const hasOpenInterest = computed(() =>
  props.candles.some((candle) => candle.openInterest !== null),
)

const totalPages = computed(() =>
  Math.max(1, Math.ceil(props.candles.length / PAGE_SIZE)),
)

const pageRows = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return props.candles.slice(start, start + PAGE_SIZE)
})

watch(
  () => props.candles,
  () => {
    page.value = 1
  },
)

function goTo(next: number) {
  page.value = Math.min(Math.max(1, next), totalPages.value)
}
</script>

<template>
  <div class="space-y-2">
    <ScrollArea class="h-[420px] rounded-md border">
      <Table>
        <TableHeader class="sticky top-0 z-10 bg-background">
          <TableRow>
            <TableHead>Timestamp (IST)</TableHead>
            <TableHead class="text-right">Open</TableHead>
            <TableHead class="text-right">High</TableHead>
            <TableHead class="text-right">Low</TableHead>
            <TableHead class="text-right">Close</TableHead>
            <TableHead class="text-right">Volume</TableHead>
            <TableHead v-if="hasOpenInterest" class="text-right">OI</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="candle in pageRows" :key="candle.openTime">
            <TableCell class="whitespace-nowrap tabular-nums">
              {{ formatIstDateTime(candle.openTime) }}
            </TableCell>
            <TableCell class="text-right tabular-nums">{{
              formatPrice(candle.open)
            }}</TableCell>
            <TableCell class="text-right tabular-nums">{{
              formatPrice(candle.high)
            }}</TableCell>
            <TableCell class="text-right tabular-nums">{{
              formatPrice(candle.low)
            }}</TableCell>
            <TableCell class="text-right tabular-nums">{{
              formatPrice(candle.close)
            }}</TableCell>
            <TableCell class="text-right tabular-nums">{{
              formatCompact(candle.volume)
            }}</TableCell>
            <TableCell v-if="hasOpenInterest" class="text-right tabular-nums">
              {{
                candle.openInterest === null
                  ? '—'
                  : formatCompact(candle.openInterest)
              }}
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </ScrollArea>

    <div
      class="flex items-center justify-between text-xs text-muted-foreground"
    >
      <span class="tabular-nums">
        {{ candles.length }} candles · page {{ page }} / {{ totalPages }}
      </span>
      <div class="flex gap-2">
        <Button
          variant="outline"
          size="sm"
          :disabled="page <= 1"
          @click="goTo(page - 1)"
        >
          Previous
        </Button>
        <Button
          variant="outline"
          size="sm"
          :disabled="page >= totalPages"
          @click="goTo(page + 1)"
        >
          Next
        </Button>
      </div>
    </div>
  </div>
</template>
