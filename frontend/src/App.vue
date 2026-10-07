<script setup lang="ts">
import { watchEffect } from 'vue'
import { RouterView } from 'vue-router'
import { Toaster } from 'vue-sonner'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import CommandPalette from '@/components/layout/CommandPalette.vue'
import StatusBar from '@/components/layout/StatusBar.vue'
import SystemReadinessBar from '@/components/system/SystemReadinessBar.vue'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { usePreferencesStore } from '@/stores/preferences'

const prefs = usePreferencesStore()

watchEffect(() => {
  document.documentElement.dataset.density = prefs.density
})
</script>

<template>
  <SidebarProvider>
    <AppSidebar />
    <SidebarInset>
      <AppTopbar />
      <div class="px-4 pt-3">
        <SystemReadinessBar />
      </div>
      <RouterView />
      <StatusBar />
    </SidebarInset>
    <CommandPalette />
    <Toaster position="bottom-right" close-button />
  </SidebarProvider>
</template>
