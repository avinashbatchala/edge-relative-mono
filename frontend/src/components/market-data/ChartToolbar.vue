<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CalendarDays } from '@lucide/vue'
import { getLocalTimeZone, parseDate, today } from '@internationalized/date'
import type { DateRange } from 'reka-ui'
import type { BrokerCandleInterval } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { INTERVAL_OPTIONS, RANGE_OPTIONS } from '@/lib/market-time'
import type { RangeKey } from '@/stores/market-data'

const props = defineProps<{
  interval: BrokerCandleInterval
  range: RangeKey
  customStart: string | null
  customEnd: string | null
}>()

const emit = defineEmits<{
  'update:interval': [BrokerCandleInterval]
  'update:range': [RangeKey]
  'update:custom': [string, string]
}>()

const customOpen = ref(false)
const zone = getLocalTimeZone()

function initialRange(): DateRange {
  const start = props.customStart
    ? parseDate(props.customStart)
    : today(zone).add({ days: -30 })
  const end = props.customEnd ? parseDate(props.customEnd) : today(zone)
  return { start, end }
}

const dateRange = ref<DateRange>(initialRange())

function onCalendarUpdate(value: unknown) {
  if (
    value &&
    typeof value === 'object' &&
    'start' in value &&
    'end' in value
  ) {
    dateRange.value = value as DateRange
  }
}

watch(
  () => [props.customStart, props.customEnd] as const,
  () => {
    dateRange.value = initialRange()
  },
)

const customLabel = computed(() =>
  props.customStart && props.customEnd
    ? `${props.customStart} → ${props.customEnd}`
    : 'Custom',
)

function onInterval(value: unknown) {
  if (typeof value === 'string' && value) {
    emit('update:interval', value as BrokerCandleInterval)
  }
}

function onRange(value: unknown) {
  if (typeof value === 'string' && value) {
    emit('update:range', value as RangeKey)
  }
}

function applyCustom() {
  const range = dateRange.value
  if (!range?.start || !range.end) {
    return
  }
  emit('update:custom', range.start.toString(), range.end.toString())
  customOpen.value = false
}
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-3">
    <span class="text-sm font-medium">Price</span>

    <div class="flex flex-wrap items-center gap-2">
      <ToggleGroup
        type="single"
        variant="outline"
        size="sm"
        :model-value="interval"
        aria-label="Candle interval"
        @update:model-value="onInterval"
      >
        <ToggleGroupItem
          v-for="option in INTERVAL_OPTIONS"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </ToggleGroupItem>
      </ToggleGroup>

      <ToggleGroup
        type="single"
        variant="outline"
        size="sm"
        :model-value="range === 'CUSTOM' ? '' : range"
        aria-label="Historical range"
        @update:model-value="onRange"
      >
        <ToggleGroupItem
          v-for="option in RANGE_OPTIONS"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </ToggleGroupItem>
      </ToggleGroup>

      <Popover v-model:open="customOpen">
        <PopoverTrigger as-child>
          <Button
            variant="outline"
            size="sm"
            class="gap-1.5"
            :aria-label="`Custom range: ${customLabel}`"
          >
            <CalendarDays class="size-3.5" aria-hidden="true" />
            <span class="tabular-nums">{{ customLabel }}</span>
          </Button>
        </PopoverTrigger>
        <PopoverContent class="w-auto p-0" align="end">
          <Calendar
            :model-value="dateRange as never"
            range
            :number-of-months="2"
            @update:model-value="onCalendarUpdate"
          />
          <div class="flex justify-end gap-2 border-t p-3">
            <Button variant="ghost" size="sm" @click="customOpen = false"
              >Cancel</Button
            >
            <Button size="sm" @click="applyCustom">Apply</Button>
          </div>
        </PopoverContent>
      </Popover>
    </div>
  </div>
</template>
