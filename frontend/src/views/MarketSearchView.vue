<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@lucide/vue'
import type { BrokerInstrument } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import InstrumentSelector from '@/components/market-data/InstrumentSelector.vue'
import { useInstrumentSearch } from '@/composables/useInstrumentSearch'
import { routeSymbolFor } from '@/lib/instrument'

const router = useRouter()
const search = ref('')
const open = ref(false)
const { query } = useInstrumentSearch(search, open)

function onSearch(value: string) {
  search.value = value
}

/** Primary search is underlying-first: a selected derivative opens its underlying workspace. */
function openInstrument(instrument: BrokerInstrument) {
  router.push({
    name: 'market-ticker',
    params: { symbol: routeSymbolFor(instrument) },
  })
}

function focusSearch() {
  window.dispatchEvent(
    new KeyboardEvent('keydown', { key: 'k', metaKey: true }),
  )
}
</script>

<template>
  <main
    class="mx-auto w-full max-w-[1600px] flex-1 space-y-4 px-4 py-6 lg:px-6"
  >
    <div class="space-y-1">
      <h1 class="text-2xl font-semibold tracking-tight">Market Data</h1>
      <p class="text-sm text-muted-foreground">
        Search an underlying to inspect live price, depth, historical candles,
        futures and options.
      </p>
    </div>

    <Card>
      <CardContent class="p-3">
        <InstrumentSelector
          v-model:open="open"
          :instruments="query.data.value ?? []"
          :loading="query.isFetching.value"
          :model-value="null"
          :search="search"
          @update:model-value="openInstrument"
          @update:search="onSearch"
        />
        <p v-if="query.isError.value" class="mt-2 text-xs text-destructive">
          Instrument master unavailable. Search may be incomplete.
        </p>
      </CardContent>
    </Card>

    <Card>
      <CardContent class="flex flex-col items-center gap-3 py-16 text-center">
        <div class="grid size-10 place-items-center rounded-full bg-muted">
          <Search class="size-4 text-muted-foreground" aria-hidden="true" />
        </div>
        <div class="space-y-1">
          <p class="text-sm font-medium">Start with an underlying</p>
          <p class="mx-auto max-w-md text-sm text-muted-foreground">
            Search by ticker or company name, for example ‘RELIANCE’ or
            ‘Reliance Industries’. Derivatives are available on the underlying
            page.
          </p>
        </div>
        <Button size="sm" @click="focusSearch">
          <Search class="size-3.5" aria-hidden="true" />
          Search instruments
        </Button>
      </CardContent>
    </Card>
  </main>
</template>
