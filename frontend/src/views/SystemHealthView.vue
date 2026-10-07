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
import EmptyState from '@/components/common/EmptyState.vue'
import { useFeatureStreamStore } from '@/stores/feature-stream'

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
</script>

<template>
  <div class="flex flex-col gap-4 p-4 lg:p-6">
    <header class="space-y-1">
      <h1 class="text-xl font-semibold tracking-tight">System health</h1>
      <p class="text-sm text-muted-foreground">
        Readiness, feature-data health and connections. Per-source detail is
        derived from feature diagnostics; a dedicated data-health endpoint does
        not exist yet.
      </p>
    </header>

    <SystemReadinessBar />

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

    <Card class="gap-0 py-0">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-base">Per-instrument state</CardTitle>
        <CardDescription>
          Availability of required measurements across the watchlist.
        </CardDescription>
      </CardHeader>
      <CardContent class="border-t p-5">
        <div v-if="metrics.length" class="flex flex-wrap gap-2">
          <Badge
            v-for="metric in metrics"
            :key="metric.metric"
            variant="outline"
          >
            {{ metric.metric }} · {{ metric.state }} ·
            {{ metric.affectedCount }}
          </Badge>
        </div>
        <p v-else class="text-sm text-muted-foreground">
          No metric gaps reported — every required measurement is available.
        </p>
        <div
          v-if="stateCounts.length"
          class="mt-3 flex flex-wrap gap-2 text-xs"
        >
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

    <EmptyState
      v-if="diagnostics.isError.value"
      title="Feature diagnostics unavailable"
      description="The diagnostics endpoint could not be reached; readiness above uses the last known values."
    />
  </div>
</template>
