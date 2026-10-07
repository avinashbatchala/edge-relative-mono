<script setup lang="ts">
import { computed } from 'vue'
import type { FeatureDashboardRow } from '@/api/features'
import type { OpportunityRow } from '@/api/opportunities'
import type { SetupObservation } from '@/api/setups'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import DecisionGateList from '@/components/trade/DecisionGateList.vue'
import { decisionGates } from '@/lib/decision-gates'
import { riskStateMeta } from '@/lib/opportunity-presentation'

const props = defineProps<{
  feature: FeatureDashboardRow | null
  setup: SetupObservation | null
  opportunity: OpportunityRow | null
}>()

const gates = computed(() =>
  decisionGates({
    feature: props.feature,
    setup: props.setup,
    opportunity: props.opportunity,
  }),
)
const risk = computed(() =>
  props.opportunity ? riskStateMeta(props.opportunity.riskState) : null,
)
</script>

<template>
  <div class="grid gap-4 lg:grid-cols-3">
    <Card class="gap-0 py-0">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-sm">Strategy</CardTitle>
      </CardHeader>
      <CardContent class="border-t p-5">
        <DecisionGateList :gates="gates" />
        <p class="mt-2 text-[10px] text-muted-foreground">
          Derived from browser-visible measurements; authoritative gates live in
          the strategy engine.
        </p>
      </CardContent>
    </Card>

    <Card class="gap-0 py-0">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-sm">ML</CardTitle>
      </CardHeader>
      <CardContent class="border-t p-5 text-xs text-muted-foreground">
        <p>
          No ML model has authority. The deterministic strategy and risk engine
          decide alone.
        </p>
      </CardContent>
    </Card>

    <Card class="gap-0 py-0">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-sm">Risk</CardTitle>
      </CardHeader>
      <CardContent class="border-t p-5 text-xs">
        <div v-if="risk" class="flex items-center gap-2">
          <span :class="risk.tone" class="font-medium">{{ risk.label }}</span>
          <Badge variant="outline">{{ opportunity?.riskState }}</Badge>
        </div>
        <p v-else class="text-muted-foreground">
          Not evaluated for this instrument.
        </p>
        <p
          v-if="opportunity?.rejectionReason"
          class="mt-2 text-muted-foreground"
        >
          {{ opportunity.rejectionReason }}
        </p>
      </CardContent>
    </Card>
  </div>
</template>
