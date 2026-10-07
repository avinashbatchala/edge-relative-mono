<script setup lang="ts">
import {
  Activity,
  Database,
  FlaskConical,
  Gauge,
  LayoutDashboard,
  LineChart,
  ListChecks,
  Settings,
  ShieldCheck,
  Target,
  Wallet,
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

interface NavItem {
  title: string
  to: string
  icon: unknown
  /** Optional path prefix treated as active (for detail routes). */
  prefix?: string
}

const primary: NavItem[] = [
  { title: 'Desk', to: '/desk', icon: LayoutDashboard },
  { title: 'Scanner', to: '/scanner', icon: Gauge, prefix: '/scanner' },
  { title: 'Setups', to: '/setups', icon: Target },
  { title: 'Chart', to: '/chart', icon: Activity, prefix: '/chart' },
  { title: 'Watchlist', to: '/watchlist', icon: ListChecks },
  { title: 'Trades', to: '/trades', icon: Wallet },
]

const analyze: NavItem[] = [
  { title: 'Research', to: '/research', icon: Database },
  {
    title: 'Validate',
    to: '/validate',
    icon: FlaskConical,
    prefix: '/validate',
  },
  { title: 'Catalog', to: '/catalog', icon: ShieldCheck },
  { title: 'Fundamentals', to: '/fundamentals', icon: LineChart },
]

const upcoming = [{ title: 'Journal', icon: Wrench }] as const

function isActive(item: NavItem): boolean {
  if (item.prefix) {
    return (
      route.path === item.prefix || route.path.startsWith(`${item.prefix}/`)
    )
  }
  return route.path === item.to
}

const brandActive = computed(() => route.path === '/desk')
</script>

<template>
  <Sidebar collapsible="icon">
    <SidebarHeader>
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton size="lg" as-child :is-active="brandActive">
            <RouterLink to="/desk">
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
        <SidebarGroupLabel>Trade</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem v-for="item in primary" :key="item.to">
              <SidebarMenuButton as-child :is-active="isActive(item)">
                <RouterLink
                  :to="item.to"
                  :aria-current="isActive(item) ? 'page' : undefined"
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

      <SidebarGroup>
        <SidebarGroupLabel>Analyse</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem v-for="item in analyze" :key="item.to">
              <SidebarMenuButton as-child :is-active="isActive(item)">
                <RouterLink
                  :to="item.to"
                  :aria-current="isActive(item) ? 'page' : undefined"
                >
                  <component :is="item.icon" aria-hidden="true" />
                  <span>{{ item.title }}</span>
                </RouterLink>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </SidebarContent>

    <SidebarFooter>
      <SidebarSeparator />
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton disabled aria-label="System (coming soon)">
            <Settings aria-hidden="true" />
            <span>System</span>
          </SidebarMenuButton>
          <SidebarMenuBadge>Soon</SidebarMenuBadge>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarFooter>

    <SidebarRail />
  </Sidebar>
</template>
