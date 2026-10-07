<script setup lang="ts">
import type { SplitterPanelEmits, SplitterPanelProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { reactiveOmit } from '@vueuse/core'
import { SplitterPanel, useForwardPropsEmits } from 'reka-ui'
import { cn } from '@/lib/utils'

const props = defineProps<
  SplitterPanelProps & { class?: HTMLAttributes['class'] }
>()
const emits = defineEmits<SplitterPanelEmits>()

const delegatedProps = reactiveOmit(props, 'class')
const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <SplitterPanel
    v-bind="forwarded"
    :class="cn('flex min-h-0 min-w-0 flex-col overflow-hidden', props.class)"
  >
    <slot />
  </SplitterPanel>
</template>
