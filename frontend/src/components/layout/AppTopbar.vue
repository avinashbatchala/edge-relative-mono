<script setup lang="ts">
import { useDark, useToggle } from '@vueuse/core'
import { Moon, Sun } from '@lucide/vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { SidebarTrigger } from '@/components/ui/sidebar'

const route = useRoute()
const isDark = useDark()
const toggleDark = useToggle(isDark)

const title = computed(() => {
  if (route.path.startsWith('/watchlist')) {
    return 'Watchlist'
  }
  if (route.path.startsWith('/market')) {
    return 'Market Data'
  }
  return 'Overview'
})
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
