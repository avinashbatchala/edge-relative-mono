<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { Inbox } from '@lucide/vue'
import {
  getHoldings,
  getOrders,
  getPositions,
  getUserMargin,
  getUserProfile,
  portfolioKeys,
} from '@/api/portfolio'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import EmptyState from '@/components/common/EmptyState.vue'
import PermissionNotice from '@/components/common/PermissionNotice.vue'
import SegmentedTabs from '@/components/common/SegmentedTabs.vue'
import SectionState from '@/components/market-data/SectionState.vue'
import PositionsThesisPanel from '@/components/trade/PositionsThesisPanel.vue'
import { formatInr, formatIstDateTime, formatQuantity } from '@/lib/format'

const tab = ref<'positions' | 'holdings' | 'orders' | 'margin'>('positions')
const tabs = [
  { value: 'positions', label: 'Positions' },
  { value: 'holdings', label: 'Holdings' },
  { value: 'orders', label: 'Orders' },
  { value: 'margin', label: 'Margin' },
] as const

const router = useRouter()

const profileQuery = useQuery(() => ({
  queryKey: portfolioKeys.user(),
  queryFn: ({ signal }) => getUserProfile(signal),
  staleTime: 5 * 60 * 1000,
  retry: 1,
}))

const positionsQuery = useQuery(() => ({
  queryKey: portfolioKeys.positions(),
  queryFn: ({ signal }) => getPositions(undefined, signal),
  enabled: tab.value === 'positions',
  staleTime: 10_000,
  refetchInterval: 20_000,
  retry: 1,
}))

const holdingsQuery = useQuery(() => ({
  queryKey: portfolioKeys.holdings(),
  queryFn: ({ signal }) => getHoldings(signal),
  enabled: tab.value === 'holdings',
  staleTime: 30_000,
  retry: 1,
}))

const ordersQuery = useQuery(() => ({
  queryKey: portfolioKeys.orders(),
  queryFn: ({ signal }) => getOrders(undefined, signal),
  enabled: tab.value === 'orders',
  staleTime: 10_000,
  refetchInterval: 20_000,
  retry: 1,
}))

const marginQuery = useQuery(() => ({
  queryKey: portfolioKeys.margin(),
  queryFn: ({ signal }) => getUserMargin(signal),
  enabled: tab.value === 'margin',
  staleTime: 30_000,
  retry: 1,
}))

const profile = computed(() => profileQuery.data.value ?? null)

const marginRows = computed(() => {
  const margin = marginQuery.data.value
  if (!margin) {
    return []
  }
  return [
    { label: 'Clear cash', value: margin.clearCash },
    { label: 'Net margin used', value: margin.netMarginUsed },
    { label: 'Collateral available', value: margin.collateralAvailable },
    { label: 'Collateral used', value: margin.collateralUsed },
    { label: 'Brokerage & charges', value: margin.brokerageAndCharges },
    { label: 'Adhoc margin', value: margin.adhocMargin },
    { label: 'F&O margin used', value: margin.fno?.netMarginUsed ?? null },
    {
      label: 'Equity margin used',
      value: margin.equity?.netEquityMarginUsed ?? null,
    },
  ]
})

function openChart(symbol: string) {
  void router.push({ name: 'market-ticker', params: { symbol } })
}
</script>

