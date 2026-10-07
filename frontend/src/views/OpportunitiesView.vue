<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ExternalLink, FileText } from '@lucide/vue'
import {
  getOpportunities,
  opportunityKeys,
  type OpportunityRow,
} from '@/api/opportunities'
import { getTradePlan, tradePlanKeys, type TradePlan } from '@/api/trade-plans'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import FeatureDashboardView from '@/views/FeatureDashboardView.vue'
import SegmentedTabs from '@/components/common/SegmentedTabs.vue'
import { formatIstDateTime, formatPrice } from '@/lib/format'
import {
  directionLabel,
  planStateMeta,
  riskStateMeta,
  setupStateMeta,
} from '@/lib/opportunity-presentation'

const tab = ref<'setups' | 'features'>('setups')
const tabs = [
  { value: 'setups', label: 'Setups' },
  { value: 'features', label: 'Features' },
] as const

const opportunitiesQuery = useQuery(() => ({
  queryKey: opportunityKeys.list(),
  queryFn: ({ signal }) => getOpportunities(signal),
  staleTime: 15_000,
}))

const rows = computed<OpportunityRow[]>(
  () => opportunitiesQuery.data.value ?? [],
)
const selectedPlanKey = ref<string | null>(null)

const planQuery = useQuery(() => ({
  queryKey: tradePlanKeys.detail(selectedPlanKey.value ?? ''),
  queryFn: ({ signal }) =>
    getTradePlan(selectedPlanKey.value as string, signal),
  enabled: selectedPlanKey.value !== null,
}))

const plan = computed<TradePlan | null>(() => planQuery.data.value ?? null)

function openPlan(row: OpportunityRow) {
  if (row.planKey) {
    selectedPlanKey.value = row.planKey
  }
}

function featureHref(symbol: string): string {
  return `/features/${symbol}`
}
</script>

