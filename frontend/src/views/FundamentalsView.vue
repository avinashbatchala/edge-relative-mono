<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Database, RefreshCw } from '@lucide/vue'
import {
  getFundamentals,
  type FundamentalMetric,
  type FundamentalResponse,
} from '@/api/fundamentals'
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
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import EmptyState from '@/components/common/EmptyState.vue'
import FundamentalPanel from '@/components/fundamental/FundamentalPanel.vue'
import { formatMetricValue } from '@/lib/fundamental-presentation'
import { formatIstDate } from '@/lib/format'

const watchlistQuery = useQuery(() => ({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
  staleTime: 30_000,
  retry: 1,
}))

const entries = computed(() => watchlistQuery.data.value?.entries ?? [])

const asOf = ref(new Date().toISOString())
const refresh = ref(false)

const fundamentalsQuery = useQuery(() => ({
  queryKey: [
    'fundamentals',
    'watchlist',
    asOf.value,
    refresh.value,
    entries.value.map((entry) => entry.instrumentId),
  ] as const,
  queryFn: async ({ signal }) => {
    const result: Record<number, FundamentalResponse | null> = {}
    for (const entry of entries.value) {
      try {
        result[entry.instrumentId] = await getFundamentals(entry.instrumentId, {
          asOf: asOf.value,
          refresh: refresh.value,
          signal,
        })
      } catch {
        result[entry.instrumentId] = null
      }
    }
    return result
  },
  enabled: entries.value.length > 0,
  staleTime: 5 * 60 * 1000,
  retry: false,
}))

const selectedId = ref<number | null>(null)
const selectedOpen = computed({
  get: () => selectedId.value !== null,
  set: (open: boolean) => {
    if (!open) {
      selectedId.value = null
    }
  },
})

const SCREENER_METRICS = [
  'trailing_pe',
  'price_to_book',
  'return_on_equity',
  'profit_margin',
  'debt_to_equity',
  'market_cap',
] as const

function metric(
  response: FundamentalResponse | null,
  code: string,
): FundamentalMetric | null {
  return response?.metrics.find((m) => m.metricCode === code) ?? null
}

function cell(response: FundamentalResponse | null, code: string): string {
  const value = metric(response, code)
  return value ? formatMetricValue(value) : '—'
}

function reload() {
  asOf.value = new Date().toISOString()
  refresh.value = true
}
</script>

<template>
  <main
    class="mx-auto w-full max-w-[1600px] flex-1 space-y-4 px-4 py-6 lg:px-6"
  >
    <div
      class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between"
    >
      <div class="space-y-1">
        <h1 class="text-2xl font-semibold tracking-tight">Fundamentals</h1>
        <p class="text-sm text-muted-foreground">
          Point-in-time, code-computed fundamentals for the watchlist. Advisory
          context only — never a trade instruction.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <Badge variant="secondary">code-computed</Badge>
        <Badge variant="outline">advisory</Badge>
        <Button
          variant="outline"
          size="sm"
          :disabled="fundamentalsQuery.isFetching.value"
          @click="reload"
        >
          <RefreshCw class="mr-1 size-3.5" aria-hidden="true" /> Refresh
        </Button>
      </div>
    </div>

    <Card>
      <CardHeader class="pb-3">
        <CardTitle class="text-base">Watchlist screener</CardTitle>
        <CardDescription>
          Latest reported period per instrument. Missing values are shown as
          “—”, never zero.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Skeleton v-if="watchlistQuery.isPending.value" class="h-40 w-full" />
        <EmptyState
          v-else-if="entries.length === 0"
          :icon="Database"
          title="Watchlist is empty"
          description="Add instruments to the watchlist to see their fundamentals."
        />
        <div v-else-if="fundamentalsQuery.isPending.value" class="space-y-2">
          <Skeleton v-for="n in 5" :key="n" class="h-9 w-full" />
        </div>
        <Table v-else>
          <TableHeader>
            <TableRow>
              <TableHead>Symbol</TableHead>
              <TableHead>Period</TableHead>
              <TableHead class="text-right">P/E</TableHead>
              <TableHead class="text-right">P/B</TableHead>
              <TableHead class="text-right">ROE</TableHead>
              <TableHead class="text-right">Net margin</TableHead>
              <TableHead class="text-right">D/E</TableHead>
              <TableHead class="text-right">Market cap</TableHead>
              <TableHead>Filed</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="entry in entries"
              :key="entry.instrumentId"
              class="cursor-pointer"
              @click="selectedId = entry.instrumentId"
            >
              <TableCell class="font-medium">{{ entry.symbol }}</TableCell>
              <TableCell class="text-muted-foreground">
                {{
                  fundamentalsQuery.data.value?.[entry.instrumentId]
                    ?.fiscalYear ?? '—'
                }}
              </TableCell>
              <TableCell
                v-for="code in SCREENER_METRICS"
                :key="code"
                class="text-right tabular-nums"
              >
                {{
                  cell(
                    fundamentalsQuery.data.value?.[entry.instrumentId] ?? null,
                    code,
                  )
                }}
              </TableCell>
              <TableCell class="text-muted-foreground">
                {{
                  formatIstDate(
                    fundamentalsQuery.data.value?.[entry.instrumentId]?.filedAt,
                  )
                }}
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <Sheet v-model:open="selectedOpen">
      <SheetContent class="w-full overflow-y-auto sm:max-w-3xl">
        <SheetHeader>
          <SheetTitle>Fundamentals</SheetTitle>
          <SheetDescription>
            Point-in-time values and advisory AI commentary.
          </SheetDescription>
        </SheetHeader>
        <div v-if="selectedId !== null" class="mt-4">
          <FundamentalPanel :instrument-id="selectedId" />
        </div>
      </SheetContent>
    </Sheet>
  </main>
</template>
