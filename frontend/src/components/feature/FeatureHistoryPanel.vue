<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { getCandles, historyKeys, type HistoryCandle } from '@/api/history'
import {
  featureKeys,
  getFeatureSeries,
  type FeatureSnapshotResponse,
} from '@/api/features'
import { FEATURE_TIMEFRAMES } from '@/api/features'
import PriceChart from '@/components/market-data/PriceChart.vue'
import { Skeleton } from '@/components/ui/skeleton'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import type { BrokerCandle } from '@/api/types'
import { formatAge, formatIstDateTime } from '@/lib/format'
import {
  availabilityLabel,
  formatFeatureVersion,
  featureVersionTitle,
  presentationState,
} from '@/lib/feature-presentation'
import FeatureOverlayChart from './FeatureOverlayChart.vue'
import FeatureStateBadge from './FeatureStateBadge.vue'

const props = defineProps<{ instrumentId: number; symbol: string }>()

const timeframe = ref<string>('M5')
const rangeKey = ref<string>('5D')

const RANGES: Record<string, number> = { '1D': 1, '5D': 5, '1M': 31, '3M': 92 }

const range = computed(() => {
  const days = RANGES[rangeKey.value] ?? 5
  const to = new Date()
  const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000)
  return { from: from.toISOString(), to: to.toISOString() }
})

const candlesQuery = useQuery(() => ({
  queryKey: historyKeys.candles(
    props.instrumentId,
    timeframe.value,
    range.value.from,
    range.value.to,
  ),
  queryFn: ({ signal }) =>
    getCandles(
      props.instrumentId,
      timeframe.value,
      range.value.from,
      range.value.to,
      5000,
      signal,
    ),
  enabled: props.instrumentId > 0,
  staleTime: 30_000,
}))

const seriesQuery = useQuery(() => ({
  queryKey: featureKeys.series(
    props.instrumentId,
    timeframe.value,
    range.value.from,
    range.value.to,
  ),
  queryFn: ({ signal }) =>
    getFeatureSeries(
      props.instrumentId,
      timeframe.value,
      range.value.from,
      range.value.to,
      2000,
      signal,
    ),
  enabled: props.instrumentId > 0,
  staleTime: 30_000,
}))

function toBrokerCandle(candle: HistoryCandle): BrokerCandle {
  return {
    openTime: candle.openTime,
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
    volume: candle.volume,
    openInterest: candle.openInterest,
  }
}

const candles = computed<BrokerCandle[]>(
  () => candlesQuery.data.value?.map(toBrokerCandle) ?? [],
)

function epochSeconds(iso: string): number {
  return Math.floor(Date.parse(iso) / 1000)
}

// Every anchor is emitted; a missing observation becomes whitespace so the line visibly breaks
// instead of bridging a gap (no smoothing or interpolation).
function points(
  series: FeatureSnapshotResponse[],
  key: string,
): { time: number; value: number | null }[] {
  return series.map((snapshot) => {
    const value = snapshot.features[key]
    const valid =
      value && value.availability === 'VALID' && value.value !== null
    return {
      time: epochSeconds(snapshot.anchorTimestamp),
      value: valid ? value.value : null,
    }
  })
}

const series = computed(() => seriesQuery.data.value ?? [])
const rrsPoints = computed(() => points(series.value, 'RRS_RAW'))
const rvolPoints = computed(() => points(series.value, 'RVOL_INTERVAL'))
const rvePoints = computed(() => points(series.value, 'RVE'))

const featureOverlays = computed(() => [
  {
    key: 'RRS',
    title: 'RRS',
    color: '#2563eb',
    baseline: 0,
    points: rrsPoints.value,
  },
  {
    key: 'RVOL',
    title: 'RVOL',
    color: '#7c3aed',
    baseline: 1,
    points: rvolPoints.value,
  },
  {
    key: 'RVE',
    title: 'RVE',
    color: '#0891b2',
    baseline: 0,
    points: rvePoints.value,
  },
])

