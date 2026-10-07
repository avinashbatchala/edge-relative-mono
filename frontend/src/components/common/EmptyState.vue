<script setup lang="ts">
import type { Component } from 'vue'

withDefaults(
  defineProps<{
    icon?: Component
    title: string
    description?: string
  }>(),
  { icon: undefined, description: '' },
)
</script>

<template>
  <div
    class="flex flex-col items-center justify-center gap-2 rounded-md border border-dashed px-6 py-12 text-center"
  >
    <div
      v-if="icon"
      class="grid size-10 place-items-center rounded-full bg-muted"
    >
      <component
        :is="icon"
        class="size-4 text-muted-foreground"
        aria-hidden="true"
      />
    </div>
    <p class="text-sm font-medium">{{ title }}</p>
    <p v-if="description" class="max-w-md text-sm text-muted-foreground">
      {{ description }}
    </p>
    <div v-if="$slots.default" class="mt-2 flex items-center gap-2">
      <slot />
    </div>
  </div>
</template>
