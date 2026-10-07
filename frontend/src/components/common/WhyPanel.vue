<script setup lang="ts">
import { Badge } from '@/components/ui/badge'
import { WHY_TONE_CLASS, type WhySection } from '@/lib/why'

withDefaults(
  defineProps<{
    sections: WhySection[]
    note?: string
  }>(),
  { note: '' },
)
</script>

<template>
  <div class="space-y-4">
    <section v-for="section in sections" :key="section.title" class="space-y-2">
      <h4
        class="text-xs font-medium tracking-wide text-muted-foreground uppercase"
      >
        {{ section.title }}
      </h4>
      <ul v-if="section.items.length" class="space-y-1.5">
        <li
          v-for="item in section.items"
          :key="item.label"
          class="flex flex-wrap items-center gap-2 text-sm"
        >
          <Badge
            variant="outline"
            :class="WHY_TONE_CLASS[item.tone ?? 'default']"
          >
            {{ item.label }}
          </Badge>
          <span v-if="item.detail" class="text-muted-foreground">
            {{ item.detail }}
          </span>
        </li>
      </ul>
      <p v-else class="text-sm text-muted-foreground">Nothing recorded.</p>
    </section>
    <p v-if="note" class="text-xs text-muted-foreground">{{ note }}</p>
  </div>
</template>