const latest = computed(() =>
  series.value.length ? series.value[series.value.length - 1] : null,
)

const availability = computed(() => {
  const counts: Record<string, number> = {}
  for (const snapshot of series.value) {
    const value = snapshot.features['RRS_RAW']
    if (value) {
      counts[value.availability] = (counts[value.availability] ?? 0) + 1
    }
  }
  return counts
})

const versionBoundaries = computed(() => {
  const versions = new Set<string>()
  for (const snapshot of series.value) {
    const value = snapshot.features['RRS_RAW']
    if (value) {
      versions.add(value.featureVersion)
    }
  }
  return [...versions]
})

const loading = computed(
  () => candlesQuery.isPending.value || seriesQuery.isPending.value,
)
const failed = computed(
  () => candlesQuery.isError.value || seriesQuery.isError.value,
)
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center gap-2">
      <NativeSelect
        v-model="timeframe"
        aria-label="Feature timeframe"
        class="w-24"
      >
        <NativeSelectOption
          v-for="option in FEATURE_TIMEFRAMES"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </NativeSelectOption>
      </NativeSelect>
      <NativeSelect v-model="rangeKey" aria-label="History range" class="w-28">
        <NativeSelectOption v-for="(_, key) in RANGES" :key="key" :value="key">
          {{ key }}
        </NativeSelectOption>
      </NativeSelect>
      <FeatureStateBadge
        v-if="latest"
        :state="presentationState(latest.quality, latest.availability)"
        :reason="latest.availability"
      />
    </div>

    <div v-if="loading" class="space-y-2">
      <Skeleton class="h-[300px] w-full" />
      <Skeleton class="h-[140px] w-full" />
    </div>

    <div
      v-else-if="failed"
      class="rounded-md border border-dashed p-4 text-sm text-muted-foreground"
      role="alert"
    >
      Historical features are unavailable for {{ symbol }}. Try a different
      range or timeframe.
    </div>

    <div
      v-else-if="series.length === 0"
      class="rounded-md border border-dashed p-4 text-sm text-muted-foreground"
    >
      No feature observations in this range.
    </div>

    <template v-else>
      <PriceChart
        v-if="candles.length"
        :candles="candles"
        sync-key="feature-history"
      />

      <FeatureOverlayChart
        :series="featureOverlays"
        sync-key="feature-history"
      />

      <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs sm:grid-cols-4">
        <div>
          <dt class="text-muted-foreground">Observation</dt>
          <dd class="font-medium">
            {{ formatIstDateTime(latest?.anchorTimestamp) }}
          </dd>
        </div>
        <div>
          <dt class="text-muted-foreground">Age</dt>
          <dd class="font-medium">{{ formatAge(latest?.anchorTimestamp) }}</dd>
        </div>
        <div>
          <dt class="text-muted-foreground">Schema</dt>
          <dd class="font-medium">{{ latest?.featureSchemaVersion ?? '—' }}</dd>
        </div>
        <div>
          <dt class="text-muted-foreground">RRS version</dt>
          <dd
            class="font-medium"
            :title="
              featureVersionTitle(latest?.features['RRS_RAW']?.featureVersion)
            "
          >
            {{
              formatFeatureVersion(latest?.features['RRS_RAW']?.featureVersion)
            }}
          </dd>
        </div>
      </dl>

      <div
        class="flex flex-wrap items-center gap-2 text-xs text-muted-foreground"
      >
        <span>Availability:</span>
        <span
          v-for="(count, state) in availability"
          :key="state"
          class="tabular-nums"
        >
          {{ availabilityLabel(state) }} {{ count }}
        </span>
      </div>

      <p
        v-if="versionBoundaries.length > 1"
        class="rounded-md border border-amber-500/40 p-2 text-xs text-amber-600 dark:text-amber-400"
        role="status"
      >
        Multiple feature versions appear in this range ({{
          versionBoundaries
            .map((version) => formatFeatureVersion(version))
            .join(', ')
        }}). The series is not continuous; treat version boundaries explicitly.
      </p>
    </template>
  </div>
</template>
