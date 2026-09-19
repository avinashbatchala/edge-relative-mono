<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import type { BrokerInstrument } from '@/api/types'
import { formatIstDate, formatPrice, formatQuantity } from '@/lib/format'

const props = defineProps<{ instrument: BrokerInstrument }>()

interface Entry {
  label: string
  value: string
}

const identity = computed<Entry[]>(() => [
  { label: 'Trading symbol', value: props.instrument.tradingSymbol },
  { label: 'Broker symbol', value: props.instrument.brokerSymbol ?? '—' },
  { label: 'Name', value: props.instrument.name ?? '—' },
  { label: 'Exchange', value: props.instrument.exchange },
  { label: 'Segment', value: props.instrument.segment ?? '—' },
  { label: 'Instrument type', value: props.instrument.instrumentType },
  { label: 'Series', value: props.instrument.series ?? '—' },
  { label: 'ISIN', value: props.instrument.isin ?? '—' },
])

const contract = computed<Entry[]>(() => [
  { label: 'Underlying', value: props.instrument.underlyingSymbol ?? '—' },
  {
    label: 'Expiry',
    value: props.instrument.expiryDate
      ? formatIstDate(props.instrument.expiryDate)
      : '—',
  },
  {
    label: 'Strike',
    value:
      props.instrument.strikePrice === null
        ? '—'
        : formatPrice(props.instrument.strikePrice),
  },
  { label: 'Lot size', value: formatQuantity(props.instrument.lotSize) },
  {
    label: 'Tick size',
    value:
      props.instrument.tickSize === null
        ? '—'
        : formatPrice(props.instrument.tickSize),
  },
  {
    label: 'Freeze quantity',
    value:
      props.instrument.freezeQuantity === null
        ? '—'
        : formatQuantity(props.instrument.freezeQuantity),
  },
])

const brokerIdentifiers = computed<Entry[]>(() => [
  { label: 'Exchange token', value: props.instrument.exchangeToken ?? '—' },
  {
    label: 'Underlying token',
    value: props.instrument.underlyingExchangeToken ?? '—',
  },
])
</script>

<template>
  <div class="grid gap-4 lg:grid-cols-2">
    <Card>
      <CardHeader class="pb-3">
        <CardTitle class="text-sm font-medium">Instrument</CardTitle>
      </CardHeader>
      <CardContent>
        <dl class="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
          <div v-for="entry in identity" :key="entry.label" class="space-y-0.5">
            <dt class="text-xs text-muted-foreground">{{ entry.label }}</dt>
            <dd class="truncate text-sm" :title="entry.value">
              {{ entry.value }}
            </dd>
          </div>
        </dl>
        <Separator class="my-4" />
        <div class="flex flex-wrap gap-2">
          <Badge :variant="instrument.buyAllowed ? 'secondary' : 'outline'">
            Buy {{ instrument.buyAllowed ? 'allowed' : 'blocked' }}
          </Badge>
          <Badge :variant="instrument.sellAllowed ? 'secondary' : 'outline'">
            Sell {{ instrument.sellAllowed ? 'allowed' : 'blocked' }}
          </Badge>
          <Badge :variant="instrument.reserved ? 'destructive' : 'outline'">
            {{ instrument.reserved ? 'Reserved' : 'Not reserved' }}
          </Badge>
        </div>
      </CardContent>
    </Card>

    <div class="space-y-4">
      <Card>
        <CardHeader class="pb-3">
          <CardTitle class="text-sm font-medium">Contract</CardTitle>
        </CardHeader>
        <CardContent>
          <dl class="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
            <div
              v-for="entry in contract"
              :key="entry.label"
              class="space-y-0.5"
            >
              <dt class="text-xs text-muted-foreground">{{ entry.label }}</dt>
              <dd class="truncate text-sm tabular-nums">{{ entry.value }}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="pb-3">
          <CardTitle class="text-sm font-medium">Broker identifiers</CardTitle>
        </CardHeader>
        <CardContent>
          <p class="mb-3 text-xs text-muted-foreground">
            Broker-specific references. Edge Relative instrument identity
            remains canonical.
          </p>
          <dl class="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
            <div
              v-for="entry in brokerIdentifiers"
              :key="entry.label"
              class="space-y-0.5"
            >
              <dt class="text-xs text-muted-foreground">{{ entry.label }}</dt>
              <dd class="truncate font-mono text-xs" :title="entry.value">
                {{ entry.value }}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
