<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import {
  getEffectiveStrategyBinding,
  listStrategyBindings,
  strategyBindingKeys,
} from '@/api/strategy-bindings'
import { getWatchlist, watchlistKeys } from '@/api/watchlist'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import EmptyState from '@/components/common/EmptyState.vue'

const selected = ref<number | null>(null)
const today = new Date().toISOString().slice(0, 10)

const watchlist = useQuery({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
})
const entries = computed(() => watchlist.data.value?.entries ?? [])

const bindings = useQuery(() => ({
  queryKey: strategyBindingKeys.forInstrument(selected.value ?? 0),
  queryFn: ({ signal }) => listStrategyBindings(selected.value ?? 0, signal),
  enabled: selected.value != null,
}))
const effective = useQuery(() => ({
  queryKey: [
    ...strategyBindingKeys.forInstrument(selected.value ?? 0),
    'effective',
    today,
  ],
  queryFn: ({ signal }) =>
    getEffectiveStrategyBinding(selected.value ?? 0, today, signal),
  enabled: selected.value != null,
}))
</script>

<template>
  <div class="flex flex-col gap-4 p-4 lg:p-6">
    <header class="space-y-1">
      <h1 class="text-xl font-semibold tracking-tight">Configuration</h1>
      <p class="text-sm text-muted-foreground">
        Per-instrument strategy parameter bindings (effective-dated,
        append-only). These change parameters only; risk and setup gates remain
        authoritative.
      </p>
    </header>

    <Card class="gap-0 py-0">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-base">Strategy bindings</CardTitle>
        <CardDescription
          >Select an instrument to inspect its effective
          binding.</CardDescription
        >
      </CardHeader>
      <CardContent class="space-y-4 border-t p-5">
        <label class="flex items-center gap-2 text-sm">
          <span class="text-muted-foreground">Instrument</span>
          <select
            v-model.number="selected"
            class="rounded-md border bg-transparent px-2 py-1"
            aria-label="Select instrument"
          >
            <option :value="null" disabled>Select…</option>
            <option
              v-for="entry in entries"
              :key="entry.instrumentId"
              :value="entry.instrumentId"
            >
              {{ entry.symbol }}
            </option>
          </select>
        </label>

        <div v-if="effective.data.value" class="rounded-md border p-3 text-xs">
          <p class="font-medium">Effective today</p>
          <p class="text-muted-foreground">
            {{ effective.data.value.lifecycleState }} · v{{
              effective.data.value.strategyVersionId
            }}
            · from {{ effective.data.value.effectiveFrom }}
          </p>
        </div>
        <p v-else-if="selected != null" class="text-xs text-muted-foreground">
          No binding is effective today — the global parameters apply.
        </p>

        <Table v-if="bindings.data.value?.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead>Version</TableHead>
              <TableHead>State</TableHead>
              <TableHead>From</TableHead>
              <TableHead>To</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="(binding, index) in bindings.data.value"
              :key="index"
            >
              <TableCell class="text-xs"
                >v{{ binding.strategyVersionId }}</TableCell
              >
              <TableCell class="text-xs">
                <Badge variant="outline">{{ binding.lifecycleState }}</Badge>
              </TableCell>
              <TableCell class="text-xs">{{ binding.effectiveFrom }}</TableCell>
              <TableCell class="text-xs">{{
                binding.effectiveTo ?? 'open'
              }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
        <EmptyState
          v-else-if="selected != null && !bindings.isPending.value"
          title="No bindings"
          description="This instrument uses the global parameters; no per-instrument binding exists."
        />
      </CardContent>
    </Card>
  </div>
</template>
