<script setup lang="ts">
import { useDark, useToggle } from '@vueuse/core'
import { Moon, Rows2, Rows3, Search, Sun } from '@lucide/vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { SidebarTrigger } from '@/components/ui/sidebar'
import { useCommandPalette } from '@/composables/useCommandPalette'
import { usePreferencesStore } from '@/stores/preferences'

const route = useRoute()
const isDark = useDark()
const toggleDark = useToggle(isDark)
const palette = useCommandPalette()
const prefs = usePreferencesStore()

const title = computed(
  () => (route.meta.title as string | undefined) ?? 'Edge Relative',
)
const isCompact = computed(() => prefs.isCompact)
</script>

<template>
  <header
    class="sticky top-0 z-10 flex h-14 items-center gap-2 border-b bg-background px-4"
  >
    <SidebarTrigger />
    <Separator orientation="vertical" class="mr-1 !h-4" />
    <div class="text-sm font-medium">{{ title }}</div>

    <div class="ml-auto flex items-center gap-2">
      <Button
        variant="outline"
        size="sm"
        class="h-8 gap-2 text-muted-foreground"
        aria-label="Open command palette"
        @click="palette.show()"
      >
        <Search class="size-3.5" aria-hidden="true" />
        <span class="hidden sm:inline">Search</span>
        <kbd
          class="hidden rounded border bg-muted px-1 font-mono text-[10px] sm:inline"
          >⌘K</kbd
        >
      </Button>

      <Button
        variant="ghost"
        size="icon"
        class="size-8"
        :aria-label="
          isCompact
            ? 'Switch to comfortable density'
            : 'Switch to compact density'
        "
        @click="prefs.toggleDensity()"
      >
        <Rows3 v-if="isCompact" class="size-4" aria-hidden="true" />
        <Rows2 v-else class="size-4" aria-hidden="true" />
      </Button>

      <Button
        variant="ghost"
        size="icon"
        class="size-8"
        :aria-label="isDark ? 'Switch to light theme' : 'Switch to dark theme'"
        @click="toggleDark()"
      >
        <Moon v-if="isDark" class="size-4" aria-hidden="true" />
        <Sun v-else class="size-4" aria-hidden="true" />
      </Button>
    </div>
  </header>
</template>
