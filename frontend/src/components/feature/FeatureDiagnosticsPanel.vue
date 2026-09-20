<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  Activity,
  ChevronDown,
  Clock,
  Layers,
  ListChecks,
  ShieldAlert,
  ShieldCheck,
  ShieldX,
} from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import type {
  FeatureDiagnosticsResponse,
  MetricAvailability,
} from '@/api/features'
import { formatAge, formatAgeSeconds, formatIstDateTime } from '@/lib/format'
import {
  calculationModeLabel,
  displayReason,
  featureStateRank,
  freshnessMeta,
  metricAvailabilityMeta,
  metricLabel,
  sessionContextLabel,
  tradingImpactMeta,
} from '@/lib/feature-presentation'
import FeatureStateBadge from './FeatureStateBadge.vue'
import ConnectionStatus from './ConnectionStatus.vue'

const props = defineProps<{
  diagnostics: FeatureDiagnosticsResponse | null
  connection: string
  lastUpdatedAt: string | null
  lastSequence: number | null
  gapDetected: boolean
}>()

const emit = defineEmits<{
  (
    event: 'view-affected',
    issue: { metric: string; state: string; reason: string },
  ): void
}>()

// Levels 1 and 2 are visible without diagnostics; level 3 is collapsed by default.
const availabilityOpen = ref(true)
const systemOpen = ref(false)

const stateCounts = computed(() => props.diagnostics?.stateCounts ?? {})

const overallState = computed(() => {
  const states = Object.keys(stateCounts.value)
  if (states.length === 0) {
    return 'UNKNOWN'
  }
  return states.reduce(
    (worst, state) =>
      featureStateRank(state) > featureStateRank(worst) ? state : worst,
    'HEALTHY',
  )
})

const qualityIcon = computed(() => {
  switch (overallState.value) {
    case 'HEALTHY':
      return ShieldCheck
    case 'INVALID':
    case 'UNAVAILABLE':
      return ShieldX
    default:
      return ShieldAlert
  }
})

const qualityTone = computed(() => {
  switch (overallState.value) {
    case 'HEALTHY':
      return 'text-positive'
    case 'INVALID':
    case 'UNAVAILABLE':
      return 'text-negative'
    default:
      return 'text-amber-600 dark:text-amber-400'
  }
})

const total = computed(() => props.diagnostics?.watchlistCount ?? 0)
const affectedCount = computed(() =>
  Math.max(0, total.value - (props.diagnostics?.healthyCount ?? 0)),
)

const scopeSentence = computed(() => {
  if (total.value === 0) {
    return 'No active watchlist instruments.'
  }
  if (affectedCount.value === 0) {
    return `All ${total.value} watchlist instruments report healthy data quality.`
  }
  return `${affectedCount.value} of ${total.value} instruments have a data-quality issue.`
})

const issues = computed<MetricAvailability[]>(() =>
  (props.diagnostics?.metricAvailability ?? []).filter(
    (issue) => issue.affectedCount > 0,
  ),
)

const issueMeta = computed(() =>
  issues.value.map((issue) => ({
    ...issue,
    stateLabel: metricAvailabilityMeta(issue.state).label,
    stateTone: metricAvailabilityMeta(issue.state).tone,
    blocking: metricAvailabilityMeta(issue.state).blocking,
    reason: displayReason(issue.reason),
  })),
)

const tradingImpact = computed(() => {
  const impact = props.diagnostics?.tradingImpact
  if (!impact) {
    return {
      label: tradingImpactMeta(undefined).label,
      glyph: tradingImpactMeta(undefined).glyph,
      tone: tradingImpactMeta(undefined).tone,
      detail: 'No trading-impact result is available.',
    }
  }
  const meta = tradingImpactMeta(impact.status)
  const scoped =
    impact.scopeCount > 0
      ? `${meta.label} for ${impact.scopeCount} instrument${impact.scopeCount === 1 ? '' : 's'}`
      : meta.label
  return {
    label: scoped,
    glyph: meta.glyph,
    tone: meta.tone,
    detail: impact.detail,
  }
})

const freshness = computed(() => {
  const value = props.diagnostics?.freshness
  const state = value?.state ?? 'UNKNOWN'
  return {
    meta: freshnessMeta(state),
    session: sessionContextLabel(value?.sessionContext),
    policySeconds: value?.policySeconds ?? null,
    asOf: value?.asOf ?? null,
    basis: value?.basis ?? '',
  }
})

const counters = computed(() => {
  const value = props.diagnostics?.counters
  if (!value) {
    return []
  }
  return [
    { label: 'Snapshots', value: value.snapshots },
    { label: 'Warm-up fails', value: value.warmupFailures },
    { label: 'Missing deps', value: value.missingDependencies },
    { label: 'Alignment fails', value: value.alignmentFailures },
    { label: 'Quality downgrades', value: value.qualityDowngrades },
    {
      label: 'Persistence queue',
      value: props.diagnostics?.persistenceQueueDepth ?? 0,
    },
    {
      label: 'Persistence dropped',
      value: props.diagnostics?.persistenceDropped ?? 0,
    },
  ]
})

