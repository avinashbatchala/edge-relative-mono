<script setup lang="ts">
import { RotateCcw, Search, SlidersHorizontal } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'

defineProps<{
  activeFilters: { key: string; label: string }[]
  visible: number
  total: number
}>()

const emit = defineEmits<{
  clearFilter: [key: string]
  clearAll: []
}>()

const search = defineModel<string>('search', { required: true })
const timeframe = defineModel<string>('timeframe', { required: true })
const rs = defineModel<string>('rs', { required: true })
const rve = defineModel<string>('rve', { required: true })
const alignment = defineModel<string>('alignment', { required: true })
const quality = defineModel<string>('quality', { required: true })
const freshness = defineModel<string>('freshness', { required: true })
const rvol = defineModel<string>('rvol', { required: true })
</script>

<template>
  <div class="flex flex-1 flex-wrap items-center gap-2">
    <div class="relative">
      <Search
        class="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground"
        aria-hidden="true"
      />
      <Input
        v-model="search"
        placeholder="Search symbol"
        aria-label="Search watchlist symbols"
        class="h-9 w-56 pl-8"
      />
    </div>

    <Popover>
      <PopoverTrigger as-child>
        <Button variant="outline" size="sm" class="h-9">
          <SlidersHorizontal class="mr-1 size-3.5" aria-hidden="true" />
          Filters
          <span
            v-if="activeFilters.length"
            class="ml-1 grid size-4 place-items-center rounded-full bg-primary text-[10px] font-semibold text-primary-foreground"
          >
            {{ activeFilters.length }}
          </span>
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" class="w-80 space-y-3 p-3">
        <div class="grid grid-cols-2 gap-3">
          <label class="space-y-1 text-xs">
            <span class="text-muted-foreground">Timeframe</span>
            <NativeSelect v-model="timeframe" aria-label="Timeframe filter">
              <NativeSelectOption value="all">All</NativeSelectOption>
              <NativeSelectOption value="M5">5m</NativeSelectOption>
              <NativeSelectOption value="D1">1D</NativeSelectOption>
            </NativeSelect>
          </label>
          <label class="space-y-1 text-xs">
            <span class="text-muted-foreground">Relative strength</span>
            <NativeSelect v-model="rs" aria-label="Relative strength filter">
              <NativeSelectOption value="all">All</NativeSelectOption>
              <NativeSelectOption value="positive">Positive</NativeSelectOption>
              <NativeSelectOption value="negative">Negative</NativeSelectOption>
              <NativeSelectOption value="neutral">Neutral</NativeSelectOption>
            </NativeSelect>
          </label>
          <label class="space-y-1 text-xs">
            <span class="text-muted-foreground">Relative volume</span>
            <NativeSelect v-model="rve" aria-label="RVE filter">
              <NativeSelectOption value="all">All</NativeSelectOption>
              <NativeSelectOption value="expanding"
                >Expanding</NativeSelectOption
              >
              <NativeSelectOption value="stable">Stable</NativeSelectOption>
              <NativeSelectOption value="contracting"
                >Contracting</NativeSelectOption
              >
            </NativeSelect>
          </label>
          <label class="space-y-1 text-xs">
            <span class="text-muted-foreground">Alignment</span>
            <NativeSelect v-model="alignment" aria-label="Alignment filter">
              <NativeSelectOption value="all">All</NativeSelectOption>
              <NativeSelectOption value="market"
                >Market aligned</NativeSelectOption
              >
              <NativeSelectOption value="sector"
                >Sector aligned</NativeSelectOption
              >
              <NativeSelectOption value="both">Both aligned</NativeSelectOption>
            </NativeSelect>
          </label>
          <label class="space-y-1 text-xs">
            <span class="text-muted-foreground">Quality</span>
            <NativeSelect v-model="quality" aria-label="Quality filter">
              <NativeSelectOption value="all">All</NativeSelectOption>
              <NativeSelectOption value="trustworthy"
                >Trustworthy</NativeSelectOption
              >
              <NativeSelectOption value="degraded">Degraded</NativeSelectOption>
              <NativeSelectOption value="unavailable"
                >Unavailable</NativeSelectOption
              >
            </NativeSelect>
          </label>
          <label class="space-y-1 text-xs">
            <span class="text-muted-foreground">Freshness</span>
            <NativeSelect v-model="freshness" aria-label="Freshness filter">
              <NativeSelectOption value="all">All</NativeSelectOption>
              <NativeSelectOption value="fresh">Fresh</NativeSelectOption>
              <NativeSelectOption value="stale">Stale</NativeSelectOption>
            </NativeSelect>
          </label>
          <label class="col-span-2 space-y-1 text-xs">
            <span class="text-muted-foreground">Minimum interval RVOL</span>
            <Input
              v-model="rvol"
              type="number"
              min="0"
              step="0.1"
              placeholder="e.g. 1.5"
              aria-label="Minimum interval RVOL"
              class="h-9"
            />
          </label>
        </div>
      </PopoverContent>
    </Popover>

    <template v-if="activeFilters.length">
      <span
        v-for="filter in activeFilters"
        :key="filter.key"
        class="inline-flex items-center gap-1 rounded-full border bg-background px-2 py-0.5 text-xs"
      >
        {{ filter.label }}
        <button
          type="button"
          class="text-muted-foreground hover:text-foreground"
          :aria-label="`Remove filter ${filter.label}`"
          @click="emit('clearFilter', filter.key)"
        >
          ×
        </button>
      </span>
      <Button
        variant="ghost"
        size="sm"
        class="h-7 px-2 text-xs"
        @click="emit('clearAll')"
      >
        <RotateCcw class="mr-1 size-3" aria-hidden="true" /> Clear
      </Button>
    </template>

    <span class="ml-auto whitespace-nowrap text-xs text-muted-foreground">
      Showing {{ visible }} of {{ total }} watchlist rows
    </span>
  </div>
</template>
