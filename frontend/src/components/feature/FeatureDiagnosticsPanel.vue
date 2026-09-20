<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronDown } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
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

const detailsOpen = ref(false)

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

const stateCounts = computed(() =>
  Object.entries(props.diagnostics?.stateCounts ?? {}),
)

const nonHealthyStates = computed(() =>
  stateCounts.value.filter(([state]) => state !== 'HEALTHY'),
)

const metricGaps = computed(() =>
  Object.entries(props.diagnostics?.metricGaps ?? {}).filter(
    ([, count]) => count > 0,
  ),
)

const healthy = computed(() => props.diagnostics?.healthyCount ?? 0)
const total = computed(() => props.diagnostics?.watchlistCount ?? 0)
</script>

<template>
  <Card class="overflow-hidden">
    <div class="flex flex-wrap items-center gap-x-4 gap-y-2 p-3">
      <div class="flex items-center gap-2">
        <span class="text-sm font-medium">Feature trust</span>
        <Badge
          :variant="connection === 'open' ? 'secondary' : 'outline'"
          class="gap-1"
        >
          <span
            class="size-1.5 rounded-full"
            :class="
              connection === 'open' ? 'bg-positive' : 'bg-muted-foreground'
            "
            aria-hidden="true"
          />
          {{ connectionLabel }}
        </Badge>
        <Badge
          v-if="gapDetected"
          variant="outline"
          class="border-amber-500/40 text-amber-600 dark:text-amber-400"
        >
          Resyncing
        </Badge>
      </div>

      <dl class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
        <div class="flex items-center gap-1">
          <dt class="text-muted-foreground">Engine</dt>
          <dd class="font-medium">{{ diagnostics?.engineStatus ?? '—' }}</dd>
        </div>
        <div class="flex items-center gap-1">
          <dt class="text-muted-foreground">Trusted</dt>
          <dd class="font-medium tabular-nums">{{ healthy }}/{{ total }}</dd>
        </div>
        <div class="hidden items-center gap-1 sm:flex">
          <dt class="text-muted-foreground">Schema</dt>
          <dd class="font-medium">
            {{ diagnostics?.versions.featureSchemaVersion ?? '—' }}
          </dd>
        </div>
        <div class="hidden items-center gap-1 lg:flex">
          <dt class="text-muted-foreground">Updated</dt>
          <dd class="font-medium">{{ formatAge(lastUpdatedAt) }}</dd>
        </div>
      </dl>

      <div class="ml-auto flex flex-wrap items-center gap-2">
        <template v-if="nonHealthyStates.length">
          <span
            v-for="[state, count] in nonHealthyStates"
            :key="state"
            class="inline-flex items-center gap-1"
          >
            <FeatureStateBadge :state="state" />
            <span class="text-xs tabular-nums text-muted-foreground">{{
              count
            }}</span>
          </span>
        </template>
        <span v-else class="text-xs text-muted-foreground"
          >All rows trusted</span
        >
        <Button variant="ghost" size="sm" @click="detailsOpen = !detailsOpen">
          {{ detailsOpen ? 'Hide details' : 'Details' }}
          <ChevronDown
            class="ml-1 size-3.5 transition-transform"
            :class="detailsOpen ? 'rotate-180' : ''"
            aria-hidden="true"
          />
        </Button>
      </div>
    </div>

    <div v-if="detailsOpen" class="space-y-4 border-t bg-muted/20 p-3 text-xs">
      <div class="grid gap-4 md:grid-cols-2">
        <section class="space-y-2">
          <h3 class="font-medium text-foreground">Current state</h3>
          <div class="flex flex-wrap items-center gap-2">
            <span
              v-for="[state, count] in stateCounts"
              :key="state"
              class="inline-flex items-center gap-1"
            >
              <FeatureStateBadge :state="state" />
              <span class="tabular-nums text-muted-foreground">{{
                count
              }}</span>
            </span>
          </div>
          <div class="space-y-1">
            <p class="text-muted-foreground">
              Unavailable metrics (rows affected):
            </p>
            <div
              v-if="metricGaps.length"
              class="flex flex-wrap gap-x-3 gap-y-1"
            >
              <span
                v-for="[metric, count] in metricGaps"
                :key="metric"
                class="inline-flex items-center gap-1 tabular-nums"
              >
                <span class="font-medium">{{ metric }}</span>
                <span class="text-muted-foreground">{{ count }}</span>
              </span>
            </div>
            <p v-else class="text-muted-foreground">
              No unavailable metrics in the displayed rows.
            </p>
          </div>
        </section>

        <section class="space-y-2">
          <h3 class="font-medium text-foreground">
            Engine counters
            <span class="font-normal text-muted-foreground"
              >(since process start)</span
            >
          </h3>
          <dl class="grid grid-cols-2 gap-x-4 gap-y-1">
            <div
              v-for="counter in counters"
              :key="counter.label"
              class="flex items-center justify-between gap-2"
            >
              <dt class="text-muted-foreground">{{ counter.label }}</dt>
              <dd class="font-medium tabular-nums">{{ counter.value }}</dd>
            </div>
            <div class="flex items-center justify-between gap-2">
              <dt class="text-muted-foreground">Calculation</dt>
              <dd class="font-medium">
                {{ diagnostics?.versions.calculationVersion ?? '—' }}
              </dd>
            </div>
            <div class="flex items-center justify-between gap-2">
              <dt class="text-muted-foreground">Generated</dt>
              <dd class="font-medium">
                {{ formatIstDateTime(diagnostics?.generatedAt) }}
              </dd>
            </div>
            <div class="flex items-center justify-between gap-2">
              <dt class="text-muted-foreground">Sequence</dt>
              <dd class="font-medium tabular-nums">
                {{ lastSequence ?? '—' }}
              </dd>
            </div>
          </dl>
        </section>
      </div>

      <section v-if="diagnostics?.instruments.length" class="space-y-2">
        <h3 class="font-medium text-foreground">Per-instrument trust</h3>
        <div class="flex flex-wrap gap-x-3 gap-y-1.5">
          <span
            v-for="instrument in diagnostics.instruments"
            :key="instrument.instrumentId"
            class="inline-flex items-center gap-1.5"
          >
            <FeatureStateBadge
              :state="instrument.state"
              :reason="instrument.reasonCode"
            />
            <span class="font-medium">{{ instrument.symbol }}</span>
          </span>
        </div>
      </section>

      <section v-if="diagnostics?.notes.length" class="space-y-1">
        <h3 class="font-medium text-foreground">Notes</h3>
        <ul class="list-disc space-y-0.5 pl-4 text-muted-foreground">
          <li v-for="note in diagnostics.notes" :key="note">{{ note }}</li>
        </ul>
      </section>
    </div>
  </Card>
</template>