<template>
  <main
    class="mx-auto w-full max-w-[1600px] flex-1 space-y-4 px-4 py-6 lg:px-6"
  >
    <div class="space-y-1">
      <h1 class="text-2xl font-semibold tracking-tight">Trades</h1>
      <p class="text-sm text-muted-foreground">
        Read-only broker account state for reconciliation.
        <template v-if="profile">
          {{ profile.userId }} · {{ profile.uniqueClientCode }} ·
          {{ profile.activeSegments.join(', ') || 'no active segments' }}
        </template>
      </p>
    </div>

    <PermissionNotice
      title="Read-only account context"
      description="Positions, holdings, orders and margin are shown for awareness and reconciliation. Order placement, modification and cancellation are not enabled — Edge Relative never sends a broker mutation."
    />

    <SegmentedTabs v-model="tab" :tabs="tabs" aria-label="Account views" />

    <Card v-if="tab === 'positions'">
      <CardHeader class="pb-3">
        <CardTitle class="text-base">Positions</CardTitle>
        <CardDescription>Open positions across segments.</CardDescription>
      </CardHeader>
      <CardContent>
        <PositionsThesisPanel :positions="positionsQuery.data.value ?? []" />
        <SectionState
          v-if="positionsQuery.isError.value"
          title="Positions unavailable"
          :error="positionsQuery.error.value"
          @retry="positionsQuery.refetch()"
        />
        <div v-else-if="positionsQuery.isPending.value" class="space-y-2">
          <Skeleton v-for="n in 5" :key="n" class="h-9 w-full" />
        </div>
        <EmptyState
          v-else-if="(positionsQuery.data.value ?? []).length === 0"
          :icon="Inbox"
          title="No open positions"
          description="There are no positions for this account."
        />
        <Table v-else>
          <TableHeader>
            <TableRow>
              <TableHead>Symbol</TableHead>
              <TableHead>Product</TableHead>
              <TableHead class="text-right">Qty</TableHead>
              <TableHead class="text-right">Net price</TableHead>
              <TableHead class="text-right">Realised P&amp;L</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="position in positionsQuery.data.value ?? []"
              :key="`${position.tradingSymbol}:${position.product}`"
              class="cursor-pointer"
              @click="openChart(position.tradingSymbol)"
            >
              <TableCell class="font-medium">{{
                position.tradingSymbol
              }}</TableCell>
              <TableCell class="text-muted-foreground">{{
                position.product
              }}</TableCell>
              <TableCell class="text-right tabular-nums">{{
                formatQuantity(position.quantity)
              }}</TableCell>
              <TableCell class="text-right tabular-nums">{{
                formatInr(position.netPrice)
              }}</TableCell>
              <TableCell class="text-right tabular-nums">{{
                formatInr(position.realisedPnl)
              }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <Card v-else-if="tab === 'holdings'">
      <CardHeader class="pb-3">
        <CardTitle class="text-base">Holdings</CardTitle>
        <CardDescription>Long-term demat holdings.</CardDescription>
      </CardHeader>
      <CardContent>
        <SectionState
          v-if="holdingsQuery.isError.value"
          title="Holdings unavailable"
          :error="holdingsQuery.error.value"
          @retry="holdingsQuery.refetch()"
        />
        <div v-else-if="holdingsQuery.isPending.value" class="space-y-2">
          <Skeleton v-for="n in 5" :key="n" class="h-9 w-full" />
        </div>
        <EmptyState
          v-else-if="(holdingsQuery.data.value ?? []).length === 0"
          :icon="Inbox"
          title="No holdings"
          description="There are no demat holdings for this account."
        />
        <Table v-else>
          <TableHeader>
            <TableRow>
              <TableHead>Symbol</TableHead>
              <TableHead>ISIN</TableHead>
              <TableHead class="text-right">Qty</TableHead>
              <TableHead class="text-right">Avg price</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="holding in holdingsQuery.data.value ?? []"
              :key="holding.isin"
              class="cursor-pointer"
              @click="openChart(holding.tradingSymbol)"
            >
              <TableCell class="font-medium">{{
                holding.tradingSymbol
              }}</TableCell>
              <TableCell class="text-muted-foreground">{{
                holding.isin
              }}</TableCell>
              <TableCell class="text-right tabular-nums">{{
                formatQuantity(holding.quantity)
              }}</TableCell>
              <TableCell class="text-right tabular-nums">{{
                formatInr(holding.averagePrice)
              }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <Card v-else-if="tab === 'orders'">
      <CardHeader class="pb-3">
        <CardTitle class="text-base">Orders</CardTitle>
        <CardDescription>Recent broker orders.</CardDescription>
      </CardHeader>
      <CardContent>
        <SectionState
          v-if="ordersQuery.isError.value"
          title="Orders unavailable"
          :error="ordersQuery.error.value"
          @retry="ordersQuery.refetch()"
        />
        <div v-else-if="ordersQuery.isPending.value" class="space-y-2">
          <Skeleton v-for="n in 5" :key="n" class="h-9 w-full" />
        </div>
        <EmptyState
          v-else-if="(ordersQuery.data.value ?? []).length === 0"
          :icon="Inbox"
          title="No orders"
          description="No orders were returned by the broker."
        />
        <Table v-else>
          <TableHeader>
            <TableRow>
              <TableHead>Time</TableHead>
              <TableHead>Symbol</TableHead>
              <TableHead>Side</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Status</TableHead>
              <TableHead class="text-right">Qty</TableHead>
              <TableHead class="text-right">Filled</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="order in ordersQuery.data.value ?? []"
              :key="
                order.brokerOrderId ??
                order.orderReferenceId ??
                order.tradingSymbol
              "
            >
              <TableCell class="text-muted-foreground">{{
                formatIstDateTime(order.createdAt)
              }}</TableCell>
              <TableCell class="font-medium">{{
                order.tradingSymbol
              }}</TableCell>
              <TableCell>
                <Badge
                  :variant="
                    order.transactionType === 'BUY' ? 'secondary' : 'outline'
                  "
                >
                  {{ order.transactionType }}
                </Badge>
              </TableCell>
              <TableCell class="text-muted-foreground">{{
                order.orderType
              }}</TableCell>
              <TableCell>{{ order.status }}</TableCell>
              <TableCell class="text-right tabular-nums">{{
                formatQuantity(order.quantity)
              }}</TableCell>
              <TableCell class="text-right tabular-nums">{{
                formatQuantity(order.filledQuantity)
              }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <Card v-else>
      <CardHeader class="pb-3">
        <CardTitle class="text-base">Margin</CardTitle>
        <CardDescription>Available margin and utilisation.</CardDescription>
      </CardHeader>
      <CardContent>
        <SectionState
          v-if="marginQuery.isError.value"
          title="Margin unavailable"
          :error="marginQuery.error.value"
          @retry="marginQuery.refetch()"
        />
        <div v-else-if="marginQuery.isPending.value" class="space-y-2">
          <Skeleton v-for="n in 6" :key="n" class="h-9 w-full" />
        </div>
        <dl v-else class="grid grid-cols-2 gap-4 sm:grid-cols-3">
          <div v-for="entry in marginRows" :key="entry.label" class="space-y-1">
            <dt class="text-xs text-muted-foreground">{{ entry.label }}</dt>
            <dd class="font-mono text-sm tabular-nums">
              {{ formatInr(entry.value) }}
            </dd>
          </div>
        </dl>
      </CardContent>
    </Card>
  </main>
</template>
