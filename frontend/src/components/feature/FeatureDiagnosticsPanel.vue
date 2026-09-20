<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronDown } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import type { FeatureDiagnosticsResponse } from '@/api/features'
import { formatAge, formatIstDateTime } from '@/lib/format'
import {
  featureStateMeta,
  featureStateRank,
  metricLabel,
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

const systemOpen = ref(false)

const stateCounts = computed(() => props.diagnostics?.stateCounts ?? {})

const overallState = computed(() => {
  const states = Object.keys(stateCounts.value)
  if (states.length === 0) {
    return 'WARMING_UP'
  }
  return states.reduce(
    (worst, state) =>
      featureStateRank(state) > featureStateRank(worst) ? state : worst,
    'HEALTHY',
  )
})

const summary = computed(() => {
  const total = props.diagnostics?.watchlistCount ?? 0
  if (total === 0) {
    return 'No active watchlist instruments.'
  }
  const nonHealthy = Object.entries(stateCounts.value)
    .filter(([state]) => state !== 'HEALTHY')
    .sort((a, b) => featureStateRank(b[0]) - featureStateRank(a[0]))
  if (nonHealthy.length === 0) {
    return `All ${total} instruments are fresh and trustworthy.`
  }
  const parts = nonHealthy
    .map(
      ([state, count]) =>
        `${count} ${featureStateMeta(state).label.toLowerCase()}`,
    )
    .join(' · ')
  const blocked =
    (stateCounts.value.UNAVAILABLE ?? 0) + (stateCounts.value.INVALID ?? 0)
  return blocked > 0
    ? `${parts}. Some features cannot be used for decisions — open a row's status for the reason.`
    : `${parts}. Measurements are shown; open a row's status for the reason.`
})

const metricGaps = computed(() =>
  Object.entries(props.diagnostics?.metricGaps ?? {})
    .filter(([, count]) => count > 0)
    .map(([metric, count]) => ({ metric, label: metricLabel(metric), count })),
)

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
</script>

<template>
  <Card class="overflow-hidden">
    <CardHeader class="gap-1 px-4 py-4 sm:px-6">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <CardTitle class="text-sm">Data trust</CardTitle>
          <FeatureStateBadge :state="overallState" />
        </div>
        <ConnectionStatus
          :connection="connection"
          :gap-detected="gapDetected"
          :last-updated-at="lastUpdatedAt"
        />
      </div>
      <CardDescription>{{ summary }}</CardDescription>
    </CardHeader>

    <CardContent class="space-y-3 px-4 pb-4 sm:px-6">
      <div
        v-if="metricGaps.length"
        class="flex flex-wrap items-center gap-1.5 text-xs"
      >
        <span class="text-muted-foreground">Unavailable by metric:</span>
        <Badge
          v-for="gap in metricGaps"
          :key="gap.metric"
          variant="outline"
          class="gap-1 font-normal text-muted-foreground"
        >
          {{ gap.label }}
          <span class="tabular-nums">{{ gap.count }}</span>
        </Badge>
      </div>

      <Button
        variant="ghost"
        size="sm"
        class="h-7 px-2 text-xs"
        @click="systemOpen = !systemOpen"
      >
        {{ systemOpen ? 'Hide' : 'Show' }} engine and feed details
        <ChevronDown
          class="ml-1 size-3.5 transition-transform"
          :class="systemOpen ? 'rotate-180' : ''"
          aria-hidden="true"
        />
      </Button>

      <div v-if="systemOpen" class="grid gap-4 text-xs md:grid-cols-2">
        <section class="space-y-2">
          <h3 class="font-medium text-foreground">Engine counters</h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1">
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
          <h3 class="font-medium text-foreground">Feed and versions</h3>
          <dl class="space-y-1">
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
              <dt class="text-muted-foreground">Generated</dt>
              <dd class="font-medium">
                {{ formatIstDateTime(diagnostics?.generatedAt) }} IST
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
          <h3 class="font-medium text-foreground">Notes</h3>
          <ul class="list-disc space-y-0.5 pl-4 text-muted-foreground">
            <li v-for="note in diagnostics.notes" :key="note">{{ note }}</li>
          </ul>
        </section>
      </div>
    </CardContent>
  </Card>
</template>
