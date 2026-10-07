<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CommandItem, useCommand } from '@/components/ui/command'
import { useInstrumentSearch } from '@/composables/useInstrumentSearch'
import { routeSymbolFor } from '@/lib/instrument'

const router = useRouter()
const { filterState } = useCommand()

const search = computed(() => filterState.search)
const enabled = ref(true)
const { query } = useInstrumentSearch(search, enabled)
const instruments = computed(() => query.data.value ?? [])

function open(symbol: string) {
  void router.push({ name: 'market-ticker', params: { symbol } })
}
</script>

<template>
  <CommandItem
    v-for="instrument in instruments"
    :key="`${instrument.exchange}:${instrument.tradingSymbol}:${instrument.brokerSymbol ?? ''}`"
    :value="`${instrument.tradingSymbol} ${instrument.name ?? ''}`"
    @select="open(routeSymbolFor(instrument))"
  >
    <span class="font-medium">{{ instrument.tradingSymbol }}</span>
    <span class="truncate text-xs text-muted-foreground">
      {{ instrument.name ?? instrument.exchange }}
    </span>
  </CommandItem>
  <p
    v-if="instruments.length === 0"
    class="px-2 py-1.5 text-xs text-muted-foreground"
  >
    Type at least two characters to search instruments.
  </p>
</template>
