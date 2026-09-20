<script setup lang="ts">
import { computed } from 'vue'
import {
  CircleCheck,
  CircleDashed,
  CircleHelp,
  CircleX,
  Clock,
  MinusCircle,
  TriangleAlert,
} from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { featureStateMeta } from '@/lib/feature-presentation'

const props = defineProps<{ state: string; reason?: string | null }>()

const ICONS = {
  HEALTHY: CircleCheck,
  WARMING_UP: CircleDashed,
  STALE: Clock,
  DEGRADED: TriangleAlert,
  INVALID: CircleX,
  UNAVAILABLE: MinusCircle,
} as const

const ICON_TONES: Record<string, string> = {
  HEALTHY: 'text-emerald-600 dark:text-emerald-500',
  WARMING_UP: 'text-sky-600 dark:text-sky-500',
  STALE: 'text-amber-600 dark:text-amber-500',
  DEGRADED: 'text-orange-600 dark:text-orange-500',
  INVALID: 'text-destructive',
  UNAVAILABLE: 'text-muted-foreground',
}

const meta = computed(() => featureStateMeta(props.state))
const icon = computed(
  () => ICONS[props.state as keyof typeof ICONS] ?? CircleHelp,
)
const iconTone = computed(
  () => ICON_TONES[props.state] ?? 'text-muted-foreground',
)
</script>

<template>
  <Badge
    variant="outline"
    class="gap-1 px-1.5 font-normal text-muted-foreground"
    :title="reason ?? meta.description"
    :aria-label="`Feature state: ${meta.label}${reason ? `, ${reason}` : ''}`"
  >
    <component :is="icon" class="size-3" :class="iconTone" aria-hidden="true" />
    {{ meta.label }}
  </Badge>
</template>
