<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { Database, Loader2, RefreshCw, Sparkles } from '@lucide/vue'
import { ApiError } from '@/api/http'
import { fundamentalKeys, getFundamentals } from '@/api/fundamentals'
import { narrateFundamentals } from '@/api/llm'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import SectionState from '@/components/market-data/SectionState.vue'
import { formatIstDateTime } from '@/lib/format'
import {
  DEFAULT_NARRATION_QUESTION,
  buildNarrationFacts,
  formatMetricValue,
  formatStatementValue,
  lineLabel,
  metricLabel,
} from '@/lib/fundamental-presentation'

const props = defineProps<{ instrumentId: number | null }>()

const asOf = ref(new Date().toISOString())
const refresh = ref(false)

const enabled = computed(
  () => typeof props.instrumentId === 'number' && props.instrumentId > 0,
)

const query = useQuery(() => ({
  queryKey: fundamentalKeys.detail(
    props.instrumentId ?? 0,
    asOf.value,
    refresh.value,
  ),
  queryFn: ({ signal }: { signal: AbortSignal }) =>
    getFundamentals(props.instrumentId as number, {
      asOf: asOf.value,
      refresh: refresh.value,
      signal,
    }),
  enabled: enabled.value,
  staleTime: 5 * 60 * 1000,
  retry: 1,
}))

const fundamentals = computed(() => query.data.value ?? null)

const notFound = computed(
  () =>
    query.error.value instanceof ApiError &&
    query.error.value.code === 'FUNDAMENTAL_NOT_FOUND',
)

function fetchFromProvider() {
  asOf.value = new Date().toISOString()
  refresh.value = true
}

function reload() {
  asOf.value = new Date().toISOString()
  refresh.value = false
}

const narration = useMutation({
  mutationFn: () => {
    if (!fundamentals.value) {
      throw new Error('No fundamentals loaded')
    }
    return narrateFundamentals({
      question: DEFAULT_NARRATION_QUESTION,
      facts: buildNarrationFacts(fundamentals.value),
    })
  },
})
</script>

<template>
  <div class="space-y-4">
    <Card v-if="!enabled">
      <CardContent class="flex flex-col items-center gap-2 py-12 text-center">
        <Database class="size-5 text-muted-foreground" aria-hidden="true" />
        <p class="text-sm font-medium">Fundamentals unavailable</p>
        <p class="max-w-md text-sm text-muted-foreground">
          Add this instrument to the watchlist to load its point-in-time
          fundamentals.
        </p>
      </CardContent>
    </Card>

    <template v-else>
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex flex-wrap items-center gap-2">
          <Badge variant="secondary">code-computed</Badge>
          <Badge variant="outline">advisory</Badge>
          <span v-if="fundamentals" class="text-xs text-muted-foreground">
            {{ fundamentals.provider }} · {{ fundamentals.fiscalYear }}
            {{ fundamentals.periodType }} ·
            {{ fundamentals.reportingBasis }}
          </span>
        </div>
        <div class="flex items-center gap-2">
          <span v-if="fundamentals" class="text-xs text-muted-foreground">
            filed {{ formatIstDateTime(fundamentals.filedAt) }}
          </span>
          <Button
            size="sm"
            variant="outline"
            :disabled="query.isFetching.value"
            @click="fetchFromProvider"
          >
            <RefreshCw class="size-3.5" aria-hidden="true" />
            Refresh from provider
          </Button>
        </div>
      </div>

      <Card v-if="query.isError.value && !notFound">
        <CardContent class="pt-6">
          <SectionState
            title="Fundamentals unavailable"
            :error="query.error.value"
            @retry="reload"
          />
        </CardContent>
      </Card>

      <Card v-else-if="notFound">
        <CardContent class="flex flex-col items-center gap-3 py-12 text-center">
          <Database class="size-5 text-muted-foreground" aria-hidden="true" />
          <div class="space-y-1">
            <p class="text-sm font-medium">No fundamentals recorded</p>
            <p class="mx-auto max-w-md text-sm text-muted-foreground">
              Fetch the latest reported facts for this instrument. Values are
              stored point-in-time and remain advisory only.
            </p>
          </div>
          <Button size="sm" @click="fetchFromProvider">
            <RefreshCw class="size-3.5" aria-hidden="true" />
            Fetch fundamentals
          </Button>
        </CardContent>
      </Card>

      <Card v-else-if="query.isPending.value">
        <CardContent class="space-y-3 pt-6">
          <div
            v-for="n in 6"
            :key="n"
            class="h-8 w-full animate-pulse rounded-md bg-muted"
          />
        </CardContent>
      </Card>

      <template v-else-if="fundamentals">
        <Card>
          <CardContent class="pt-6">
            <p class="mb-3 text-sm font-medium">Income statement</p>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Line</TableHead>
                  <TableHead class="text-right">Value</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow
                  v-for="line in fundamentals.statements"
                  :key="line.lineCode"
                >
                  <TableCell>{{
                    lineLabel(line.lineCode, line.label)
                  }}</TableCell>
                  <TableCell class="text-right font-mono">
                    {{ formatStatementValue(line.unit, line.value) }}
                  </TableCell>
                </TableRow>
                <TableRow v-if="fundamentals.statements.length === 0">
                  <TableCell
                    :colspan="2"
                    class="text-center text-muted-foreground"
                  >
                    No statement lines reported.
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <Card>
          <CardContent class="pt-6">
            <p class="mb-3 text-sm font-medium">Metrics</p>
            <dl class="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <div
                v-for="metric in fundamentals.metrics"
                :key="metric.metricCode"
                class="space-y-1"
              >
                <dt class="text-xs text-muted-foreground">
                  {{ metricLabel(metric.metricCode) }}
                </dt>
                <dd class="font-mono text-sm">
                  {{ formatMetricValue(metric) }}
                </dd>
              </div>
            </dl>
            <p
              v-if="fundamentals.metrics.length === 0"
              class="text-sm text-muted-foreground"
            >
              No metrics reported.
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardContent class="space-y-3 pt-6">
            <div class="flex flex-wrap items-center gap-2">
              <p class="text-sm font-medium">AI commentary</p>
              <Badge variant="outline">LLM-assisted · advisory</Badge>
            </div>
            <p class="text-xs text-muted-foreground">
              The model explains the code-computed numbers above. It cannot
              change them and does not give trading instructions.
            </p>
            <Button
              size="sm"
              :disabled="narration.isPending.value"
              @click="narration.mutate()"
            >
              <Loader2
                v-if="narration.isPending.value"
                class="size-3.5 animate-spin"
                aria-hidden="true"
              />
              <Sparkles v-else class="size-3.5" aria-hidden="true" />
              Explain with AI
            </Button>
            <p
              v-if="narration.data.value"
              class="whitespace-pre-wrap rounded-md bg-muted p-3 text-sm"
            >
              {{ narration.data.value.text }}
            </p>
            <SectionState
              v-if="narration.isError.value"
              title="AI commentary unavailable"
              :error="narration.error.value"
              @retry="narration.mutate()"
            />
          </CardContent>
        </Card>
      </template>
    </template>
  </div>
</template>
