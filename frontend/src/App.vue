<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppHeader from '@/components/layout/AppHeader.vue'
import { TooltipProvider } from '@/components/ui/tooltip'
</script>

<template>
  <TooltipProvider :delay-duration="150">
    <div class="flex min-h-svh flex-col bg-background text-foreground">
      <AppHeader />
      <RouterView />
    </div>
  </TooltipProvider>
</template>
