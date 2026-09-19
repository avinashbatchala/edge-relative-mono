<script setup lang="ts">
import { computed, ref } from 'vue'
import { useIntervalFn } from '@vueuse/core'
import { formatAge, formatIstTime } from '@/lib/format'

const props = defineProps<{
  /** Epoch milliseconds of the last successful update, from the query cache. */
  updatedAt?: number | null
  label?: string
}>()

const now = ref(Date.now())
useIntervalFn(
  () => {
    now.value = Date.now()
  },
  1000,
  { immediateCallback: true },
)

const age = computed(() =>
  props.updatedAt
    ? formatAge(new Date(props.updatedAt).toISOString(), now.value)
    : null,
)
const clock = computed(() =>
  props.updatedAt
    ? formatIstTime(new Date(props.updatedAt).toISOString())
    : null,
)
</script>

<template>
  <p
    class="flex items-center gap-1.5 text-xs text-muted-foreground tabular-nums"
    :aria-live="'polite'"
  >
    <span
      class="size-1.5 rounded-full"
      :class="updatedAt ? 'bg-[var(--positive)]' : 'bg-muted-foreground/40'"
      aria-hidden="true"
    />
    <template v-if="updatedAt">
      <span>{{ label ?? 'Updated' }} {{ age }}</span>
      <span class="text-muted-foreground/60">·</span>
      <span>{{ clock }} IST</span>
    </template>
    <template v-else>
      <span>{{ label ?? 'Awaiting data' }}</span>
    </template>
  </p>
</template>
