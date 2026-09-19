<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, Copy } from '@lucide/vue'
import type {
  BrokerCandleSeries,
  BrokerInstrument,
  BrokerQuote,
} from '@/api/types'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const props = defineProps<{
  instrument: BrokerInstrument
  quote: BrokerQuote | null
  history: BrokerCandleSeries | null
}>()

type DatasetKey = 'instrument' | 'quote' | 'depth' | 'history'

const dataset = ref<DatasetKey>('quote')
const copied = ref(false)

const payload = computed(() => {
  switch (dataset.value) {
    case 'instrument':
      return props.instrument
    case 'depth':
      return props.quote
        ? { bids: props.quote.bids, asks: props.quote.asks }
        : null
    case 'history':
      return props.history
    case 'quote':
    default:
      return props.quote
  }
})

const formatted = computed(() => JSON.stringify(payload.value, null, 2))

async function copy() {
  try {
    await navigator.clipboard.writeText(formatted.value)
    copied.value = true
    window.setTimeout(() => {
      copied.value = false
    }, 1500)
  } catch {
    copied.value = false
  }
}
</script>

<template>
  <div class="space-y-3">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <Select v-model="dataset">
        <SelectTrigger class="w-[220px]" aria-label="Raw dataset">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="quote">Quote</SelectItem>
          <SelectItem value="depth">Depth</SelectItem>
          <SelectItem value="history">Historical</SelectItem>
          <SelectItem value="instrument">Instrument</SelectItem>
        </SelectContent>
      </Select>

      <Button variant="outline" size="sm" :disabled="!payload" @click="copy">
        <Check v-if="copied" class="size-3.5" aria-hidden="true" />
        <Copy v-else class="size-3.5" aria-hidden="true" />
        {{ copied ? 'Copied' : 'Copy' }}
      </Button>
    </div>

    <ScrollArea class="h-[420px] rounded-md border bg-muted/30">
      <pre
        class="p-4 text-xs leading-relaxed"
      ><code>{{ payload ? formatted : 'No data available.' }}</code></pre>
    </ScrollArea>
  </div>
</template>
