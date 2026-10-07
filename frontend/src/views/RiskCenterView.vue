<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { TriangleAlert } from '@lucide/vue'
import { getOpportunities, opportunityKeys } from '@/api/opportunities'
import { getRiskPosture, riskKeys } from '@/api/risk'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
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
import EmptyState from '@/components/common/EmptyState.vue'
import { formatInr } from '@/lib/format'

const opportunities = useQuery({
  queryKey: opportunityKeys.list(),
  queryFn: ({ signal }) => getOpportunities(signal),
})

const postureQuery = useQuery({
  queryKey: riskKeys.posture(),
  queryFn: ({ signal }) => getRiskPosture(signal),
  refetchInterval: 30000,
})

const posture = computed(() => postureQuery.data.value ?? null)
</script>

<template>
  <div class="flex flex-col gap-4 p-4 lg:p-6">
    <header class="space-y-1">
      <h1 class="text-xl font-semibold tracking-tight">Risk center</h1>
      <p class="text-sm text-muted-foreground">
        Risk has authority above opportunity. Portfolio/account capacity is read
        from persisted authoritative state; per-candidate decisions are shown
        below. Read-only — this screen grants no authority.
      </p>
    </header>

    <Card
      v-if="posture?.portfolioAvailable"
      class="gap-0 py-0"
      data-testid="risk-portfolio"
    >
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-base">Portfolio</CardTitle>
        <CardDescription>
          Marked-to-market snapshot as of
          {{ posture.portfolio?.snapshotAt ?? '—' }}.
        </CardDescription>
      </CardHeader>
      <CardContent class="border-t p-5">
        <div class="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          <div>
            <p class="text-xs text-muted-foreground">Net liquidation value</p>
            <p class="text-lg font-semibold tabular-nums">
              {{ formatInr(posture.portfolio?.netLiquidationValue) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">Available cash</p>
            <p class="text-lg font-semibold tabular-nums">
              {{ formatInr(posture.portfolio?.availableCash) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">Buying power</p>
            <p class="text-lg font-semibold tabular-nums">
              {{ formatInr(posture.portfolio?.buyingPower) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">Margin used</p>
            <p class="text-lg font-semibold tabular-nums">
              {{ formatInr(posture.portfolio?.marginUsed) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">Gross exposure</p>
            <p class="text-sm tabular-nums">
              {{ formatInr(posture.portfolio?.grossExposure) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">Net exposure</p>
            <p class="text-sm tabular-nums">
              {{ formatInr(posture.portfolio?.netExposure) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">Open risk</p>
            <p class="text-sm tabular-nums">
              {{ formatInr(posture.portfolio?.openRisk) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">Stress open risk</p>
            <p class="text-sm tabular-nums">
              {{ formatInr(posture.portfolio?.stressOpenRisk) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">
              Session realized P&amp;L
            </p>
            <p class="text-sm tabular-nums">
              {{ formatInr(posture.portfolio?.realizedSessionPnl) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">Unrealized P&amp;L</p>
            <p class="text-sm tabular-nums">
              {{ formatInr(posture.portfolio?.unrealizedPnl) }}
            </p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">Open positions</p>
            <p class="text-sm tabular-nums">
              {{ posture.portfolio?.openPositions ?? '—' }}
            </p>
          </div>
        </div>
      </CardContent>
    </Card>

    <Card v-else class="border-amber-500/40" data-testid="risk-unavailable">
      <CardHeader class="flex-row items-start gap-3">
        <TriangleAlert
          class="mt-0.5 size-5 text-amber-600 dark:text-amber-400"
          aria-hidden="true"
        />
        <div>
          <CardTitle class="text-base"
            >Portfolio risk context unavailable</CardTitle
          >
          <CardDescription class="mt-1">
            The live portfolio/account producer is not wired, so net liquidation
            value, P&amp;L, drawdown and capacity are not produced. The engine
            fails closed without context, so no trade can be approved. This
            screen populates automatically once the producer exists.
          </CardDescription>
          <ul
            v-if="posture?.unavailable.length"
            class="mt-2 list-disc pl-4 text-xs text-muted-foreground"
          >
            <li v-for="reason in posture.unavailable" :key="reason">
              {{ reason }}
            </li>
          </ul>
        </div>
      </CardHeader>
    </Card>

    <div class="grid gap-4 lg:grid-cols-2">
      <Card class="gap-0 py-0">
        <CardHeader class="px-5 py-4">
          <CardTitle class="text-base">Account risk state</CardTitle>
          <CardDescription
            >Risk-owned ledger (reserved/open risk, drawdown).</CardDescription
          >
        </CardHeader>
        <CardContent class="border-t p-5">
          <div
            v-if="posture?.accountRiskAvailable"
            class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm"
          >
            <span class="text-muted-foreground">State</span>
            <span
              class="font-medium"
              :class="
                posture.account?.riskState === 'NORMAL'
                  ? 'text-positive'
                  : 'text-amber-600 dark:text-amber-400'
              "
              >{{ posture.account?.riskState }}</span
            >
            <span class="text-muted-foreground">Risk reference equity</span>
            <span class="tabular-nums">{{
              formatInr(posture.account?.riskReferenceEquity)
            }}</span>
            <span class="text-muted-foreground">Reserved risk</span>
            <span class="tabular-nums">{{
              formatInr(posture.account?.reservedRisk)
            }}</span>
            <span class="text-muted-foreground">Open risk</span>
            <span class="tabular-nums">{{
              formatInr(posture.account?.openRisk)
            }}</span>
            <span class="text-muted-foreground">Stress open risk</span>
            <span class="tabular-nums">{{
              formatInr(posture.account?.stressOpenRisk)
            }}</span>
            <span class="text-muted-foreground">Session drawdown</span>
            <span class="tabular-nums">{{
              formatInr(posture.account?.sessionDrawdown)
            }}</span>
          </div>
          <p v-else class="text-sm text-muted-foreground">
            No authoritative risk account state exists for this session.
          </p>
        </CardContent>
      </Card>

      <Card class="gap-0 py-0">
        <CardHeader class="px-5 py-4">
          <CardTitle class="text-base">Controls &amp; exposure count</CardTitle>
          <CardDescription
            >Persisted safety switches and open work.</CardDescription
          >
        </CardHeader>
        <CardContent class="border-t p-5">
          <div class="flex flex-wrap gap-2">
            <Badge variant="outline" class="font-normal">
              Stop new trades:
              {{ posture?.controls.stopNewTrades ? 'yes' : 'no' }}
            </Badge>
            <Badge variant="outline" class="font-normal">
              Flatten only: {{ posture?.controls.flattenOnly ? 'yes' : 'no' }}
            </Badge>
            <Badge variant="outline" class="font-normal">
              Automation:
              {{ posture?.controls.automationEnabled ? 'on' : 'off' }}
            </Badge>
            <Badge variant="outline" class="font-normal">
              Execution: {{ posture?.controls.executionEnabled ? 'on' : 'off' }}
            </Badge>
            <Badge
              v-if="posture && !posture.controls.present"
              variant="outline"
              class="font-normal text-muted-foreground"
            >
              No control row — defaults shown
            </Badge>
          </div>
          <div class="mt-3 grid grid-cols-3 gap-4 text-sm">
            <div>
              <p class="text-xs text-muted-foreground">Open trades</p>
              <p class="tabular-nums">
                {{ posture?.counts.openTrades ?? '—' }}
              </p>
            </div>
            <div>
              <p class="text-xs text-muted-foreground">Active reservations</p>
              <p class="tabular-nums">
                {{ posture?.counts.activeReservations ?? '—' }}
              </p>
            </div>
            <div>
              <p class="text-xs text-muted-foreground">Pending orders</p>
              <p class="tabular-nums">
                {{ posture?.counts.pendingOrders ?? '—' }}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <Card class="gap-0 py-0">
      <CardHeader class="px-5 py-4">
        <CardTitle class="text-base">Per-candidate risk decisions</CardTitle>
        <CardDescription>
          Latest risk outcome per watchlist instrument (from the opportunity
          board).
        </CardDescription>
      </CardHeader>
      <CardContent class="border-t p-5">
        <Table v-if="opportunities.data.value?.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead>Symbol</TableHead>
              <TableHead>Direction</TableHead>
              <TableHead>Setup</TableHead>
              <TableHead>Risk state</TableHead>
              <TableHead>Reason</TableHead>
              <TableHead>Plan</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="row in opportunities.data.value"
              :key="row.instrumentId"
            >
              <TableCell class="text-xs">{{ row.symbol }}</TableCell>
              <TableCell class="text-xs">{{ row.direction ?? '—' }}</TableCell>
              <TableCell class="text-xs">{{
                row.setupStatus ?? '—'
              }}</TableCell>
              <TableCell class="text-xs">
                <Badge variant="outline">{{
                  row.riskState ?? 'NOT_EVALUATED'
                }}</Badge>
              </TableCell>
              <TableCell class="text-xs text-muted-foreground">
                {{ row.rejectionReason ?? row.riskDecision ?? '—' }}
              </TableCell>
              <TableCell class="text-xs">{{
                row.planEligibilityStatus ?? '—'
              }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
        <EmptyState
          v-else
          title="No candidate risk decisions"
          description="No watchlist instrument has produced a risk evaluation yet."
        />
      </CardContent>
    </Card>
  </div>
</template>
