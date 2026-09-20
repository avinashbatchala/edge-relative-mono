<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { formatAge } from '@/lib/format'

const props = withDefaults(
  defineProps<{
    connection: string
    gapDetected?: boolean
    lastUpdatedAt?: string | null
    showAge?: boolean
  }>(),
  { gapDetected: false, lastUpdatedAt: null, showAge: true },
)

const meta = computed(() => {
  switch (props.connection) {
    case 'open':
      return {
        label: 'Live',
        dot: 'bg-positive',
        variant: 'secondary' as const,
      }
    case 'connecting':
      return {
        label: 'Connecting',
        dot: 'bg-amber-500',
        variant: 'outline' as const,
      }
    case 'reconnecting':
      return {
        label: 'Reconnecting',
        dot: 'bg-amber-500',
        variant: 'outline' as const,
      }
    case 'closed':
      return {
        label: 'Offline',
        dot: 'bg-negative',
        variant: 'outline' as const,
      }
    default:
      return {
        label: 'Idle',
        dot: 'bg-muted-foreground',
        variant: 'outline' as const,
      }
  }
})
</script>

<template>
  <div class="flex items-center gap-2" aria-live="polite">
    <Badge :variant="meta.variant" class="gap-1">
      <span
        class="size-1.5 rounded-full"
        :class="meta.dot"
        aria-hidden="true"
      />
      {{ meta.label }}
    </Badge>
    <Badge
      v-if="gapDetected"
      variant="outline"
      class="border-amber-500/40 text-amber-600 dark:text-amber-400"
    >
      Resyncing
    </Badge>
    <span v-if="showAge" class="hidden text-xs text-muted-foreground sm:inline">
      {{
        lastUpdatedAt ? `Updated ${formatAge(lastUpdatedAt)}` : 'No updates yet'
      }}
    </span>
  </div>
</template>
