<script setup lang="ts">
import { computed } from 'vue'
import { ShieldCheck } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'

/**
 * Trading-mode badge. The backend has no current-mode endpoint yet, so the mode is a derived display
 * and is labelled as such. It never implies order authority: execution is disabled in this product.
 */
const props = withDefaults(
  defineProps<{ mode?: string; derived?: boolean }>(),
  {
    mode: 'OBSERVE',
    derived: true,
  },
)

const LABELS: Record<string, string> = {
  RESEARCH: 'Research',
  BACKTEST: 'Backtest',
  OBSERVE: 'Observe',
  SHADOW: 'Shadow',
  PAPER: 'Paper',
  ASSISTED_LIVE: 'Assisted live',
  GUARDED_AUTOPILOT: 'Guarded autopilot',
}

const label = computed(() => LABELS[props.mode] ?? props.mode)
</script>

<template>
  <Tooltip>
    <TooltipTrigger as-child>
      <Badge
        variant="outline"
        class="gap-1"
        data-testid="trading-mode"
        :aria-label="`Trading mode: ${label}${derived ? ' (derived)' : ''}`"
      >
        <ShieldCheck class="size-3" aria-hidden="true" />
        <span>{{ label }}</span>
        <span v-if="derived" class="text-muted-foreground">· derived</span>
      </Badge>
    </TooltipTrigger>
    <TooltipContent>
      <p class="max-w-xs text-xs">
        Derived from current configuration — the backend does not yet expose an
        authoritative mode. Execution remains disabled; this is advisory only.
      </p>
    </TooltipContent>
  </Tooltip>
</template>
