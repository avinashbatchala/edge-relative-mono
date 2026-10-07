<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, RefreshCw } from '@lucide/vue'
import {
  createBinding,
  getAnalysisRun,
  mlKeys,
  verifyMl,
  type MlVerificationReport,
} from '@/api/ml'
import { getWatchlist, watchlistKeys } from '@/api/watchlist'
import { ApiError } from '@/api/http'
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
import { formatPrice } from '@/lib/format'

const route = useRoute()
const router = useRouter()
const runKey = computed(() => String(route.params.runKey))

const runQuery = useQuery({
  queryKey: mlKeys.run(runKey.value),
  queryFn: ({ signal }) => getAnalysisRun(runKey.value, signal),
  refetchInterval: (query) => {
    const status = (query.state.data as { status?: string } | undefined)?.status
    return status === 'SUCCEEDED' ||
      status === 'FAILED' ||
      status === 'CANCELLED'
      ? false
      : 3000
  },
})

const run = computed(() => runQuery.data.value ?? null)
const config = computed(
  () => (run.value?.config ?? {}) as Record<string, unknown>,
)
const symbols = computed(
  () => (config.value.symbols as string[] | undefined) ?? [],
)

const watchlist = useQuery({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
  staleTime: 60_000,
})
const instruments = computed(
  () =>
    watchlist.data.value?.entries
      .filter((entry) => symbols.value.includes(entry.symbol))
      .map((entry) => ({
        symbol: entry.symbol,
        instrumentId: entry.instrumentId,
      })) ?? [],
)

const metrics = computed(
  () => (run.value?.metrics ?? {}) as Record<string, unknown>,
)
const gatesPass = computed(() => metrics.value.gatesPass === true)

function block(key: string): {
  rankIc?: number
  ndcg?: number
  groups?: number
} {
  return (
    (metrics.value[key] as {
      rankIc?: number
      ndcg?: number
      groups?: number
    }) ?? {}
  )
}
function fmt(value: number | undefined, digits = 3): string {
  return value == null ? '—' : value.toFixed(digits)
}

const promote = reactive({ instrumentId: '' as number | '', topK: 3 })
const today = new Date().toISOString().slice(0, 10)

const promoteMutation = useMutation({
  mutationFn: () =>
    createBinding({
      instrumentId: Number(promote.instrumentId),
      modelVersionId: run.value?.modelVersionId as number,
      authorityLevel: 'RANKER',
      effectiveFrom: today,
      lifecycleState: 'VALIDATED',
      source: `ml-run:${runKey.value}`,
    }),
})
const promoteMessage = ref<string | null>(null)
function promoteNow() {
  promoteMessage.value = null
  promoteMutation.mutate(undefined, {
    onSuccess: (result) =>
      (promoteMessage.value = `Bound model to instrument (binding ${result.bindingId}).`),
    onError: (error) =>
      (promoteMessage.value =
        error instanceof ApiError ? error.message : 'Binding failed.'),
  })
}

const verifyMutation = useMutation({
  mutationFn: () =>
    verifyMl({
      symbols: symbols.value,
      setupTimeframe: String(config.value.setupTimeframe ?? 'M5'),
      dailyTimeframe: String(config.value.dailyTimeframe ?? 'D1'),
      startDate: String(config.value.startDate),
      endDate: String(config.value.endDate),
      marketSymbol: String(config.value.marketSymbol ?? 'NIFTY'),
      startingCapital: Number(config.value.startingCapital ?? 1_000_000),
      seed: Number(config.value.seed ?? 7),
      modelVersionId: run.value?.modelVersionId as number,
      rankingTopK: promote.topK,
    }),
})
const report = computed<MlVerificationReport | null>(
  () => verifyMutation.data.value ?? null,
)
</script>

