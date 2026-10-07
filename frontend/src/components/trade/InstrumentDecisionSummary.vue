<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { featureKeys, getFeatureDashboard } from '@/api/features'
import { getOpportunities, opportunityKeys } from '@/api/opportunities'
import { getSetups, setupKeys } from '@/api/setups'
import { getWatchlist, watchlistKeys } from '@/api/watchlist'
import { setupStateMeta, riskStateMeta } from '@/lib/opportunity-presentation'
import WhyBreakdown from '@/components/trade/WhyBreakdown.vue'
import { TriangleAlert } from '@lucide/vue'

const props = defineProps<{ symbol: string }>()

const watchlist = useQuery({
  queryKey: watchlistKeys.all,
  queryFn: ({ signal }) => getWatchlist(signal),
})
const instrumentId = computed(
  () =>
    watchlist.data.value?.entries.find((entry) => entry.symbol === props.symbol)
      ?.instrumentId ?? null,
)
const dashboard = useQuery({
  queryKey: featureKeys.dashboard(),
  queryFn: ({ signal }) => getFeatureDashboard(signal),
})
const opportunities = useQuery({
  queryKey: opportunityKeys.list(),
  queryFn: ({ signal }) => getOpportunities(signal),
})
const setups = useQuery(() => ({
  queryKey: setupKeys.forInstrument(instrumentId.value ?? 0),
  queryFn: ({ signal }) => getSetups(instrumentId.value ?? 0, signal),
  enabled: instrumentId.value != null,
}))

const feature = computed(
  () =>
    dashboard.data.value?.find((row) => row.symbol === props.symbol) ?? null,
)
const opportunity = computed(
  () =>
    opportunities.data.value?.find(
      (row) => row.instrumentId === instrumentId.value,
    ) ?? null,
)
const latestSetup = computed(() => {
  const list = setups.data.value ?? []
  return (
    [...list].sort((a, b) =>
      (b.observedAt ?? '').localeCompare(a.observedAt ?? ''),
    )[0] ?? null
  )
})

function prettify(value: string | null | undefined): string {
  return value ? value.replace(/_/g, ' ').toLowerCase() : '—'
}

interface Chip {
  label: string
  value: string
  tone: string
}
const chips = computed<Chip[]>(() => {
  const setupState =
    latestSetup.value?.setupStatus ?? opportunity.value?.setupStatus ?? null
  return [
    {
      label: 'Market',
      value: prettify(feature.value?.marketState),
      tone: 'text-muted-foreground',
    },
    {
      label: 'Sector',
      value: prettify(feature.value?.sectorState),
      tone: 'text-muted-foreground',
    },
    {
      label: 'Stock',
      value: prettify(
        feature.value?.rrsTrendState ?? feature.value?.dailyRrsState,
      ),
      tone: 'text-muted-foreground',
    },
    {
      label: 'Setup',
      value: setupStateMeta(setupState).label,
      tone: setupStateMeta(setupState).tone,
    },
    {
      label: 'Risk',
      value: opportunity.value
        ? riskStateMeta(opportunity.value.riskState).label
        : 'Not evaluated',
      tone: opportunity.value
        ? riskStateMeta(opportunity.value.riskState).tone
        : 'text-muted-foreground',
    },
    {
      label: 'Plan',
      value: opportunity.value?.planEligibilityStatus ?? 'Not created',
      tone: 'text-muted-foreground',
    },
  ]
})

const DEGRADED_QUALITY = [
  'STALE',
  'DEGRADED',
  'SUSPECT',
  'INVALID',
  'UNAVAILABLE',
]
const warning = computed(() => {
  const row = feature.value
  if (!row) return null
  if (row.availability !== 'VALID') {
    return `Measurements ${row.availability.toLowerCase()} — RRS-dependent gates cannot be evaluated.`
  }
  if (DEGRADED_QUALITY.includes(row.quality)) {
    return `Data quality ${row.quality.toLowerCase()}${row.qualityReason ? ` — ${row.qualityReason}` : ''}.`
  }
  return null
})
</script>

<template>
  <div class="space-y-4">
    <div
      v-if="warning"
      class="flex items-start gap-2 rounded-md border border-amber-500/40 bg-amber-500/5 p-3 text-xs"
      role="alert"
      data-testid="instrument-warning"
    >
      <TriangleAlert
        class="mt-0.5 size-3.5 text-amber-600 dark:text-amber-400"
        aria-hidden="true"
      />
      <span>{{ warning }}</span>
    </div>
    <div
      class="grid grid-cols-3 gap-2 rounded-lg border bg-card p-3 sm:grid-cols-6"
      data-testid="instrument-decision-summary"
    >
      <div v-for="chip in chips" :key="chip.label" class="space-y-0.5">
        <p class="text-[10px] uppercase tracking-wide text-muted-foreground">
          {{ chip.label }}
        </p>
        <p class="truncate text-xs font-medium capitalize" :class="chip.tone">
          {{ chip.value }}
        </p>
      </div>
    </div>
    <WhyBreakdown
      :feature="feature"
      :setup="latestSetup"
      :opportunity="opportunity"
    />
  </div>
</template>