function onViewAffected(issue: MetricAvailability) {
  emit('view-affected', {
    metric: issue.metric,
    state: issue.state,
    reason: issue.reason,
  })
}
</script>

<template>
  <Card class="rounded-xl py-0">
    <CardHeader class="gap-3 p-5">
      <!-- Level 1: always visible -->
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="flex items-center gap-2">
          <component
            :is="qualityIcon"
            class="size-4"
            :class="qualityTone"
            aria-hidden="true"
          />
          <CardTitle class="text-base">Data trust</CardTitle>
          <FeatureStateBadge :state="overallState" />
        </div>
        <ConnectionStatus
          :connection="connection"
          :gap-detected="gapDetected"
          :last-updated-at="lastUpdatedAt"
        />
      </div>

      <CardDescription class="text-sm">{{ scopeSentence }}</CardDescription>

      <p
        data-testid="trading-impact"
        class="flex items-start gap-2 text-sm"
        :class="tradingImpact.tone"
      >
        <span aria-hidden="true">{{ tradingImpact.glyph }}</span>
        <span>
          <span class="font-medium">{{ tradingImpact.label }}.</span>
          <span class="text-muted-foreground"> {{ tradingImpact.detail }}</span>
        </span>
      </p>

      <dl
        class="grid gap-x-6 gap-y-2 border-t pt-3 text-sm sm:grid-cols-2 lg:grid-cols-4"
      >
        <div class="space-y-0.5">
          <dt class="text-xs text-muted-foreground">Data quality</dt>
          <dd class="flex items-center gap-2">
            <FeatureStateBadge :state="overallState" />
            <span class="text-xs text-muted-foreground">
              {{ affectedCount }} affected
            </span>
          </dd>
        </div>
        <div class="space-y-0.5">
          <dt class="text-xs text-muted-foreground">Market-data freshness</dt>
          <dd class="flex items-center gap-2" :class="freshness.meta.tone">
            <Clock class="size-3.5" aria-hidden="true" />
            <span class="font-medium">{{ freshness.meta.label }}</span>
            <span class="text-xs text-muted-foreground">
              {{ freshness.session }}
              <template v-if="freshness.policySeconds !== null">
                · bound {{ formatAgeSeconds(freshness.policySeconds) }}
              </template>
            </span>
          </dd>
        </div>
        <div class="space-y-0.5">
          <dt class="text-xs text-muted-foreground">Calculation mode</dt>
          <dd class="flex items-center gap-2">
            <Layers class="size-3.5 text-muted-foreground" aria-hidden="true" />
            <span class="font-medium">
              {{ calculationModeLabel(diagnostics?.calculationMode) }}
            </span>
          </dd>
        </div>
        <div class="space-y-0.5">
          <dt class="text-xs text-muted-foreground">Snapshot refreshed</dt>
          <dd class="flex items-center gap-2">
            <Activity
              class="size-3.5 text-muted-foreground"
              aria-hidden="true"
            />
            <time
              :datetime="diagnostics?.generatedAt ?? undefined"
              :title="`${formatIstDateTime(diagnostics?.generatedAt)} IST`"
            >
              {{ formatAge(diagnostics?.generatedAt) }}
            </time>
          </dd>
        </div>
      </dl>

      <p class="text-sm text-muted-foreground">
        Market data as of
        <time
          :datetime="freshness.asOf ?? undefined"
          :title="`${formatIstDateTime(freshness.asOf)} IST`"
        >
          {{ formatIstDateTime(freshness.asOf) }} IST
        </time>
        <span v-if="freshness.basis"> · {{ freshness.basis }}</span>
      </p>
    </CardHeader>

    <CardContent class="space-y-4 px-5 pb-5">
      <!-- Level 2: metric availability -->
      <section class="space-y-3">
        <button
          type="button"
          class="flex w-full items-center justify-between gap-2 rounded-md text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          :aria-expanded="availabilityOpen"
          aria-controls="metric-availability"
          @click="availabilityOpen = !availabilityOpen"
        >
          <span class="flex items-center gap-2">
            <ListChecks
              class="size-4 text-muted-foreground"
              aria-hidden="true"
            />
            <span class="text-sm font-medium">Metric availability</span>
            <Badge
              v-if="issueMeta.length"
              variant="outline"
              class="tabular-nums"
            >
              {{ issueMeta.length }}
            </Badge>
          </span>
          <ChevronDown
            class="size-4 transition-transform"
            :class="availabilityOpen ? 'rotate-180' : ''"
            aria-hidden="true"
          />
        </button>

        <div
          v-show="availabilityOpen"
          id="metric-availability"
          class="space-y-2"
        >
          <p v-if="!issueMeta.length" class="text-sm text-muted-foreground">
            No metric availability issues for the current watchlist.
          </p>
          <ul v-else class="divide-y rounded-lg border">
            <li
              v-for="issue in issueMeta"
              :key="`${issue.metric}:${issue.state}:${issue.reason}`"
              class="flex flex-col gap-2 p-3 sm:flex-row sm:items-center sm:justify-between"
            >
              <div class="min-w-0 space-y-1">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="text-sm font-medium">{{
                    metricLabel(issue.metric)
                  }}</span>
                  <Badge variant="outline" :class="issue.stateTone">
                    {{ issue.stateLabel }}
                  </Badge>
                  <span class="text-xs text-muted-foreground tabular-nums">
                    {{ issue.affectedCount }}
                    {{
                      issue.affectedCount === 1 ? 'instrument' : 'instruments'
                    }}
                  </span>
                </div>
                <p class="text-xs text-muted-foreground">{{ issue.reason }}</p>
              </div>
              <Button
                variant="outline"
                size="sm"
                class="shrink-0"
                :aria-label="`View ${issue.affectedCount} rows affected by ${metricLabel(issue.metric)}`"
                @click="onViewAffected(issue)"
              >
                View affected rows
              </Button>
            </li>
          </ul>
        </div>
      </section>

      <!-- Level 3: technical diagnostics, collapsed by default -->
      <section class="space-y-3 border-t pt-4">
        <button
          type="button"
          class="flex w-full items-center justify-between gap-2 rounded-md text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          :aria-expanded="systemOpen"
          aria-controls="technical-diagnostics"
          @click="systemOpen = !systemOpen"
        >
          <span class="flex items-center gap-2">
            <Activity class="size-4 text-muted-foreground" aria-hidden="true" />
            <span class="text-sm font-medium">Technical diagnostics</span>
            <span class="text-xs text-muted-foreground">
              engine counters, versions, sequence
            </span>
          </span>
          <ChevronDown
            class="size-4 transition-transform"
            :class="systemOpen ? 'rotate-180' : ''"
            aria-hidden="true"
          />
        </button>

        <div
          v-show="systemOpen"
          id="technical-diagnostics"
          class="grid gap-4 text-sm md:grid-cols-2"
        >
          <section class="space-y-2">
            <h3 class="text-sm font-medium text-foreground">Engine counters</h3>
            <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
              <div
                v-for="counter in counters"
                :key="counter.label"
                class="flex items-center justify-between gap-2"
              >
                <dt class="text-muted-foreground">{{ counter.label }}</dt>
                <dd class="font-medium tabular-nums">{{ counter.value }}</dd>
              </div>
            </dl>
          </section>
          <section class="space-y-2">
            <h3 class="text-sm font-medium text-foreground">
              Feed and versions
            </h3>
            <dl class="space-y-1 text-xs">
              <div class="flex items-center justify-between gap-2">
                <dt class="text-muted-foreground">Engine</dt>
                <dd class="font-medium">
                  {{ diagnostics?.engineStatus ?? '—' }}
                </dd>
              </div>
              <div class="flex items-center justify-between gap-2">
                <dt class="text-muted-foreground">Schema</dt>
                <dd class="font-medium">
                  {{ diagnostics?.versions.featureSchemaVersion ?? '—' }}
                </dd>
              </div>
              <div class="flex items-center justify-between gap-2">
                <dt class="text-muted-foreground">Calculation</dt>
                <dd class="font-medium">
                  {{ diagnostics?.versions.calculationVersion ?? '—' }}
                </dd>
              </div>
              <div class="flex items-center justify-between gap-2">
                <dt class="text-muted-foreground">Sequence</dt>
                <dd class="font-medium tabular-nums">
                  {{ lastSequence ?? '—' }}
                </dd>
              </div>
              <div class="flex items-center justify-between gap-2">
                <dt class="text-muted-foreground">Snapshot refreshed</dt>
                <dd class="font-medium">
                  <time :datetime="diagnostics?.generatedAt ?? undefined">
                    {{ formatIstDateTime(diagnostics?.generatedAt) }} IST
                  </time>
                </dd>
              </div>
              <div class="flex items-center justify-between gap-2">
                <dt class="text-muted-foreground">Last update</dt>
                <dd class="font-medium">{{ formatAge(lastUpdatedAt) }}</dd>
              </div>
            </dl>
          </section>
          <section
            v-if="diagnostics?.notes.length"
            class="space-y-1 md:col-span-2"
          >
            <h3 class="text-sm font-medium text-foreground">
              Implementation notes
            </h3>
            <ul
              class="list-disc space-y-0.5 pl-4 text-xs text-muted-foreground"
            >
              <li v-for="note in diagnostics.notes" :key="note">{{ note }}</li>
            </ul>
          </section>
        </div>
      </section>
    </CardContent>
  </Card>
</template>
