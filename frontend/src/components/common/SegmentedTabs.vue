<script setup lang="ts" generic="T extends string">
defineProps<{
  modelValue: T
  tabs: readonly { value: T; label: string }[]
  ariaLabel?: string
  capitalize?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [value: T] }>()
</script>

<template>
  <div
    class="inline-flex rounded-lg border p-0.5"
    role="tablist"
    :aria-label="ariaLabel"
  >
    <button
      v-for="item in tabs"
      :key="item.value"
      type="button"
      role="tab"
      :aria-selected="modelValue === item.value"
      class="rounded-md px-3 py-1.5 text-sm font-medium focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
      :class="[
        modelValue === item.value ? 'bg-muted' : 'text-muted-foreground',
        capitalize ? 'capitalize' : '',
      ]"
      @click="emit('update:modelValue', item.value)"
    >
      {{ item.label }}
    </button>
  </div>
</template>
