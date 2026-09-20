<script setup lang="ts">
import { computed } from 'vue'
import { formatPrice, formatSigned, movementClass } from '@/lib/format'

const props = withDefaults(
  defineProps<{
    value: number | null | undefined
    signed?: boolean
    decimals?: number
    suffix?: string
    reason?: string | null
  }>(),
  { signed: false, decimals: 2, suffix: '', reason: null },
)

const unavailable = computed(
  () => props.value === null || props.value === undefined,
)

const direction = computed(() => {
  if (unavailable.value) {
    return 'none'
  }
  const value = props.value as number
  if (value > 0) {
    return 'up'
  }
  if (value < 0) {
    return 'down'
  }
  return 'flat'
})

const arrow = computed(() => {
  if (direction.value === 'up') {
    return '▲'
  }
  if (direction.value === 'down') {
    return '▼'
  }
  return '■'
})

const text = computed(() => {
  if (unavailable.value) {
    return '—'
  }
  const value = props.value as number
  const body = props.signed
    ? formatSigned(value, props.decimals)
    : formatPrice(value, props.decimals)
  return `${body}${props.suffix}`
})

const srLabel = computed(() =>
  unavailable.value
    ? (props.reason ?? 'unavailable')
    : `${direction.value} ${text.value}`,
)
</script>

<template>
  <span
    class="inline-flex items-center gap-1 font-medium tabular-nums"
    :class="unavailable ? 'text-muted-foreground' : movementClass(value)"
    :aria-label="srLabel"
    :title="unavailable ? (reason ?? 'unavailable') : undefined"
  >
    <span
      v-if="!unavailable"
      aria-hidden="true"
      class="text-[9px] leading-none"
    >
      {{ arrow }}
    </span>
    <span>{{ text }}</span>
  </span>
</template>
