<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { featureKeys, getFeatureDiagnostics } from '@/api/features'
import { getHealth, systemKeys } from '@/api/system'
import SystemReadinessBar from '@/components/system/SystemReadinessBar.vue'
import { Badge } from '@/components/ui/badge'
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
import EmptyState from '@/components/common/EmptyState.vue'
import { useFeatureStreamStore } from '@/stores/feature-stream'
import { deriveSystemAlerts, type AlertSeverity } from '@/lib/system-alerts'

const diagnostics = useQuery({
  queryKey: featureKeys.diagnostics(),
  queryFn: ({ signal }) => getFeatureDiagnostics(signal),
  refetchInterval: 15000,
})
const health = useQuery({
  queryKey: systemKeys.health(),
  queryFn: ({ signal }) => getHealth(signal),
  refetchInterval: 30000,
})
const stream = useFeatureStreamStore()

const freshness = computed(() => diagnostics.data.value?.freshness ?? null)
const metrics = computed(() => diagnostics.data.value?.metricAvailability ?? [])
const stateCounts = computed(() =>
  Object.entries(diagnostics.data.value?.stateCounts ?? {}),
)
const instruments = computed(() => diagnostics.data.value?.instruments ?? [])

const alerts = computed(() =>
  deriveSystemAlerts({
    health: health.data.value?.status ?? null,
    stream: stream.connection,
    engineStatus: diagnostics.data.value?.engineStatus ?? null,
    freshness: freshness.value?.state ?? null,
    persistenceDropped: diagnostics.data.value?.persistenceDropped ?? null,
    persistenceQueueDepth:
      diagnostics.data.value?.persistenceQueueDepth ?? null,
    metricGaps: diagnostics.data.value?.metricGaps ?? null,
    instruments: instruments.value.map((instrument) => ({
      symbol: instrument.symbol,
      state: instrument.state,
      quality: instrument.quality,
    })),
    counters: diagnostics.data.value?.counters
      ? {
          warmupFailures: diagnostics.data.value.counters.warmupFailures,
          missingDependencies:
            diagnostics.data.value.counters.missingDependencies,
          alignmentFailures: diagnostics.data.value.counters.alignmentFailures,
          qualityDowngrades: diagnostics.data.value.counters.qualityDowngrades,
        }
      : null,
  }),
)

const SEVERITY_TONE: Record<AlertSeverity, string> = {
  BLOCKER: 'text-negative',
  WARNING: 'text-amber-600 dark:text-amber-400',
  INFO: 'text-muted-foreground',
}
</script>

