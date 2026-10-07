<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { listBindings, listModels, mlKeys, setModelLifecycle } from '@/api/ml'
import { getWatchlist, watchlistKeys } from '@/api/watchlist'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
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

const queryClient = useQueryClient()
const selected = ref<number | null>(null)

const modelsQuery = useQuery({
  queryKey: mlKeys.models(),
  queryFn: ({ signal }) => listModels(signal),
  refetchInterval: 10000,
})

const watchlist = useQuery({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
  staleTime: 60_000,
})
const entries = computed(() => watchlist.data.value?.entries ?? [])

const bindingsQuery = useQuery({
  queryKey: mlKeys.bindings(selected.value ?? 0),
  queryFn: ({ signal }) => listBindings(selected.value as number, signal),
  enabled: selected.value != null,
})

const lifecycleMutation = useMutation({
  mutationFn: (args: { id: number; state: string }) =>
    setModelLifecycle(args.id, args.state),
  onSuccess: () => queryClient.invalidateQueries({ queryKey: mlKeys.models() }),
})

function oosIc(metrics: Record<string, unknown>): string {
  const oos = metrics.outOfSample as { rankIc?: number } | undefined
  return oos?.rankIc == null ? '—' : oos.rankIc.toFixed(3)
}
function lifecycleTone(state: string): string {
  if (state === 'PRODUCTION' || state === 'VALIDATED') return 'text-positive'
  if (state === 'RETIRED') return 'text-negative'
  return 'text-muted-foreground'
}
</script>

<template>
  <div class="flex flex-col gap-4 p-4 lg:p-6">
    <header class="space-y-1">
      <h1 class="text-xl font-semibold tracking-tight">
        Models &amp; bindings
      </h1>
      <p class="max-w-3xl text-sm text-muted-foreground">
        Trained model versions and the per-instrument bindings that wire a
        survivor to a symbol. Bindings are effective-dated and append-only.
      </p>
    </header>

    <Card class="gap-0 overflow-hidden py-0">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-base">Model registry</CardTitle>
        <CardDescription
          >Immutable versions with lifecycle and out-of-sample
          quality.</CardDescription
        >
      </CardHeader>
      <div class="overflow-x-auto border-t">
        <Table class="min-w-[820px]">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead>Model</TableHead>
              <TableHead>Version</TableHead>
              <TableHead>Lifecycle</TableHead>
              <TableHead class="text-right">OOS IC</TableHead>
              <TableHead>Artifact</TableHead>
              <TableHead class="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="model in modelsQuery.data.value ?? []"
              :key="model.modelVersionId"
            >
              <TableCell class="text-xs">
                {{ model.modelCode }}
                <span class="block text-[10px] text-muted-foreground">{{
                  model.algorithm
                }}</span>
              </TableCell>
              <TableCell class="text-xs tabular-nums"
                >v{{ model.version }}</TableCell
              >
              <TableCell class="text-xs">
                <Badge
                  variant="outline"
                  :class="lifecycleTone(model.lifecycleState)"
                >
                  {{ model.lifecycleState }}
                </Badge>
              </TableCell>
              <TableCell class="text-right text-xs tabular-nums">{{
                oosIc(model.metrics)
              }}</TableCell>
              <TableCell
                class="max-w-[220px] truncate font-mono text-[10px] text-muted-foreground"
              >
                {{ model.artifactChecksum?.slice(0, 12) ?? '—' }}
              </TableCell>
              <TableCell class="text-right">
                <Button
                  variant="ghost"
                  size="sm"
                  @click="
                    lifecycleMutation.mutate({
                      id: model.modelVersionId,
                      state: 'RETIRED',
                    })
                  "
                >
                  Retire
                </Button>
              </TableCell>
            </TableRow>
            <TableRow v-if="(modelsQuery.data.value ?? []).length === 0">
              <TableCell
                colspan="6"
                class="py-8 text-center text-sm text-muted-foreground"
              >
                No models registered yet. Start an analysis in the ML Lab.
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </Card>

    <Card class="gap-0 py-0">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-base">Per-instrument bindings</CardTitle>
        <CardDescription
          >Select an instrument to inspect its effective-dated model
          bindings.</CardDescription
        >
      </CardHeader>
      <CardContent class="space-y-3 border-t p-5">
        <label class="flex items-center gap-2 text-sm">
          <span class="text-muted-foreground">Instrument</span>
          <select
            v-model.number="selected"
            aria-label="Binding instrument"
            class="rounded-md border bg-transparent px-2 py-1"
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

        <Table v-if="bindingsQuery.data.value?.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead>Model version</TableHead>
              <TableHead>Authority</TableHead>
              <TableHead>State</TableHead>
              <TableHead>From</TableHead>
              <TableHead>To</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="(binding, index) in bindingsQuery.data.value"
              :key="index"
            >
              <TableCell class="text-xs tabular-nums">{{
                binding.modelVersionId
              }}</TableCell>
              <TableCell class="text-xs">{{
                binding.authorityLevel
              }}</TableCell>
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
          v-else-if="selected != null && !bindingsQuery.isPending.value"
          title="No bindings"
          description="This instrument has no ML model binding; deterministic ranking applies."
        />
      </CardContent>
    </Card>
  </div>
</template>