<template>
  <div class="flex flex-col gap-4 p-4 lg:p-6">
    <header class="space-y-1">
      <Button
        variant="ghost"
        size="sm"
        class="-ml-2"
        @click="router.push({ name: 'ml-lab' })"
      >
        <ArrowLeft class="mr-1 size-3.5" /> ML Lab
      </Button>
      <h1 class="flex items-center gap-2 text-xl font-semibold tracking-tight">
        Analysis {{ runKey.slice(0, 8) }}
        <Badge v-if="run" variant="outline">{{ run.status }}</Badge>
      </h1>
      <p class="text-sm text-muted-foreground">
        {{ symbols.join(', ') }} · {{ config.setupTimeframe }} +
        {{ config.dailyTimeframe }} · {{ config.startDate }} –
        {{ config.endDate }}
      </p>
    </header>

    <p v-if="runQuery.isError.value" class="text-sm text-negative">
      Run not found.
    </p>

    <Card v-if="run" class="gap-0 py-0">
      <CardHeader class="flex-row items-center justify-between px-5 py-4">
        <div>
          <CardTitle class="text-base">Out-of-sample ranking</CardTitle>
          <CardDescription>
            Validation winner evaluated on held-out sessions. Gates are
            advisory; promotion is manual.
          </CardDescription>
        </div>
        <Button variant="outline" size="sm" @click="runQuery.refetch()">
          <RefreshCw class="mr-1 size-3.5" aria-hidden="true" /> Refresh
        </Button>
      </CardHeader>
      <CardContent class="border-t p-5">
        <div class="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div>
            <p class="text-xs text-muted-foreground">Validation IC</p>
            <p class="text-lg font-semibold tabular-nums">
              {{ fmt(block('validation').rankIc) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">Validation NDCG</p>
            <p class="text-lg font-semibold tabular-nums">
              {{ fmt(block('validation').ndcg, 2) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">OOS IC</p>
            <p class="text-lg font-semibold tabular-nums">
              {{ fmt(block('outOfSample').rankIc) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">OOS NDCG</p>
            <p class="text-lg font-semibold tabular-nums">
              {{ fmt(block('outOfSample').ndcg, 2) }}
            </p>
          </div>
        </div>
        <div class="mt-4 flex flex-wrap items-center gap-2 text-xs">
          <Badge
            variant="outline"
            :class="
              gatesPass ? 'text-positive' : 'text-amber-600 dark:text-amber-400'
            "
          >
            {{ gatesPass ? 'Gates pass' : 'Gates not met' }}
          </Badge>
          <span class="text-muted-foreground"
            >{{ metrics.rows ?? '—' }} labelled anchors ·
            {{ metrics.features ?? '—' }} features</span
          >
          <span v-if="run.modelVersionId" class="text-muted-foreground"
            >model version {{ run.modelVersionId }}</span
          >
        </div>
        <p v-if="run.error" class="mt-3 text-sm text-negative" role="alert">
          {{ run.error }}
        </p>
        <p
          v-else-if="run.status === 'QUEUED' || run.status === 'RUNNING'"
          class="mt-3 text-sm text-muted-foreground"
        >
          {{
            run.status === 'QUEUED'
              ? 'Queued — waiting for the research runner.'
              : 'Training…'
          }}
        </p>
      </CardContent>
    </Card>

    <div class="grid gap-4 lg:grid-cols-2">
      <Card class="gap-0 py-0">
        <CardHeader class="px-5 py-4">
          <CardTitle class="text-base">Promote to a symbol</CardTitle>
          <CardDescription>
            Wire the survivor to one instrument's effective-dated model binding.
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-3 border-t p-5">
          <label class="flex items-center gap-2 text-sm">
            <span class="text-muted-foreground">Instrument</span>
            <select
              v-model.number="promote.instrumentId"
              aria-label="Promote instrument"
              class="rounded-md border bg-transparent px-2 py-1"
            >
              <option :value="''" disabled>Select…</option>
              <option
                v-for="instrument in instruments"
                :key="instrument.instrumentId"
                :value="instrument.instrumentId"
              >
                {{ instrument.symbol }}
              </option>
            </select>
          </label>
          <Button
            :disabled="
              !gatesPass || !run?.modelVersionId || promote.instrumentId === ''
            "
            @click="promoteNow"
          >
            Promote
          </Button>
          <p v-if="!gatesPass" class="text-xs text-muted-foreground">
            Promotion is disabled until the out-of-sample gates pass.
          </p>
          <p v-if="promoteMessage" class="text-xs">{{ promoteMessage }}</p>
        </CardContent>
      </Card>

      <Card class="gap-0 py-0">
        <CardHeader class="px-5 py-4">
          <CardTitle class="text-base">Verify with backtest</CardTitle>
          <CardDescription>
            Replay the window with the ML ranking overlay and compare to the
            deterministic baseline.
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-3 border-t p-5">
          <label class="flex items-center gap-2 text-sm">
            <span class="text-muted-foreground">Concurrent-position cap</span>
            <input
              v-model.number="promote.topK"
              type="number"
              min="1"
              aria-label="Ranking top K"
              class="w-24 rounded-md border bg-transparent px-2 py-1"
            />
          </label>
          <Button
            variant="outline"
            :disabled="!run?.modelVersionId || verifyMutation.isPending.value"
            @click="verifyMutation.mutate()"
          >
            Run verification
          </Button>
          <p
            v-if="verifyMutation.error.value"
            class="text-xs text-negative"
            role="alert"
          >
            Verification failed.
          </p>
        </CardContent>
      </Card>
    </div>

    <Card v-if="report" class="gap-0 overflow-hidden py-0">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-base">Baseline vs ML-ranked</CardTitle>
        <CardDescription
          >Cap = {{ report.topK }} concurrent positions.</CardDescription
        >
      </CardHeader>
      <div class="overflow-x-auto border-t">
        <Table>
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead>Book</TableHead>
              <TableHead class="text-right">Completed</TableHead>
              <TableHead class="text-right">Wins</TableHead>
              <TableHead class="text-right">Net P&amp;L</TableHead>
              <TableHead class="text-right">Avg R</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow>
              <TableCell class="text-xs">Deterministic baseline</TableCell>
              <TableCell class="text-right text-xs tabular-nums">{{
                report.baseline.completedTrades
              }}</TableCell>
              <TableCell class="text-right text-xs tabular-nums">{{
                report.baseline.wins
              }}</TableCell>
              <TableCell class="text-right text-xs tabular-nums">{{
                formatPrice(report.baseline.netPnl)
              }}</TableCell>
              <TableCell class="text-right text-xs tabular-nums">{{
                fmt(report.baseline.averageRealizedR ?? undefined, 2)
              }}</TableCell>
            </TableRow>
            <TableRow>
              <TableCell class="text-xs">ML-ranked</TableCell>
              <TableCell class="text-right text-xs tabular-nums">{{
                report.ranked.completedTrades
              }}</TableCell>
              <TableCell class="text-right text-xs tabular-nums">{{
                report.ranked.wins
              }}</TableCell>
              <TableCell class="text-right text-xs tabular-nums">{{
                formatPrice(report.ranked.netPnl)
              }}</TableCell>
              <TableCell class="text-right text-xs tabular-nums">{{
                fmt(report.ranked.averageRealizedR ?? undefined, 2)
              }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </Card>
  </div>
</template>
