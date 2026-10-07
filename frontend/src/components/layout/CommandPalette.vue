<script setup lang="ts">
import { useQueryClient } from '@tanstack/vue-query'
import { onKeyStroke, useDark, useToggle } from '@vueuse/core'
import { useRouter } from 'vue-router'
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
  CommandShortcut,
} from '@/components/ui/command'
import PaletteInstruments from './PaletteInstruments.vue'
import { useCommandPalette } from '@/composables/useCommandPalette'
import { usePreferencesStore } from '@/stores/preferences'

const router = useRouter()
const queryClient = useQueryClient()
const palette = useCommandPalette()
const isDark = useDark()
const toggleDark = useToggle(isDark)
const prefs = usePreferencesStore()

const { open, hide } = palette

const navigation = [
  { title: 'Desk', to: '/desk', shortcut: 'D' },
  { title: 'Scanner', to: '/scanner', shortcut: 'S' },
  { title: 'Chart', to: '/chart', shortcut: 'C' },
  { title: 'Watchlist', to: '/watchlist', shortcut: 'W' },
  { title: 'Research', to: '/research', shortcut: 'R' },
  { title: 'Validate', to: '/validate', shortcut: 'V' },
  { title: 'Catalog', to: '/catalog', shortcut: 'G' },
] as const

function go(to: string) {
  hide()
  void router.push(to)
}

function run(action: () => void) {
  hide()
  action()
}

onKeyStroke('k', (event) => {
  if (event.metaKey || event.ctrlKey) {
    event.preventDefault()
    palette.toggle()
  }
})
</script>

<template>
  <CommandDialog
    v-model:open="open"
    title="Command palette"
    description="Search symbols or run a command"
  >
    <CommandInput placeholder="Search symbols or run a command…" />
    <CommandList>
      <CommandEmpty>No results.</CommandEmpty>

      <CommandGroup heading="Instruments">
        <PaletteInstruments />
      </CommandGroup>

      <CommandSeparator />
      <CommandGroup heading="Navigate">
        <CommandItem
          v-for="item in navigation"
          :key="item.to"
          :value="item.title"
          @select="go(item.to)"
        >
          {{ item.title }}
          <CommandShortcut>G {{ item.shortcut }}</CommandShortcut>
        </CommandItem>
      </CommandGroup>

      <CommandSeparator />
      <CommandGroup heading="Actions">
        <CommandItem value="Toggle theme" @select="run(() => toggleDark())">
          Toggle theme
        </CommandItem>
        <CommandItem
          value="Toggle density"
          @select="run(() => prefs.toggleDensity())"
        >
          Toggle density ({{ prefs.density }})
        </CommandItem>
        <CommandItem
          value="Refresh data"
          @select="run(() => queryClient.invalidateQueries())"
        >
          Refresh data
        </CommandItem>
      </CommandGroup>
    </CommandList>
  </CommandDialog>
</template>
