<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Inbox } from '@lucide/vue'
import { listContracts, listExpiries, marketDataKeys } from '@/api/market-data'
import type { BrokerExchange } from '@/api/types'
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
import { formatIstDate } from '@/lib/format'

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

const contractsQuery = useQuery(() => ({
  queryKey: marketDataKeys.contracts(
    props.exchange,
    props.underlying,
    selectedExpiry.value,
  ),
  queryFn: ({ signal }) =>
    listContracts(
      props.exchange,
      props.underlying,
      selectedExpiry.value,
      signal,
    ),
  enabled: selectedExpiry.value.length > 0,
  staleTime: 30 * 60 * 1000,
  retry: 1,
}))

const futures = computed(() =>
  (contractsQuery.data.value ?? []).filter((contract) =>
    contract.brokerSymbol.toUpperCase().includes('FUT'),
  ),
)
</script>

<template>
  <Card>
    <CardContent class="space-y-4 pt-6">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p class="text-sm font-medium">Futures</p>
          <p class="text-xs text-muted-foreground">
            Contracts listed for {{ underlying }}.
          </p>
        </div>
        <Select
          v-if="(expiriesQuery.data.value ?? []).length > 0"
          v-model="selectedExpiry"
        >
          <SelectTrigger class="w-[200px]" aria-label="Futures expiry">
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
        title="Futures unavailable"
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
          No derivative contracts are listed for this underlying.
        </p>
      </div>

      <SectionState
        v-else-if="contractsQuery.isError.value"
        title="Futures unavailable"
        :error="contractsQuery.error.value"
        @retry="contractsQuery.refetch()"
      />

      <div v-else-if="contractsQuery.isPending.value" class="space-y-2">
        <Skeleton v-for="n in 4" :key="n" class="h-9 w-full" />
      </div>

      <p
        v-else-if="futures.length === 0"
        class="py-12 text-center text-sm text-muted-foreground"
      >
        No futures for the selected expiry.
      </p>

      <Table v-else>
        <TableHeader>
          <TableRow>
            <TableHead>Contract</TableHead>
            <TableHead>Type</TableHead>
            <TableHead class="text-right">Expiry</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="contract in futures" :key="contract.brokerSymbol">
            <TableCell class="font-mono text-xs">{{
              contract.brokerSymbol
            }}</TableCell>
            <TableCell>FUT</TableCell>
            <TableCell class="text-right tabular-nums">{{
              formatIstDate(selectedExpiry)
            }}</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </CardContent>
  </Card>
</template>