<template>
  <div class="flex flex-col gap-4 p-4 lg:p-6">
    <header class="space-y-1">
      <h1 class="text-xl font-semibold tracking-tight">System health</h1>
      <p class="text-sm text-muted-foreground">
        Readiness, feature-data health and connections. Alerts and the data
        matrix are derived from feature diagnostics; a dedicated data-health or
        alert endpoint does not exist yet.
      </p>
    </header>

    <SystemReadinessBar />

    <Card class="gap-0 py-0" data-testid="system-alerts">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-base">Alerts</CardTitle>
        <CardDescription>
          Derived, non-authoritative signals that may block new trades.
        </CardDescription>
      </CardHeader>
      <CardContent class="border-t p-5">
        <ul v-if="alerts.length" class="space-y-2">
          <li
            v-for="alert in alerts"
            :key="alert.id"
            class="flex items-start gap-2 text-sm"
          >
            <Badge
              variant="outline"
              class="shrink-0 text-[10px] tracking-wide"
              :class="SEVERITY_TONE[alert.severity]"
            >
              {{ alert.severity }}
            </Badge>
            <span>
              <span class="font-medium">{{ alert.source }}:</span>
              {{ alert.message }}
            </span>
          </li>
        </ul>
        <p v-else class="text-sm text-muted-foreground">
          No alerts — all derivable signals are nominal.
        </p>
      </CardContent>
    </Card>

    <div class="grid gap-4 lg:grid-cols-2">
      <Card class="gap-0 py-0">
        <CardHeader class="px-5 py-4">
          <CardTitle class="text-base">Data freshness</CardTitle>
          <CardDescription
            >Feature snapshot age against the configured
            policy.</CardDescription
          >
        </CardHeader>
        <CardContent
          class="grid grid-cols-2 gap-x-4 gap-y-1 border-t p-5 text-sm"
        >
          <span class="text-muted-foreground">State</span>
          <span>{{ freshness?.state ?? 'unknown' }}</span>
          <span class="text-muted-foreground">Session</span>
          <span>{{ freshness?.sessionContext ?? 'unknown' }}</span>
          <span class="text-muted-foreground">Newest sample</span>
          <span>{{
            freshness?.newestAgeSeconds == null
              ? '—'
              : `${freshness.newestAgeSeconds}s`
          }}</span>
          <span class="text-muted-foreground">Oldest sample</span>
          <span>{{
            freshness?.oldestAgeSeconds == null
              ? '—'
              : `${freshness.oldestAgeSeconds}s`
          }}</span>
          <span class="text-muted-foreground">Policy</span>
          <span>{{
            freshness?.policySeconds == null
              ? '—'
              : `${freshness.policySeconds}s`
          }}</span>
          <span class="text-muted-foreground">Persistence queue</span>
          <span>{{
            diagnostics.data.value?.persistenceQueueDepth ?? '—'
          }}</span>
        </CardContent>
      </Card>

      <Card class="gap-0 py-0">
        <CardHeader class="px-5 py-4">
          <CardTitle class="text-base">Connections</CardTitle>
          <CardDescription
            >Broker, feature stream and execution posture.</CardDescription
          >
        </CardHeader>
        <CardContent
          class="grid grid-cols-2 gap-x-4 gap-y-1 border-t p-5 text-sm"
        >
          <span class="text-muted-foreground">Broker</span>
          <span>{{ health.data.value?.status ?? 'unavailable' }}</span>
          <span class="text-muted-foreground">Feature stream</span>
          <span>{{ stream.connection }}</span>
          <span class="text-muted-foreground">Watchlist</span>
          <span>{{ diagnostics.data.value?.watchlistCount ?? '—' }}</span>
          <span class="text-muted-foreground">Healthy</span>
          <span>{{ diagnostics.data.value?.healthyCount ?? '—' }}</span>
          <span class="text-muted-foreground">Execution</span>
          <span>Disabled (advisory)</span>
        </CardContent>
      </Card>
    </div>

    <Card class="gap-0 py-0" data-testid="data-health-matrix">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-base">Data-health matrix</CardTitle>
        <CardDescription>
          Per-measurement and per-instrument availability across the watchlist.
        </CardDescription>
      </CardHeader>
      <CardContent class="space-y-4 border-t p-5">
        <div>
          <p class="mb-1 text-xs font-medium text-muted-foreground">
            Measurements
          </p>
          <Table v-if="metrics.length">
            <TableHeader>
              <TableRow class="hover:bg-transparent">
                <TableHead>Metric</TableHead>
                <TableHead>State</TableHead>
                <TableHead class="text-right">Affected</TableHead>
                <TableHead>Reason</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="metric in metrics" :key="metric.metric">
                <TableCell class="text-xs">{{ metric.metric }}</TableCell>
                <TableCell class="text-xs">
                  <span class="text-amber-600 dark:text-amber-400">{{
                    metric.state
                  }}</span>
                </TableCell>
                <TableCell class="text-right text-xs tabular-nums">{{
                  metric.affectedCount
                }}</TableCell>
                <TableCell class="text-xs text-muted-foreground">{{
                  metric.reason
                }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
          <p v-else class="text-sm text-muted-foreground">
            Every required measurement is available.
          </p>
        </div>

        <div>
          <p class="mb-1 text-xs font-medium text-muted-foreground">
            Instruments
          </p>
          <Table v-if="instruments.length">
            <TableHeader>
              <TableRow class="hover:bg-transparent">
                <TableHead>Symbol</TableHead>
                <TableHead>State</TableHead>
                <TableHead>Quality</TableHead>
                <TableHead class="text-right">Stale</TableHead>
                <TableHead>Reason</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow
                v-for="instrument in instruments"
                :key="instrument.instrumentId"
              >
                <TableCell class="text-xs">{{ instrument.symbol }}</TableCell>
                <TableCell
                  class="text-xs"
                  :class="
                    instrument.state.toUpperCase() === 'HEALTHY'
                      ? 'text-positive'
                      : 'text-amber-600 dark:text-amber-400'
                  "
                >
                  {{ instrument.state }}
                </TableCell>
                <TableCell class="text-xs">{{ instrument.quality }}</TableCell>
                <TableCell class="text-right text-xs tabular-nums">
                  {{
                    instrument.staleSeconds == null
                      ? '—'
                      : `${instrument.staleSeconds}s`
                  }}
                </TableCell>
                <TableCell class="text-xs text-muted-foreground">{{
                  instrument.reasonCode ?? '—'
                }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
          <p v-else class="text-sm text-muted-foreground">
            No instruments on the watchlist.
          </p>
        </div>

        <div v-if="stateCounts.length" class="flex flex-wrap gap-2 text-xs">
          <span
            v-for="[state, count] in stateCounts"
            :key="state"
            class="text-muted-foreground"
          >
            {{ state }}: {{ count }}
          </span>
        </div>
      </CardContent>
    </Card>

    <Card class="gap-0 py-0">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-base">Audit</CardTitle>
        <CardDescription
          >Authoritative intent and control-change history.</CardDescription
        >
      </CardHeader>
      <CardContent class="border-t p-5">
        <p class="text-sm text-muted-foreground">
          No audit-log producer exists yet. Once durable intent and
          configuration-change records are exposed, they will appear here; the
          workstation does not fabricate an audit trail.
        </p>
      </CardContent>
    </Card>

    <EmptyState
      v-if="diagnostics.isError.value"
      title="Feature diagnostics unavailable"
      description="The diagnostics endpoint could not be reached; readiness above uses the last known values."
    />
  </div>
</template>
