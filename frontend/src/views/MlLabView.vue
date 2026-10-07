<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { Play, RefreshCw } from '@lucide/vue'
import {
  createAnalysisRun,
  listAnalysisRuns,
  mlKeys,
  type MlAnalysisConfig,
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

const router = useRouter()
const queryClient = useQueryClient()

function iso(date: Date): string {
  return date.toISOString().slice(0, 10)
}
const today = new Date()
const threeYearsAgo = new Date(today)
threeYearsAgo.setFullYear(today.getFullYear() - 3)

const form = reactive({
  symbols: [] as string[],
  setupTimeframe: 'M5' as MlAnalysisConfig['setupTimeframe'],
  dailyTimeframe: 'D1',
  startDate: iso(threeYearsAgo),
  endDate: iso(today),
  seed: 7,
  modelCode: 'er-ranker',
  rounds: 200,
  rankIc: 0.05,
  ndcg: 0.5,
})

const watchlist = useQuery({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
  staleTime: 60_000,
})
const universe = computed(
  () => watchlist.data.value?.entries.map((entry) => entry.symbol) ?? [],
)

const runsQuery = useQuery({
  queryKey: mlKeys.runs(),
  queryFn: ({ signal }) => listAnalysisRuns(signal),
  refetchInterval: 5000,
})

function toggleSymbol(symbol: string) {
  form.symbols = form.symbols.includes(symbol)
    ? form.symbols.filter((value) => value !== symbol)
    : [...form.symbols, symbol]
}

const PRESETS = [
  { label: 'Quick · M5 · 1y', timeframe: 'M5', years: 1 },
  { label: 'Standard · M5 · 3y', timeframe: 'M5', years: 3 },
  { label: 'Intraday · M15 · 2y', timeframe: 'M15', years: 2 },
] as const

function applyPreset(preset: (typeof PRESETS)[number]) {
  form.setupTimeframe = preset.timeframe
  const start = new Date(today)
  start.setFullYear(today.getFullYear() - preset.years)
  form.startDate = iso(start)
  form.endDate = iso(today)
}

const createMutation = useMutation({
  mutationFn: () =>
    createAnalysisRun({
      symbols: form.symbols,
      setupTimeframe: form.setupTimeframe,
      dailyTimeframe: form.dailyTimeframe,
      startDate: form.startDate,
      endDate: form.endDate,
      seed: form.seed,
      modelCode: form.modelCode,
      rounds: form.rounds,
      thresholds: { rankIc: form.rankIc, ndcg: form.ndcg },
    }),
  onSuccess: (run) => {
    void queryClient.invalidateQueries({ queryKey: mlKeys.runs() })
    void router.push({ name: 'ml-run', params: { runKey: run.key } })
  },
})

const error = computed(() =>
  createMutation.error.value instanceof ApiError
    ? createMutation.error.value.message
    : createMutation.error.value
      ? 'Could not start the analysis.'
      : null,
)

function metricsOf(metrics: Record<string, unknown>, key: string): string {
  const block = metrics[key] as { rankIc?: number; ndcg?: number } | undefined
  if (!block || block.rankIc == null) return '—'
  const ic = block.rankIc.toFixed(3)
  const ndcg = block.ndcg == null ? '—' : block.ndcg.toFixed(2)
  return `IC ${ic} · NDCG ${ndcg}`
}
</script>

<template>
  <div class="flex flex-col gap-4 p-4 lg:p-6">
    <header class="space-y-1">
      <h1 class="text-xl font-semibold tracking-tight">ML Lab</h1>
      <p class="max-w-3xl text-sm text-muted-foreground">
        Train a gradient-boosted ranker over VALID setups. The model only orders
        or filters already-valid opportunities — it never rescues an invalid
        setup or overrides risk.
      </p>
    </header>

    <Card class="gap-0 py-0">
      <CardHeader class="px-5 py-5">
        <CardTitle class="text-base">New analysis</CardTitle>
        <CardDescription>
          Choose a universe, setup timeframe and window; the Python research
          runner does the training.
        </CardDescription>
      </CardHeader>
      <CardContent class="space-y-3 border-t p-5">
        <div class="flex flex-wrap gap-2">
          <Button
            v-for="preset in PRESETS"
            :key="preset.label"
            variant="outline"
            size="sm"
            @click="applyPreset(preset)"
          >
            {{ preset.label }}
          </Button>
        </div>

        <fieldset class="space-y-1 text-sm">
          <legend class="text-muted-foreground">
            Universe (active watchlist)
            <span class="text-xs">· {{ form.symbols.length }} selected</span>
          </legend>
          <div
            v-if="universe.length"
            class="flex flex-wrap gap-x-4 gap-y-1 rounded-md border p-2"
          >
            <label
              v-for="symbol in universe"
              :key="symbol"
              class="flex items-center gap-1.5 text-sm"
            >
              <input
                type="checkbox"
                :checked="form.symbols.includes(symbol)"
                :aria-label="`Include ${symbol}`"
                @change="toggleSymbol(symbol)"
              />
              <span>{{ symbol }}</span>
            </label>
          </div>
          <p v-else class="text-xs text-muted-foreground">
            No watched instruments. Add symbols on the Watchlist page first.
          </p>
        </fieldset>

        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label class="space-y-1 text-sm">
            <span class="text-muted-foreground">Setup timeframe</span>
            <select
              v-model="form.setupTimeframe"
              aria-label="Setup timeframe"
              class="w-full rounded-md border bg-transparent px-2 py-1"
            >
              <option value="M5">M5</option>
              <option value="M15">M15</option>
              <option value="M30">M30</option>
            </select>
          </label>
          <label class="space-y-1 text-sm">
            <span class="text-muted-foreground">Start</span>
            <input
              v-model="form.startDate"
              type="date"
              aria-label="Start date"
              class="w-full rounded-md border bg-transparent px-2 py-1"
            />
          </label>
          <label class="space-y-1 text-sm">
            <span class="text-muted-foreground">End</span>
            <input
              v-model="form.endDate"
              type="date"
              aria-label="End date"
              class="w-full rounded-md border bg-transparent px-2 py-1"
            />
          </label>
          <label class="space-y-1 text-sm">
            <span class="text-muted-foreground">Seed</span>
            <input
              v-model.number="form.seed"
              type="number"
              aria-label="Seed"
              class="w-full rounded-md border bg-transparent px-2 py-1"
            />
          </label>
          <label class="space-y-1 text-sm">
            <span class="text-muted-foreground">Model code</span>
            <input
              v-model="form.modelCode"
              aria-label="Model code"
              class="w-full rounded-md border bg-transparent px-2 py-1"
            />
          </label>
          <label class="space-y-1 text-sm">
            <span class="text-muted-foreground">Rounds</span>
            <input
              v-model.number="form.rounds"
              type="number"
              aria-label="Rounds"
              class="w-full rounded-md border bg-transparent px-2 py-1"
            />
          </label>
          <label class="space-y-1 text-sm">
            <span class="text-muted-foreground">Min rank IC</span>
            <input
              v-model.number="form.rankIc"
              type="number"
              step="0.01"
              aria-label="Min rank IC"
              class="w-full rounded-md border bg-transparent px-2 py-1"
            />
          </label>
          <label class="space-y-1 text-sm">
            <span class="text-muted-foreground">Min NDCG</span>
            <input
              v-model.number="form.ndcg"
              type="number"
              step="0.1"
              aria-label="Min NDCG"
              class="w-full rounded-md border bg-transparent px-2 py-1"
            />
          </label>
        </div>

        <div class="flex items-center gap-3">
          <Button
            :disabled="
              form.symbols.length === 0 || createMutation.isPending.value
            "
            @click="createMutation.mutate()"
          >
            <Play class="mr-1 size-3.5" aria-hidden="true" /> Start analysis
          </Button>
          <p v-if="error" class="text-sm text-negative" role="alert">
            {{ error }}
          </p>
        </div>
      </CardContent>
    </Card>

    <Card class="gap-0 overflow-hidden py-0">
      <CardHeader class="flex-row items-center justify-between px-5 py-4">
        <div>
          <CardTitle class="text-base">Analysis runs</CardTitle>
          <CardDescription>Queued and completed training runs.</CardDescription>
        </div>
        <Button variant="outline" size="sm" @click="runsQuery.refetch()">
          <RefreshCw class="mr-1 size-3.5" aria-hidden="true" /> Refresh
        </Button>
      </CardHeader>
      <div class="overflow-x-auto border-t">
        <Table class="min-w-[820px]">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead>Run</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Universe</TableHead>
              <TableHead>Window</TableHead>
              <TableHead>Out-of-sample</TableHead>
              <TableHead class="text-right">Model</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="run in runsQuery.data.value ?? []"
              :key="run.key"
              class="cursor-pointer"
              @click="
                router.push({ name: 'ml-run', params: { runKey: run.key } })
              "
            >
              <TableCell class="font-mono text-xs">{{
                run.key.slice(0, 8)
              }}</TableCell>
              <TableCell>
                <Badge
                  variant="outline"
                  :data-testid="`ml-run-status-${run.key}`"
                >
                  {{ run.status }}
                </Badge>
              </TableCell>
              <TableCell class="text-xs">
                {{ (run.config.symbols as string[] | undefined)?.length ?? 0 }}
              </TableCell>
              <TableCell class="text-xs">
                {{ run.config.startDate }} – {{ run.config.endDate }}
              </TableCell>
              <TableCell class="text-xs tabular-nums">
                {{ metricsOf(run.metrics, 'outOfSample') }}
              </TableCell>
              <TableCell class="text-right text-xs">
                {{ run.modelVersionId ?? '—' }}
              </TableCell>
            </TableRow>
            <TableRow v-if="(runsQuery.data.value ?? []).length === 0">
              <TableCell
                colspan="6"
                class="py-8 text-center text-sm text-muted-foreground"
              >
                No analysis runs yet.
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </Card>
  </div>
</template>
