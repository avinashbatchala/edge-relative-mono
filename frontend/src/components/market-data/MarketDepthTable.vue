<script setup lang="ts">
import { computed } from 'vue'
import type { BrokerDepthLevel } from '@/api/types'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { formatPrice, formatQuantity } from '@/lib/format'

const props = defineProps<{
  bids: BrokerDepthLevel[]
  asks: BrokerDepthLevel[]
}>()

const rows = computed(() => {
  const count = Math.max(props.bids.length, props.asks.length)
  return Array.from({ length: count }, (_, index) => ({
    bid: props.bids[index] ?? null,
    ask: props.asks[index] ?? null,
  }))
})
</script>

<template>
  <Table>
    <TableHeader>
      <TableRow>
        <TableHead class="text-right">Bid Qty</TableHead>
        <TableHead class="text-right">Bid</TableHead>
        <TableHead class="text-right">Ask</TableHead>
        <TableHead class="text-right">Ask Qty</TableHead>
      </TableRow>
    </TableHeader>
    <TableBody>
      <TableRow v-for="(row, index) in rows" :key="index">
        <TableCell class="text-right tabular-nums">
          {{ row.bid ? formatQuantity(row.bid.quantity) : '—' }}
        </TableCell>
        <TableCell class="text-right tabular-nums text-[var(--positive)]">
          {{ row.bid ? formatPrice(row.bid.price) : '—' }}
        </TableCell>
        <TableCell class="text-right tabular-nums text-[var(--negative)]">
          {{ row.ask ? formatPrice(row.ask.price) : '—' }}
        </TableCell>
        <TableCell class="text-right tabular-nums">
          {{ row.ask ? formatQuantity(row.ask.quantity) : '—' }}
        </TableCell>
      </TableRow>
    </TableBody>
  </Table>
</template>
