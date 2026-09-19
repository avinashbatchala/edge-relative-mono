<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { Check, ChevronsUpDown, Search } from '@lucide/vue'
import type { BrokerInstrument } from '@/api/types'
import { Button } from '@/components/ui/button'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { Skeleton } from '@/components/ui/skeleton'
import { instrumentKey } from '@/lib/instrument'
import { cn } from '@/lib/utils'

const props = defineProps<{
  instruments: BrokerInstrument[]
  loading: boolean
  modelValue: BrokerInstrument | null
  /** Search term owned by the parent so it can drive the server-side query. */
  search: string
  /** Open state owned by the parent so it can fetch a first page when opened. */
  open: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [BrokerInstrument]
  'update:search': [string]
  'update:open': [boolean]
}>()

const open = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value),
})

const metaLine = computed(() =>
  props.modelValue
    ? [
        props.modelValue.exchange,
        props.modelValue.segment ?? props.modelValue.instrumentType,
      ]
        .filter(Boolean)
        .join(' · ')
    : '',
)

function pick(instrument: BrokerInstrument) {
  emit('update:modelValue', instrument)
  open.value = false
}

function handleSearch(value: unknown) {
  emit('update:search', typeof value === 'string' ? value : '')
}

function shortcut(event: KeyboardEvent) {
  const target = event.target as HTMLElement | null
  const typing =
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement ||
    target?.isContentEditable === true
  const isCommandK =
    (event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k'
  const isSlash = event.key === '/' && !typing
  if (isCommandK || isSlash) {
    event.preventDefault()
    open.value = true
  }
}

onMounted(() => window.addEventListener('keydown', shortcut))
onUnmounted(() => window.removeEventListener('keydown', shortcut))
</script>

<template>
  <Popover v-model:open="open">
    <PopoverTrigger as-child>
      <Button
        variant="outline"
        role="combobox"
        :aria-expanded="open"
        :aria-label="
          modelValue
            ? `Selected instrument ${modelValue.tradingSymbol}`
            : 'Search instrument'
        "
        class="h-auto w-full justify-between gap-3 px-3 py-2.5 text-left font-normal"
      >
        <span class="flex min-w-0 items-center gap-3">
          <Search
            class="size-4 shrink-0 text-muted-foreground"
            aria-hidden="true"
          />
          <span v-if="modelValue" class="flex min-w-0 flex-col">
            <span class="truncate text-sm font-medium">{{
              modelValue.tradingSymbol
            }}</span>
            <span class="truncate text-xs text-muted-foreground">
              {{ modelValue.name ?? metaLine }}
            </span>
          </span>
          <span v-else class="text-sm text-muted-foreground"
            >Search instrument…</span
          >
        </span>
        <span class="flex items-center gap-2">
          <kbd
            v-if="!modelValue"
            class="hidden rounded border bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground sm:inline"
            >⌘K</kbd
          >
          <ChevronsUpDown
            class="size-4 shrink-0 text-muted-foreground"
            aria-hidden="true"
          />
        </span>
      </Button>
    </PopoverTrigger>

    <PopoverContent
      class="w-[var(--reka-popover-trigger-width)] p-0"
      align="start"
    >
      <Command>
        <CommandInput
          placeholder="Search by symbol, name or ISIN…"
          @update:model-value="handleSearch"
        />
        <CommandList>
          <CommandEmpty>
            <template v-if="loading">Searching…</template>
            <template v-else-if="search.trim().length < 2"
              >Type at least 2 characters to search.</template
            >
            <template v-else>No instrument matches “{{ search }}”.</template>
          </CommandEmpty>
          <CommandGroup>
            <CommandItem
              v-for="instrument in instruments"
              :key="instrumentKey(instrument)"
              :value="instrumentKey(instrument)"
              class="gap-3"
              @select="pick(instrument)"
            >
              <Check
                class="size-4 shrink-0"
                :class="
                  cn(
                    'text-foreground',
                    modelValue &&
                      instrumentKey(modelValue) === instrumentKey(instrument)
                      ? 'opacity-100'
                      : 'opacity-0',
                  )
                "
                aria-hidden="true"
              />
              <span class="flex min-w-0 flex-1 flex-col">
                <span class="truncate text-sm">{{
                  instrument.tradingSymbol
                }}</span>
                <span class="truncate text-xs text-muted-foreground">
                  {{ instrument.name ?? '—' }}
                </span>
              </span>
              <span class="shrink-0 text-xs text-muted-foreground">
                {{
                  [
                    instrument.exchange,
                    instrument.segment ?? instrument.instrumentType,
                  ]
                    .filter(Boolean)
                    .join(' · ')
                }}
              </span>
            </CommandItem>
          </CommandGroup>
        </CommandList>
      </Command>
    </PopoverContent>
  </Popover>

  <div v-if="loading && instruments.length === 0" class="mt-2">
    <Skeleton class="h-4 w-40" />
  </div>
</template>
