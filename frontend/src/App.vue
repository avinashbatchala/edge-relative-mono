<script setup lang="ts">
import { watchEffect } from 'vue'
import { RouterView } from 'vue-router'
import { Toaster } from 'vue-sonner'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import CommandPalette from '@/components/layout/CommandPalette.vue'
import StatusBar from '@/components/layout/StatusBar.vue'
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
      <RouterView />
      <StatusBar />
    </SidebarInset>
    <CommandPalette />
    <Toaster position="bottom-right" close-button />
  </SidebarProvider>
</template>
