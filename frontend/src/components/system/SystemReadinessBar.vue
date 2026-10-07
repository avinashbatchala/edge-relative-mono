<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Activity, Database, ShieldOff } from '@lucide/vue'
import { getFeatureDiagnostics, featureKeys } from '@/api/features'
import { getHealth, getSystemMode, systemKeys } from '@/api/system'
import { Badge } from '@/components/ui/badge'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import { Separator } from '@/components/ui/separator'
import { useSystemReadiness } from '@/composables/useSystemReadiness'
import { formatAge } from '@/lib/format'
import { READINESS_TONE } from '@/lib/system-readiness'
import { useFeatureStreamStore } from '@/stores/feature-stream'
import TradingModeBadge from '@/components/system/TradingModeBadge.vue'

const readiness = useSystemReadiness()
const stream = useFeatureStreamStore()

const health = useQuery({
  queryKey: systemKeys.health(),
  queryFn: ({ signal }) => getHealth(signal),
  refetchInterval: 30000,
})
const diagnostics = useQuery({
  queryKey: featureKeys.diagnostics(),
  queryFn: ({ signal }) => getFeatureDiagnostics(signal),
  refetchInterval: 30000,
})

const modeQuery = useQuery({
  queryKey: systemKeys.mode(),
  queryFn: ({ signal }) => getSystemMode(signal),
  refetchInterval: 30000,
})

const mode = computed(() => modeQuery.data.value ?? null)
const executionEnabled = computed(() => mode.value?.executionEnabled ?? false)

const freshness = computed(() => diagnostics.data.value?.freshness ?? null)
const broker = computed(() => {
  const status = health.data.value?.status
  if (!status) return { label: 'Broker unavailable', ok: false }
  return status.toUpperCase() === 'UP'
    ? { label: 'Broker healthy', ok: true }
    : { label: `Broker ${status}`, ok: false }
})
const streamLabel = computed(() => {
  switch (stream.connection) {
    case 'open':
      return 'Stream live'
    case 'connecting':
      return 'Stream connecting'
    case 'reconnecting':
      return 'Stream reconnecting'
    case 'closed':
      return 'Stream offline'
    default:
      return 'Stream idle'
  }
})
const sessionLabel = computed(() =>
  (freshness.value?.sessionContext ?? 'UNKNOWN')
    .replace('_', ' ')
    .toLowerCase(),
)
</script>

<template>
  <div
    class="flex flex-wrap items-center gap-2 rounded-lg border bg-card px-3 py-2 text-xs"
    data-testid="system-readiness"
  >
    <Sheet>
      <SheetTrigger as-child>
        <button
          type="button"
          class="flex items-center gap-1.5 rounded-md px-1.5 py-0.5 font-medium hover:bg-accent"
          :aria-label="`System readiness: ${readiness.state}. Open diagnostics.`"
        >
          <span
            class="size-2 rounded-full bg-current"
            :class="READINESS_TONE[readiness.state]"
            aria-hidden="true"
          />
          <span :class="READINESS_TONE[readiness.state]">{{
            readiness.headline
          }}</span>
        </button>
      </SheetTrigger>
      <SheetContent side="right" class="w-full sm:max-w-sm">
        <SheetHeader>
          <SheetTitle>System readiness · {{ readiness.state }}</SheetTitle>
          <SheetDescription>
            Derived from health, feature stream, data freshness and session.
            Non-authoritative.
          </SheetDescription>
        </SheetHeader>
        <div class="space-y-3 px-4 pb-6 text-sm">
          <section>
            <h3 class="mb-1 font-medium">Reasons</h3>
            <ul class="list-disc pl-4 text-xs text-muted-foreground">
              <li v-for="reason in readiness.reasons" :key="reason">
                {{ reason }}
              </li>
              <li v-if="readiness.reasons.length === 0">
                No blocking conditions.
              </li>
            </ul>
          </section>
          <Separator />
          <section class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
            <span class="text-muted-foreground">Session</span
            ><span>{{ sessionLabel }}</span>
            <span class="text-muted-foreground">Data freshness</span>
            <span>{{ freshness?.state ?? 'unknown' }}</span>
            <span class="text-muted-foreground">Newest sample</span>
            <span>{{
              freshness?.newestAgeSeconds == null
                ? '—'
                : `${freshness.newestAgeSeconds}s`
            }}</span>
            <span class="text-muted-foreground">Feature stream</span
            ><span>{{ streamLabel }}</span>
            <span class="text-muted-foreground">Last update</span>
            <span>{{
              stream.lastUpdatedAt ? formatAge(stream.lastUpdatedAt) : '—'
            }}</span>
            <span class="text-muted-foreground">Broker</span
            ><span>{{ broker.label }}</span>
            <span class="text-muted-foreground">Stop new trades</span>
            <span>{{ mode?.control.stopNewTrades ? 'Engaged' : 'No' }}</span>
            <span class="text-muted-foreground">Flatten only</span>
            <span>{{ mode?.control.flattenOnly ? 'Engaged' : 'No' }}</span>
            <span class="text-muted-foreground">Execution</span>
            <span>{{
              executionEnabled
                ? 'Enabled by control state'
                : 'Disabled (advisory)'
            }}</span>
          </section>
        </div>
      </SheetContent>
    </Sheet>

    <TradingModeBadge :mode="mode?.configuredMode" :derived="mode === null" />

    <Badge variant="outline" class="gap-1 font-normal">
      <Database class="size-3" aria-hidden="true" />
      <span>{{ broker.label }}</span>
    </Badge>

    <Badge variant="outline" class="gap-1 font-normal">
      <Activity class="size-3" aria-hidden="true" />
      <span>{{ streamLabel }}</span>
    </Badge>

    <Badge variant="outline" class="gap-1 font-normal">
      <ShieldOff class="size-3" aria-hidden="true" />
      <span>{{
        executionEnabled ? 'Execution enabled' : 'Execution disabled'
      }}</span>
    </Badge>

    <span class="ml-auto text-muted-foreground">
      {{ sessionLabel }} · data
      {{
        freshness?.newestAgeSeconds == null
          ? '—'
          : `${freshness.newestAgeSeconds}s`
      }}
    </span>
  </div>
</template>
