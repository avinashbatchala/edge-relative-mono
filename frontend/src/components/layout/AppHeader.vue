<script setup lang="ts">
import { useDark, useToggle } from '@vueuse/core'
import { Activity, Moon, Sun } from '@lucide/vue'
import { RouterLink } from 'vue-router'
import { Button } from '@/components/ui/button'

const isDark = useDark()
const toggleDark = useToggle(isDark)
</script>

<template>
  <header class="border-b bg-background">
    <div
      class="mx-auto flex h-14 w-full max-w-[1600px] items-center gap-6 px-4 lg:px-6"
    >
      <RouterLink
        to="/market"
        class="flex items-center gap-2 text-sm font-semibold tracking-tight"
      >
        <span
          class="grid size-7 place-items-center rounded-md bg-primary text-[11px] font-bold text-primary-foreground"
          aria-hidden="true"
          >er</span
        >
        <span>Edge Relative</span>
      </RouterLink>

      <nav class="flex items-center gap-1" aria-label="Primary">
        <Button
          as-child
          variant="ghost"
          size="sm"
          class="text-muted-foreground"
        >
          <RouterLink to="/market">
            <Activity class="size-4" aria-hidden="true" />
            Market Data
          </RouterLink>
        </Button>
      </nav>

      <div class="ml-auto flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          class="size-8"
          :aria-label="
            isDark ? 'Switch to light theme' : 'Switch to dark theme'
          "
          @click="toggleDark()"
        >
          <Moon v-if="isDark" class="size-4" aria-hidden="true" />
          <Sun v-else class="size-4" aria-hidden="true" />
        </Button>
      </div>
    </div>
  </header>
</template>
