<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { getHealth, systemKeys } from '@/api/system'
import { formatAge } from '@/lib/format'
import { useFeatureStreamStore } from '@/stores/feature-stream'

const stream = useFeatureStreamStore()

const health = useQuery(() => ({
  queryKey: systemKeys.health(),
  queryFn: ({ signal }) => getHealth(signal),
  refetchInterval: 30_000,
  retry: false,
}))

const broker = computed(() => {
  if (health.isPending.value) {
    return { label: 'Checking', variant: 'outline' as const }
  }
  if (health.isError.value) {
    return { label: 'Unavailable', variant: 'destructive' as const }
  }
  return health.data.value?.status === 'UP'
    ? { label: 'Up', variant: 'secondary' as const }
    : { label: 'Down', variant: 'destructive' as const }
})

const streamLabel = computed(() => stream.connection)
</script>

<template>
  <Card>
    <CardHeader class="pb-3">
      <CardDescription>System</CardDescription>
      <CardTitle class="text-2xl">
        <Badge :variant="broker.variant">Broker · {{ broker.label }}</Badge>
      </CardTitle>
    </CardHeader>
    <CardContent class="space-y-1 text-xs text-muted-foreground">
      <div class="flex justify-between">
        <span>Stream</span><span>{{ streamLabel }}</span>
      </div>
      <div class="flex justify-between">
        <span>Data age</span><span>{{ formatAge(stream.lastUpdatedAt) }}</span>
      </div>
      <div class="flex justify-between">
        <span>Execution</span><span>Disabled (advisory)</span>
      </div>
    </CardContent>
  </Card>
</template>
