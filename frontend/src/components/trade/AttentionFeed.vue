<script setup lang="ts">
import type {
  AttentionEvent,
  AttentionSeverity,
} from '@/composables/useAttentionFeed'
import { formatAge } from '@/lib/format'

defineProps<{ events: AttentionEvent[] }>()

const TONE: Record<AttentionSeverity, string> = {
  INFO: 'text-muted-foreground',
  ATTENTION: 'text-amber-600 dark:text-amber-400',
  WARNING: 'text-negative',
}
const GLYPH: Record<AttentionSeverity, string> = {
  INFO: '·',
  ATTENTION: '△',
  WARNING: '!',
}
</script>

<template>
  <ul
    v-if="events.length"
    class="space-y-2 text-sm"
    data-testid="attention-feed"
  >
    <li
      v-for="event in events"
      :key="event.id"
      class="flex items-start justify-between gap-3"
    >
      <span class="flex min-w-0 items-start gap-2">
        <span
          class="w-3 shrink-0 tabular-nums"
          :class="TONE[event.severity]"
          aria-hidden="true"
          >{{ GLYPH[event.severity] }}</span
        >
        <span class="min-w-0">
          <RouterLink
            :to="{ name: 'market-ticker', params: { symbol: event.symbol } }"
            class="font-medium hover:underline"
          >
            {{ event.symbol }}
          </RouterLink>
          <span class="text-muted-foreground"> · {{ event.message }}</span>
        </span>
      </span>
      <span class="shrink-0 text-xs text-muted-foreground">{{
        formatAge(event.at)
      }}</span>
    </li>
  </ul>
  <p v-else class="text-sm text-muted-foreground">
    No meaningful changes since the last authoritative snapshot.
  </p>
</template>
