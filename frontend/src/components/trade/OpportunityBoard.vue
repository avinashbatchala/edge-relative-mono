<script setup lang="ts">
import { computed } from 'vue'
import type { FeatureDashboardRow } from '@/api/features'
import type { OpportunityRow } from '@/api/opportunities'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import SetupLifecycle from '@/components/trade/SetupLifecycle.vue'
import { formatAge } from '@/lib/format'
import { riskStateMeta } from '@/lib/opportunity-presentation'
import { rankOpportunities, type Alignment } from '@/lib/opportunity-rank'

const props = withDefaults(
  defineProps<{
    opportunities: OpportunityRow[]
    features?: FeatureDashboardRow[]
    loading?: boolean
    limit?: number
  }>(),
  { features: () => [], loading: false, limit: 20 },
)

const ranked = computed(() =>
  rankOpportunities(props.opportunities, props.features).slice(0, props.limit),
)

function num(value: number | null | undefined, digits = 2): string {
  return value === null || value === undefined ? '—' : value.toFixed(digits)
}

const ALIGN_LABEL: Record<Exclude<Alignment, null>, string> = {
  ALIGNED: 'Aligned',
  NEUTRAL: 'Mixed',
  OPPOSED: 'Opposed',
}
const ALIGN_TONE: Record<Exclude<Alignment, null>, string> = {
  ALIGNED: 'text-positive',
  NEUTRAL: 'text-muted-foreground',
  OPPOSED: 'text-negative',
}
</script>

<template>
  <Card class="gap-0 overflow-hidden py-0">
    <CardHeader class="px-5 py-4">
      <CardTitle class="text-base">Opportunity board</CardTitle>
      <CardDescription>
        Ranked by a deterministic rule (setup maturity → risk permission → RRS
        momentum → RVOL). Not a probabilistic score.
      </CardDescription>
    </CardHeader>
    <div class="overflow-x-auto border-t">
      <Table class="min-w-[1000px]">
        <TableHeader>
          <TableRow class="hover:bg-transparent">
            <TableHead class="w-10">#</TableHead>
            <TableHead>Symbol</TableHead>
            <TableHead>Dir</TableHead>
            <TableHead>Setup</TableHead>
            <TableHead>Align</TableHead>
            <TableHead class="text-right">RRS</TableHead>
            <TableHead class="text-right">Mom</TableHead>
            <TableHead class="text-right">RVOL</TableHead>
            <TableHead class="text-right">RVE</TableHead>
            <TableHead>Risk</TableHead>
            <TableHead>Plan</TableHead>
            <TableHead>Fresh</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="item in ranked" :key="item.row.instrumentId">
            <TableCell class="text-xs tabular-nums text-muted-foreground">{{
              item.rank
            }}</TableCell>
            <TableCell class="text-xs">
              <RouterLink
                :to="{
                  name: 'market-ticker',
                  params: { symbol: item.row.symbol },
                }"
                class="font-medium hover:underline"
              >
                {{ item.row.symbol }}
              </RouterLink>
            </TableCell>
            <TableCell class="text-xs">{{
              item.row.direction ?? '—'
            }}</TableCell>
            <TableCell>
              <SetupLifecycle :state="item.row.setupStatus" />
            </TableCell>
            <TableCell class="text-xs">
              <span v-if="item.alignment" :class="ALIGN_TONE[item.alignment]">
                {{ ALIGN_LABEL[item.alignment] }}
              </span>
              <span v-else class="text-muted-foreground">—</span>
            </TableCell>
            <TableCell
              class="text-right text-xs tabular-nums"
              :class="
                (item.feature?.rrsRaw ?? 0) >= 0
                  ? 'text-positive'
                  : 'text-negative'
              "
            >
              {{ num(item.feature?.rrsRaw) }}
            </TableCell>
            <TableCell
              class="text-right text-xs tabular-nums"
              :class="
                item.momentum === null
                  ? 'text-muted-foreground'
                  : item.momentum >= 0
                    ? 'text-positive'
                    : 'text-negative'
              "
            >
              {{ num(item.momentum) }}
            </TableCell>
            <TableCell class="text-right text-xs tabular-nums">
              {{ num(item.feature?.rvolInterval) }}
            </TableCell>
            <TableCell class="text-right text-xs tabular-nums">
              {{ num(item.feature?.rve) }}
            </TableCell>
            <TableCell class="text-xs">
              <span :class="riskStateMeta(item.row.riskState).tone">
                {{ riskStateMeta(item.row.riskState).label }}
              </span>
            </TableCell>
            <TableCell class="text-xs">
              <Badge v-if="item.row.planEligibilityStatus" variant="outline">
                {{ item.row.planEligibilityStatus }}
              </Badge>
              <span v-else class="text-muted-foreground">—</span>
            </TableCell>
            <TableCell class="text-xs text-muted-foreground">
              {{
                item.feature?.observationTime
                  ? formatAge(item.feature.observationTime)
                  : '—'
              }}
            </TableCell>
          </TableRow>
          <TableRow v-if="ranked.length === 0">
            <TableCell
              colspan="12"
              class="py-8 text-center text-sm text-muted-foreground"
            >
              No setups. No watchlist instrument has produced a setup
              observation yet.
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  </Card>
</template>
