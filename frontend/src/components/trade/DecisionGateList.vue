<script setup lang="ts">
import type { DecisionGate, GateStatus } from '@/lib/decision-gates'

defineProps<{ gates: DecisionGate[] }>()

const GLYPH: Record<GateStatus, string> = {
  PASS: '✓',
  FAIL: '✕',
  WAITING: '○',
  NOT_EVALUATED: '—',
}
const TONE: Record<GateStatus, string> = {
  PASS: 'text-positive',
  FAIL: 'text-negative',
  WAITING: 'text-amber-600 dark:text-amber-400',
  NOT_EVALUATED: 'text-muted-foreground',
}
</script>

<template>
  <ul class="space-y-1 text-xs" data-testid="decision-gates">
    <li v-for="gate in gates" :key="gate.code" class="flex items-start gap-2">
      <span
        class="w-3 shrink-0 tabular-nums"
        :class="TONE[gate.status]"
        aria-hidden="true"
        >{{ GLYPH[gate.status] }}</span
      >
      <span class="min-w-0 flex-1">
        <span class="font-medium">{{ gate.label }}</span>
        <span class="text-muted-foreground"> — {{ gate.detail }}</span>
      </span>
    </li>
  </ul>
</template>