<template>
  <div class="flex flex-1 flex-col gap-4 p-4 lg:p-6">
    <header class="space-y-2">
      <h1 class="text-xl font-semibold tracking-tight">Opportunities</h1>
      <p class="max-w-3xl text-sm text-muted-foreground">
        Setups and features for the active watchlist. A valid setup is
        strategy-qualified, not risk-approved; risk and plan state are shown
        separately.
      </p>
      <SegmentedTabs
        v-model="tab"
        :tabs="tabs"
        aria-label="Opportunity views"
      />
    </header>

    <div v-show="tab === 'features'">
      <FeatureDashboardView embedded />
    </div>

    <Card v-show="tab === 'setups'" class="gap-0 overflow-hidden py-0">
      <CardHeader class="px-5 py-5">
        <CardTitle class="text-base">Setup and plan state</CardTitle>
        <CardDescription>
          Qualification, risk outcome, and plan eligibility are distinct.
        </CardDescription>
      </CardHeader>

      <div
        v-if="opportunitiesQuery.isPending.value"
        class="border-t p-5 text-sm text-muted-foreground"
        role="status"
      >
        Loading opportunities…
      </div>
      <div
        v-else-if="opportunitiesQuery.isError.value"
        class="border-t p-5 text-sm text-muted-foreground"
        role="alert"
      >
        Opportunities are unavailable.
      </div>

      <div
        v-else
        class="overflow-x-auto border-t"
        data-testid="opportunities-setups"
      >
        <Table class="min-w-[880px]">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead>Instrument</TableHead>
              <TableHead>Setup</TableHead>
              <TableHead>Risk</TableHead>
              <TableHead>Plan</TableHead>
              <TableHead>Validity</TableHead>
              <TableHead class="w-[90px] text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="row in rows" :key="row.instrumentId">
              <TableCell>
                <a
                  :href="featureHref(row.symbol)"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="font-medium underline-offset-4 hover:underline"
                >
                  {{ row.symbol }}
                </a>
                <span class="block text-[10px] text-muted-foreground">
                  {{ row.exchange }} · {{ row.timeframe }} ·
                  {{ directionLabel(row.direction) }}
                </span>
              </TableCell>
              <TableCell>
                <span
                  class="text-xs"
                  :class="setupStateMeta(row.setupStatus).tone"
                  :data-testid="`setup-state-${row.symbol}`"
                >
                  {{ setupStateMeta(row.setupStatus).glyph }}
                  {{ setupStateMeta(row.setupStatus).label }}
                </span>
                <span
                  v-if="row.setupFamily"
                  class="block text-[10px] text-muted-foreground"
                >
                  {{ row.setupFamily }}
                </span>
              </TableCell>
              <TableCell>
                <span
                  class="text-xs"
                  :class="riskStateMeta(row.riskState).tone"
                  :data-testid="`risk-state-${row.symbol}`"
                >
                  {{ riskStateMeta(row.riskState).glyph }}
                  {{ riskStateMeta(row.riskState).label }}
                </span>
                <span
                  v-if="row.rejectionReason"
                  class="block text-[10px] text-muted-foreground"
                >
                  {{ row.rejectionReason }}
                </span>
              </TableCell>
              <TableCell>
                <span
                  v-if="row.planKey"
                  class="text-xs"
                  :class="planStateMeta(row.planEligibilityStatus).tone"
                  :data-testid="`plan-state-${row.symbol}`"
                >
                  {{ planStateMeta(row.planEligibilityStatus).glyph }}
                  {{ planStateMeta(row.planEligibilityStatus).label }}
                </span>
                <span v-else class="text-xs text-muted-foreground">—</span>
              </TableCell>
              <TableCell class="text-xs text-muted-foreground">
                <template v-if="row.planExpiresAt">
                  Expires {{ formatIstDateTime(row.planExpiresAt) }} IST
                </template>
                <template v-else>—</template>
              </TableCell>
              <TableCell class="text-right">
                <Button
                  v-if="row.planKey"
                  variant="outline"
                  size="sm"
                  :data-testid="`view-plan-${row.symbol}`"
                  @click="openPlan(row)"
                >
                  <FileText class="mr-1 size-3.5" aria-hidden="true" /> View
                  plan
                </Button>
              </TableCell>
            </TableRow>
            <TableRow v-if="rows.length === 0">
              <TableCell
                colspan="6"
                class="py-8 text-center text-sm text-muted-foreground"
              >
                No active watchlist instruments.
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </Card>

    <Sheet
      :open="selectedPlanKey !== null"
      @update:open="(open: boolean) => !open && (selectedPlanKey = null)"
    >
      <SheetContent class="w-full overflow-y-auto sm:max-w-2xl">
        <SheetHeader>
          <SheetTitle>
            {{ plan?.symbol }} plan · {{ directionLabel(plan?.direction) }}
          </SheetTitle>
          <SheetDescription>
            Frozen approved intent. Current market values are not substituted
            here; open the linked feature observation for live measurements.
          </SheetDescription>
        </SheetHeader>

        <div
          v-if="plan"
          class="mt-4 space-y-5 text-sm"
          data-testid="plan-detail"
        >
          <!-- 1. Instrument, direction, setup, eligibility, validity -->
          <section class="space-y-1">
            <h3 class="text-sm font-medium">Plan and validity</h3>
            <div class="flex flex-wrap items-center gap-2">
              <Badge variant="outline">{{ plan.riskDecision }}</Badge>
              <span
                class="text-xs"
                :class="planStateMeta(plan.eligibility.status).tone"
              >
                {{ planStateMeta(plan.eligibility.status).glyph }}
                {{ planStateMeta(plan.eligibility.status).label }}
              </span>
            </div>
            <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Valid from</dt>
                <dd>{{ formatIstDateTime(plan.validFrom) }} IST</dd>
              </div>
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Expires</dt>
                <dd>
                  {{
                    plan.expiresAt
                      ? `${formatIstDateTime(plan.expiresAt)} IST`
                      : 'No expiry policy configured'
                  }}
                </dd>
              </div>
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Entry cutoff</dt>
                <dd>
                  {{
                    plan.entryCutoffAt
                      ? `${formatIstDateTime(plan.entryCutoffAt)} IST`
                      : '—'
                  }}
                </dd>
              </div>
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Setup observation</dt>
                <dd>
                  <a
                    :href="featureHref(plan.symbol)"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="inline-flex items-center gap-1 underline-offset-4 hover:underline"
                  >
                    #{{ plan.setupObservationId }}
                    <ExternalLink class="size-3" aria-hidden="true" />
                  </a>
                </dd>
              </div>
            </dl>
          </section>

          <!-- 2. Entry, stop, targets, quantity -->
          <section class="space-y-1 border-t pt-4">
            <h3 class="text-sm font-medium">Entry, stop, targets, quantity</h3>
            <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Entry</dt>
                <dd>
                  {{ formatPrice(plan.entryLow) }}
                  <template v-if="plan.entryHigh !== plan.entryLow">
                    – {{ formatPrice(plan.entryHigh) }}
                  </template>
                </dd>
              </div>
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Protective stop</dt>
                <dd>{{ formatPrice(plan.protectiveStop) }}</dd>
              </div>
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Structural invalidation</dt>
                <dd>
                  {{ formatPrice(plan.structuralInvalidation) }}
                  <span
                    v-if="plan.invalidationReason"
                    class="text-muted-foreground"
                  >
                    ({{ plan.invalidationReason }})
                  </span>
                </dd>
              </div>
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Target</dt>
                <dd>
                  {{
                    plan.targetReference !== null
                      ? formatPrice(plan.targetReference)
                      : `${plan.targetMethod} (no fixed price)`
                  }}
                </dd>
              </div>
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Quantity</dt>
                <dd>
                  {{ plan.plannedQuantity }} /
                  {{ plan.approvedQuantityCeiling }}
                  approved
                </dd>
              </div>
            </dl>
          </section>

          <!-- 3. Risk and approved limits -->
          <section class="space-y-1 border-t pt-4">
            <h3 class="text-sm font-medium">Planned risk and limits</h3>
            <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Planned risk</dt>
                <dd>
                  {{ formatPrice(plan.plannedRisk) }} /
                  {{ formatPrice(plan.approvedRiskCeiling) }} approved
                </dd>
              </div>
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Planned notional</dt>
                <dd>
                  {{ formatPrice(plan.plannedNotional) }} /
                  {{ formatPrice(plan.approvedNotionalCeiling) }} approved
                </dd>
              </div>
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Maximum planned loss</dt>
                <dd>{{ formatPrice(plan.maximumPlannedLoss) }} (estimate)</dd>
              </div>
              <div class="flex justify-between gap-2">
                <dt class="text-muted-foreground">Reward/risk</dt>
                <dd>
                  {{
                    plan.expectedRewardRisk !== null
                      ? plan.expectedRewardRisk.toFixed(2)
                      : 'Not asserted'
                  }}
                </dd>
              </div>
            </dl>
          </section>

          <!-- 4. Why this trade -->
          <section class="space-y-1 border-t pt-4">
            <h3 class="text-sm font-medium">Why this trade</h3>
            <div class="flex flex-wrap gap-1">
              <Badge
                v-for="reason in plan.reasonCodes"
                :key="reason"
                variant="outline"
                class="font-normal"
              >
                {{ reason }}
              </Badge>
            </div>
            <p v-if="plan.explanation" class="text-xs text-muted-foreground">
              {{ plan.explanation }}
            </p>
            <p v-if="plan.noChaseBasis" class="text-xs text-muted-foreground">
              No-chase: {{ plan.noChaseBasis }}
              <template v-if="plan.noChasePrice !== null">
                (limit {{ formatPrice(plan.noChasePrice) }})
              </template>
            </p>
          </section>

          <!-- 5. Lineage and technical details -->
          <section class="border-t pt-4">
            <details class="text-xs">
              <summary class="cursor-pointer font-medium">
                Lineage and versions
              </summary>
              <dl class="mt-2 grid grid-cols-2 gap-x-4 gap-y-1">
                <div class="flex justify-between gap-2">
                  <dt class="text-muted-foreground">Strategy</dt>
                  <dd>{{ plan.strategyId }} {{ plan.strategyVersion }}</dd>
                </div>
                <div class="flex justify-between gap-2">
                  <dt class="text-muted-foreground">Decision key</dt>
                  <dd class="truncate">{{ plan.decisionKey }}</dd>
                </div>
                <div class="flex justify-between gap-2">
                  <dt class="text-muted-foreground">Setup instance</dt>
                  <dd class="truncate">{{ plan.setupInstanceId ?? '—' }}</dd>
                </div>
                <div class="flex justify-between gap-2">
                  <dt class="text-muted-foreground">Policy</dt>
                  <dd>{{ plan.policyReference ?? '—' }}</dd>
                </div>
              </dl>
            </details>
          </section>
        </div>
        <div v-else class="mt-4 text-sm text-muted-foreground">
          Loading plan…
        </div>
      </SheetContent>
    </Sheet>
  </div>
</template>
