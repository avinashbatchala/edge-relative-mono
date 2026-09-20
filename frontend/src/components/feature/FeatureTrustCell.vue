<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from '@/components/ui/hover-card'
import { featureStateMeta } from '@/lib/feature-presentation'
import FeatureStateBadge from './FeatureStateBadge.vue'

const props = defineProps<{
  state: string
  reason?: string | null
  metrics: string[]
}>()

const meta = computed(() => featureStateMeta(props.state))
</script>

<template>
  <HoverCard :open-delay="150" :close-delay="100">
    <HoverCardTrigger as-child>
      <FeatureStateBadge :state="state" :reason="reason" />
    </HoverCardTrigger>
    <HoverCardContent align="start" class="w-72 space-y-3 text-xs">
      <div class="space-y-1">
        <div class="flex items-center gap-2">
          <FeatureStateBadge :state="state" />
        </div>
        <p class="text-muted-foreground">{{ meta.description }}</p>
      </div>
      <div class="space-y-1">
        <p class="text-muted-foreground">Reason</p>
        <p class="font-medium text-foreground">
          {{ reason ?? 'Required inputs are present and trustworthy' }}
        </p>
      </div>
      <div v-if="metrics.length" class="space-y-1">
        <p class="text-muted-foreground">Unavailable metrics</p>
        <div class="flex flex-wrap gap-1">
          <Badge
            v-for="metric in metrics"
            :key="metric"
            variant="outline"
            class="font-normal text-muted-foreground"
          >
            {{ metric }}
          </Badge>
        </div>
      </div>
    </HoverCardContent>
  </HoverCard>
</template>
