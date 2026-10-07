<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Database, Radio, ShieldCheck } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
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
    return { label: 'Broker …', variant: 'outline' as const }
  }
  if (health.isError.value) {
    return { label: 'Broker unavailable', variant: 'destructive' as const }
  }
  return health.data.value?.status === 'UP'
    ? { label: 'Broker up', variant: 'secondary' as const }
    : { label: 'Broker down', variant: 'destructive' as const }
})

const streamState = computed(() => {
  switch (stream.connection) {
    case 'open':
      return { label: 'Stream live', tone: 'text-positive' }
    case 'connecting':
    case 'reconnecting':
      return { label: 'Stream connecting', tone: 'text-muted-foreground' }
    case 'closed':
      return { label: 'Stream offline', tone: 'text-negative' }
    default:
      return { label: 'Stream idle', tone: 'text-muted-foreground' }
  }
})

const dataAge = computed(() => formatAge(stream.lastUpdatedAt))
</script>

<template>
  <footer
    class="sticky bottom-0 z-10 flex h-8 items-center gap-3 border-t bg-background px-4 text-xs text-muted-foreground"
  >
    <Badge :variant="broker.variant" class="h-5">{{ broker.label }}</Badge>
    <span class="inline-flex items-center gap-1" :class="streamState.tone">
      <Radio class="size-3" aria-hidden="true" />
      {{ streamState.label }}
    </span>
    <span class="inline-flex items-center gap-1">
      <Database class="size-3" aria-hidden="true" />
      data {{ dataAge }}
    </span>
    <span class="ml-auto inline-flex items-center gap-1">
      <ShieldCheck class="size-3" aria-hidden="true" />
      Advisory · execution disabled
    </span>
  </footer>
</template>
