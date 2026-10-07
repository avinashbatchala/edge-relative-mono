<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { TriangleAlert } from '@lucide/vue'
import { getOpportunities, opportunityKeys } from '@/api/opportunities'
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

const opportunities = useQuery({
  queryKey: opportunityKeys.list(),
  queryFn: ({ signal }) => getOpportunities(signal),
})
</script>

<template>
  <div class="flex flex-col gap-4 p-4 lg:p-6">
    <header class="space-y-1">
      <h1 class="text-xl font-semibold tracking-tight">Risk center</h1>
      <p class="text-sm text-muted-foreground">
        Risk has authority above opportunity. Portfolio-level capacity is shown
        once a live risk context producer is wired; per-candidate decisions are
        shown below.
      </p>
    </header>

    <Card class="border-amber-500/40">
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
            Net liquidation value, P&amp;L, drawdown, risk used/available and
            concentration are not currently produced by the backend (the live
            risk context provider is not wired). The engine fails closed on
            missing context, so no trade can be approved without it. This screen
            will populate automatically once the provider exists.
          </CardDescription>
        </div>
      </CardHeader>
    </Card>

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
