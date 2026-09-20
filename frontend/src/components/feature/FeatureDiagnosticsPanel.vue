<script setup lang="ts">
import { computed } from 'vue'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import type { FeatureDiagnosticsResponse } from '@/api/features'
import { formatAge, formatIstDateTime } from '@/lib/format'
import FeatureStateBadge from './FeatureStateBadge.vue'

const props = defineProps<{
  diagnostics: FeatureDiagnosticsResponse | null
  connection: string
  lastUpdatedAt: string | null
  lastSequence: number | null
  gapDetected: boolean
}>()

const connectionLabel = computed(() => {
  switch (props.connection) {
    case 'open':
      return 'Live'
    case 'connecting':
      return 'Connecting'
    case 'reconnecting':
      return 'Reconnecting'
    case 'closed':
      return 'Disconnected'
    default:
      return 'Idle'
  }
})

const counters = computed(() => {
  const value = props.diagnostics?.counters
  if (!value) {
    return []
  }
  return [
    { label: 'Snapshots (since start)', value: value.snapshots },
    { label: 'Warm-up fails (since start)', value: value.warmupFailures },
    { label: 'Missing deps (since start)', value: value.missingDependencies },
    { label: 'Alignment fails (since start)', value: value.alignmentFailures },
    {
      label: 'Quality downgrades (since start)',
      value: value.qualityDowngrades,
    },
    {
      label: 'Persistence queue',
      value: props.diagnostics?.persistenceQueueDepth ?? 0,
    },
    {
      label: 'Persistence dropped (since start)',
      value: props.diagnostics?.persistenceDropped ?? 0,
    },
  ]
})

const stateCounts = computed(() =>
  Object.entries(props.diagnostics?.stateCounts ?? {}),
)

const metricGaps = computed(() =>
  Object.entries(props.diagnostics?.metricGaps ?? {}).filter(
    ([, count]) => count > 0,
  ),
)
</script>

<template>
  <Card>
    <CardHeader class="pb-2">
      <CardTitle class="flex flex-wrap items-center gap-2 text-sm">
        Feature diagnostics
        <Badge :variant="connection === 'open' ? 'secondary' : 'outline'">
          {{ connectionLabel }}
        </Badge>
        <Badge
          v-if="gapDetected"
          variant="outline"
          class="border-amber-500/40 text-amber-600"
        >
          Sequence gap — resyncing
        </Badge>
      </CardTitle>
    </CardHeader>
    <CardContent class="space-y-3 text-xs">
      <dl class="grid grid-cols-2 gap-x-4 gap-y-1 sm:grid-cols-4">
        <div>
          <dt class="text-muted-foreground">Engine</dt>
          <dd class="font-medium">{{ diagnostics?.engineStatus ?? '—' }}</dd>
        </div>
        <div>
          <dt class="text-muted-foreground">Watchlist</dt>
          <dd class="font-medium">{{ diagnostics?.watchlistCount ?? '—' }}</dd>
        </div>
        <div>
          <dt class="text-muted-foreground">Last update</dt>
          <dd class="font-medium">{{ formatAge(lastUpdatedAt) }}</dd>
        </div>
        <div>
          <dt class="text-muted-foreground">Sequence</dt>
          <dd class="font-medium tabular-nums">{{ lastSequence ?? '—' }}</dd>
        </div>
        <div>
          <dt class="text-muted-foreground">Schema</dt>
          <dd class="font-medium">
            {{ diagnostics?.versions.featureSchemaVersion ?? '—' }}
          </dd>
        </div>
        <div>
          <dt class="text-muted-foreground">Calculation</dt>
          <dd class="font-medium">
            {{ diagnostics?.versions.calculationVersion ?? '—' }}
          </dd>
        </div>
        <div>
          <dt class="text-muted-foreground">Generated</dt>
          <dd class="font-medium">
            {{ formatIstDateTime(diagnostics?.generatedAt) }}
          </dd>
        </div>
      </dl>

      <div class="flex flex-wrap items-center gap-2">
        <span
          v-for="[state, count] in stateCounts"
          :key="state"
          class="inline-flex items-center gap-1"
        >
          <FeatureStateBadge :state="state" />
          <span class="tabular-nums text-muted-foreground">{{ count }}</span>
        </span>
      </div>

      <div v-if="metricGaps.length" class="space-y-1">
        <p class="text-muted-foreground">
          Current per-metric gaps (rows unavailable):
        </p>
        <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span
            v-for="[metric, count] in metricGaps"
            :key="metric"
            class="inline-flex items-center gap-1 tabular-nums"
          >
            <span class="font-medium">{{ metric }}</span>
            <span class="text-muted-foreground">{{ count }}</span>
          </span>
        </div>
      </div>

      <dl class="grid grid-cols-2 gap-x-4 gap-y-1 sm:grid-cols-4">
        <div v-for="counter in counters" :key="counter.label">
          <dt class="text-muted-foreground">{{ counter.label }}</dt>
          <dd class="font-medium tabular-nums">{{ counter.value }}</dd>
        </div>
      </dl>

      <ul
        v-if="diagnostics?.notes.length"
        class="list-disc space-y-0.5 pl-4 text-muted-foreground"
      >
        <li v-for="note in diagnostics.notes" :key="note">{{ note }}</li>
      </ul>
    </CardContent>
  </Card>
</template>
