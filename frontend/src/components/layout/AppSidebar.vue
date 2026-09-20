<script setup lang="ts">
import {
  Activity,
  Database,
  FlaskConical,
  Gauge,
  LayoutDashboard,
  ListChecks,
  Settings,
  ShieldCheck,
  Wrench,
} from '@lucide/vue'
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  SidebarSeparator,
} from '@/components/ui/sidebar'

const route = useRoute()

const tools = [
  { title: 'Overview', to: '/overview', icon: LayoutDashboard },
  { title: 'Watchlist', to: '/watchlist', icon: ListChecks },
  { title: 'Opportunities', to: '/opportunities', icon: Gauge },
  { title: 'Market Data', to: '/market', icon: Activity },
  { title: 'Research Data', to: '/research', icon: Database },
  { title: 'Backtests', to: '/backtests', icon: FlaskConical },
  { title: 'Strategies & Risk', to: '/strategies', icon: ShieldCheck },
] as const

const upcoming = [{ title: 'Trades', icon: Wrench }] as const

function isActive(to: string): boolean {
  if (to === '/market') {
    return route.path === '/market' || route.path.startsWith('/market/')
  }
  return route.path === to
}

const brandActive = computed(() => route.path === '/overview')
</script>

<template>
  <Sidebar collapsible="icon">
    <SidebarHeader>
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton size="lg" as-child :is-active="brandActive">
            <RouterLink to="/overview">
              <span
                class="grid size-7 shrink-0 place-items-center rounded-md bg-primary text-[11px] font-bold text-primary-foreground"
                aria-hidden="true"
                >er</span
              >
              <span class="flex flex-col gap-0.5 leading-none">
                <span class="font-semibold">Edge Relative</span>
                <span class="text-xs text-muted-foreground">Workstation</span>
              </span>
            </RouterLink>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>

    <SidebarContent>
      <SidebarGroup>
        <SidebarGroupLabel>Tools</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem v-for="item in tools" :key="item.to">
              <SidebarMenuButton as-child :is-active="isActive(item.to)">
                <RouterLink
                  :to="item.to"
                  :aria-current="isActive(item.to) ? 'page' : undefined"
                >
                  <component :is="item.icon" aria-hidden="true" />
                  <span>{{ item.title }}</span>
                </RouterLink>
              </SidebarMenuButton>
            </SidebarMenuItem>

            <SidebarMenuItem v-for="item in upcoming" :key="item.title">
              <SidebarMenuButton
                disabled
                :aria-label="`${item.title} (coming soon)`"
              >
                <component :is="item.icon" aria-hidden="true" />
                <span>{{ item.title }}</span>
              </SidebarMenuButton>
              <SidebarMenuBadge>Soon</SidebarMenuBadge>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </SidebarContent>

    <SidebarFooter>
      <SidebarSeparator />
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton disabled aria-label="Settings (coming soon)">
            <Settings aria-hidden="true" />
            <span>System / Settings</span>
          </SidebarMenuButton>
          <SidebarMenuBadge>Soon</SidebarMenuBadge>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarFooter>

    <SidebarRail />
  </Sidebar>
</template>
