<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ state: string | null }>()

const STEPS = ['WATCH', 'FORMING', 'NEAR_TRIGGER', 'VALID'] as const
const SHORT: Record<string, string> = {
  WATCH: 'WATCH',
  FORMING: 'FORM',
  NEAR_TRIGGER: 'NEAR',
  VALID: 'VALID',
}
const TERMINAL = ['INVALIDATED', 'EXPIRED', 'MISSED', 'BLOCKED']

const currentIndex = computed(() =>
  STEPS.indexOf((props.state ?? '') as (typeof STEPS)[number]),
)
const terminal = computed(() => TERMINAL.includes(props.state ?? ''))
</script>

<template>
  <div
    class="flex items-center gap-1 text-[10px] uppercase tracking-wide"
    :aria-label="`Setup lifecycle: ${state ?? 'none'}`"
  >
    <template v-if="terminal">
      <span class="rounded bg-muted px-1.5 py-0.5 text-muted-foreground">{{
        state
      }}</span>
    </template>
    <template v-else>
      <template v-for="(step, index) in STEPS" :key="step">
        <span
          class="rounded px-1 py-0.5 tabular-nums"
          :class="
            index === currentIndex
              ? 'bg-primary text-primary-foreground font-semibold'
              : index < currentIndex
                ? 'text-muted-foreground'
                : 'text-muted-foreground/50'
          "
        >
          {{ SHORT[step] }}
        </span>
        <span
          v-if="index < STEPS.length - 1"
          class="text-muted-foreground/40"
          aria-hidden="true"
          >›</span
        >
      </template>
    </template>
  </div>
</template>
