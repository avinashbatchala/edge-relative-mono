<script setup lang="ts">
import { computed } from 'vue'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { backtestFunnel, funnelReasons } from '@/lib/backtest-funnel'

const props = defineProps<{ stageCounts: Record<string, number> }>()

const stages = computed(() => backtestFunnel(props.stageCounts))
const reasons = computed(() => funnelReasons(props.stageCounts))
const maxCount = computed(() =>
  Math.max(1, ...stages.value.map((stage) => stage.count)),
)

function width(count: number): string {
  return `${Math.max(1, (count / maxCount.value) * 100).toFixed(1)}%`
}
</script>

<template>
  <div class="grid gap-4 lg:grid-cols-2">
    <Card class="gap-0 py-0">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-base">Decision funnel</CardTitle>
        <CardDescription>
          Where the strategy filtered opportunities — countable for zero-trade
          runs too.
        </CardDescription>
      </CardHeader>
      <CardContent class="border-t p-5">
        <ol class="space-y-1.5" data-testid="backtest-funnel">
          <li v-for="stage in stages" :key="stage.key" class="space-y-0.5">
            <div class="flex items-center justify-between text-xs">
              <span class="font-medium">{{ stage.label }}</span>
              <span class="tabular-nums">
                {{ stage.count.toLocaleString() }}
                <span
                  v-if="stage.eliminatedFromPrevious"
                  class="text-muted-foreground"
                >
                  (−{{ stage.eliminatedFromPrevious.toLocaleString() }})
                </span>
              </span>
            </div>
            <div class="h-1.5 w-full rounded bg-muted">
              <div
                class="h-1.5 rounded bg-primary"
                :style="{ width: width(stage.count) }"
              />
            </div>
          </li>
        </ol>
      </CardContent>
    </Card>

    <Card class="gap-0 py-0">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-base">Gate rejections</CardTitle>
        <CardDescription
          >Most frequent setup-gate reason codes (long +
          short).</CardDescription
        >
      </CardHeader>
      <CardContent class="border-t p-5">
        <ul v-if="reasons.length" class="space-y-1 text-xs">
          <li
            v-for="reason in reasons"
            :key="reason.code"
            class="flex justify-between"
          >
            <span>{{ reason.code }}</span>
            <span class="tabular-nums text-muted-foreground">{{
              reason.count.toLocaleString()
            }}</span>
          </li>
        </ul>
        <p v-else class="text-sm text-muted-foreground">
          No setup-gate rejections recorded for this run.
        </p>
      </CardContent>
    </Card>
  </div>
</template>
