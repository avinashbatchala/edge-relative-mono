<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Inbox } from '@lucide/vue'
import { getOptionChain, listExpiries, marketDataKeys } from '@/api/market-data'
import type { BrokerExchange, BrokerOptionChainEntry } from '@/api/types'
import { Card, CardContent } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import SectionState from './SectionState.vue'
import { formatCompact, formatIstDate, formatPrice } from '@/lib/format'

const props = defineProps<{
  exchange: BrokerExchange
  underlying: string
}>()

const expiriesQuery = useQuery(() => ({
  queryKey: marketDataKeys.expiries(props.exchange, props.underlying),
  queryFn: ({ signal }) =>
    listExpiries(props.exchange, props.underlying, signal),
  staleTime: 30 * 60 * 1000,
  retry: 1,
}))

const selectedExpiry = ref('')

watch(
  () => expiriesQuery.data.value,
  (list) => {
    const dates = (list ?? []).map((expiry) => expiry.expiryDate)
    if (!dates.includes(selectedExpiry.value) && dates.length > 0) {
      selectedExpiry.value = dates[0] ?? ''
    }
  },
  { immediate: true },
)

const optionChainQuery = useQuery(() => ({
  queryKey: marketDataKeys.optionChain(
    props.exchange,
    props.underlying,
    selectedExpiry.value,
  ),
  queryFn: ({ signal }) =>
    getOptionChain(
      props.exchange,
      props.underlying,
      selectedExpiry.value,
      signal,
    ),
  enabled: selectedExpiry.value.length > 0,
  staleTime: 30 * 1000,
  retry: 1,
}))

const strikes = computed(() => optionChainQuery.data.value?.strikes ?? [])

function ltp(entry: BrokerOptionChainEntry | null): string {
  return entry ? formatPrice(entry.lastPrice) : '—'
}

function oi(entry: BrokerOptionChainEntry | null): string {
  return entry ? formatCompact(entry.openInterest) : '—'
}

function volume(entry: BrokerOptionChainEntry | null): string {
  return entry ? formatCompact(entry.volume) : '—'
}
</script>

<template>
  <Card>
    <CardContent class="space-y-4 pt-6">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p class="text-sm font-medium">Options</p>
          <p class="text-xs text-muted-foreground">
            Option chain for {{ underlying }}.
          </p>
        </div>
        <Select
          v-if="(expiriesQuery.data.value ?? []).length > 0"
          v-model="selectedExpiry"
        >
          <SelectTrigger class="w-[200px]" aria-label="Options expiry">
            <SelectValue placeholder="Expiry" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem
              v-for="expiry in expiriesQuery.data.value ?? []"
              :key="expiry.expiryDate"
              :value="expiry.expiryDate"
            >
              {{ formatIstDate(expiry.expiryDate) }}
            </SelectItem>
          </SelectContent>
        </Select>
      </div>

      <SectionState
        v-if="expiriesQuery.isError.value"
        title="Option chain unavailable"
        :error="expiriesQuery.error.value"
        @retry="expiriesQuery.refetch()"
      />

      <div v-else-if="expiriesQuery.isPending.value" class="space-y-2">
        <Skeleton v-for="n in 4" :key="n" class="h-9 w-full" />
      </div>

      <div
        v-else-if="(expiriesQuery.data.value ?? []).length === 0"
        class="flex flex-col items-center gap-2 py-12 text-center"
      >
        <Inbox class="size-5 text-muted-foreground" aria-hidden="true" />
        <p class="text-sm text-muted-foreground">
          No options are listed for this underlying.
        </p>
      </div>

      <SectionState
        v-else-if="optionChainQuery.isError.value"
        title="Option chain unavailable"
        :error="optionChainQuery.error.value"
        @retry="optionChainQuery.refetch()"
      />

      <div v-else-if="optionChainQuery.isPending.value" class="space-y-2">
        <Skeleton v-for="n in 6" :key="n" class="h-9 w-full" />
      </div>

      <p
        v-else-if="strikes.length === 0"
        class="py-12 text-center text-sm text-muted-foreground"
      >
        No option strikes for the selected expiry.
      </p>

      <Table v-else>
        <TableHeader>
          <TableRow>
            <TableHead colspan="3" class="text-center">Call</TableHead>
            <TableHead class="text-center">Strike</TableHead>
            <TableHead colspan="3" class="text-center">Put</TableHead>
          </TableRow>
          <TableRow>
            <TableHead class="text-right">LTP</TableHead>
            <TableHead class="text-right">OI</TableHead>
            <TableHead class="text-right">Vol</TableHead>
            <TableHead class="text-center">Price</TableHead>
            <TableHead class="text-right">LTP</TableHead>
            <TableHead class="text-right">OI</TableHead>
            <TableHead class="text-right">Vol</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="strike in strikes" :key="strike.strikePrice">
            <TableCell class="text-right tabular-nums">{{
              ltp(strike.call)
            }}</TableCell>
            <TableCell class="text-right tabular-nums">{{
              oi(strike.call)
            }}</TableCell>
            <TableCell class="text-right tabular-nums">{{
              volume(strike.call)
            }}</TableCell>
            <TableCell class="text-center font-medium tabular-nums">{{
              formatPrice(strike.strikePrice)
            }}</TableCell>
            <TableCell class="text-right tabular-nums">{{
              ltp(strike.put)
            }}</TableCell>
            <TableCell class="text-right tabular-nums">{{
              oi(strike.put)
            }}</TableCell>
            <TableCell class="text-right tabular-nums">{{
              volume(strike.put)
            }}</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </CardContent>
  </Card>
</template>
