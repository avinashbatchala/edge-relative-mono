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
 * Trading-mode badge. The mode comes from the authoritative `/api/v1/system/mode` endpoint; when
 * that is unreachable the caller passes `derived` so the badge says so. Execution remains disabled
 * until the persisted control state enables it, so this never implies order authority.
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
        <template v-if="derived">
          Derived from current configuration — the backend mode endpoint is
          unreachable. Execution remains disabled; this is advisory only.
        </template>
        <template v-else>
          Authoritative declared mode from the backend. Execution remains
          disabled until the persisted control state enables it.
        </template>
      </p>
    </TooltipContent>
  </Tooltip>
</template>
