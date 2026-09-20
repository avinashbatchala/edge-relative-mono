<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'

const props = defineProps<{ state: string; reason?: string | null }>()

const LABELS: Record<string, string> = {
  HEALTHY: 'Healthy',
  WARMING_UP: 'Warming up',
  STALE: 'Stale',
  DEGRADED: 'Degraded',
  INVALID: 'Invalid',
  UNAVAILABLE: 'Unavailable',
}

const GLYPHS: Record<string, string> = {
  HEALTHY: '●',
  WARMING_UP: '◐',
  STALE: '◌',
  DEGRADED: '▲',
  INVALID: '✕',
  UNAVAILABLE: '—',
}

const TONES: Record<string, string> = {
  HEALTHY: 'border-positive/40 text-positive',
  WARMING_UP: 'border-amber-500/40 text-amber-600 dark:text-amber-400',
  STALE: 'border-amber-500/40 text-amber-600 dark:text-amber-400',
  DEGRADED: 'border-orange-500/40 text-orange-600 dark:text-orange-400',
  INVALID: 'border-negative/40 text-negative',
  UNAVAILABLE: 'border-muted-foreground/40 text-muted-foreground',
}

const label = computed(() => LABELS[props.state] ?? props.state)
const glyph = computed(() => GLYPHS[props.state] ?? '•')
const tone = computed(
  () =>
    TONES[props.state] ?? 'border-muted-foreground/40 text-muted-foreground',
)
</script>

<template>
  <Badge
    variant="outline"
    class="gap-1 whitespace-nowrap font-medium"
    :class="tone"
    :title="reason ?? undefined"
    :aria-label="`Feature state: ${label}${reason ? `, ${reason}` : ''}`"
  >
    <span aria-hidden="true">{{ glyph }}</span>
    <span>{{ label }}</span>
  </Badge>
</template>
