<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import type { ProgressRootProps } from 'reka-ui'
import { computed } from 'vue'
import { reactiveOmit } from '@vueuse/core'
import { ProgressIndicator, ProgressRoot } from 'reka-ui'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<ProgressRootProps & { class?: HTMLAttributes['class'] }>(),
  { modelValue: 0 },
)

const delegatedProps = reactiveOmit(props, 'class')

const offset = computed(
  () => 100 - Math.min(100, Math.max(0, props.modelValue ?? 0)),
)
</script>

<template>
  <ProgressRoot
    data-slot="progress"
    v-bind="delegatedProps"
    :class="
      cn(
        'relative h-2 w-full overflow-hidden rounded-full bg-primary/20',
        props.class,
      )
    "
  >
    <ProgressIndicator
      data-slot="progress-indicator"
      class="bg-primary h-full w-full flex-1 transition-all"
      :style="`transform: translateX(-${offset}%);`"
    />
  </ProgressRoot>
</template>
