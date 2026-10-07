<script setup lang="ts">
import {
  Activity,
  Boxes,
  Brain,
  Database,
  FlaskConical,
  Gauge,
  HeartPulse,
  LayoutDashboard,
  LineChart,
  ListChecks,
  Settings,
  ShieldAlert,
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

const trade: NavItem[] = [
  { title: 'Desk', to: '/desk', icon: LayoutDashboard },
  { title: 'Opportunities', to: '/setups', icon: Target },
  { title: 'Watchlist', to: '/watchlist', icon: ListChecks },
  { title: 'Scanner', to: '/scanner', icon: Gauge, prefix: '/scanner' },
  { title: 'Instrument', to: '/chart', icon: Activity, prefix: '/chart' },
  { title: 'Positions', to: '/positions', icon: Wallet, prefix: '/positions' },
]

const research: NavItem[] = [
  { title: 'Data', to: '/research', icon: Database },
  {
    title: 'Backtests',
    to: '/validate',
    icon: FlaskConical,
    prefix: '/validate',
  },
  { title: 'ML Lab', to: '/research/ml', icon: Brain, prefix: '/research/ml' },
  { title: 'Models', to: '/research/ml/models', icon: Boxes },
  { title: 'Strategies', to: '/catalog', icon: ShieldCheck },
  { title: 'Fundamentals', to: '/fundamentals', icon: LineChart },
]

const system: NavItem[] = [
  {
    title: 'Risk',
    to: '/system/risk',
    icon: ShieldAlert,
    prefix: '/system/risk',
  },
  {
    title: 'Health',
    to: '/system/health',
    icon: HeartPulse,
    prefix: '/system/health',
  },
  {
    title: 'Config',
    to: '/system/config',
    icon: Settings,
    prefix: '/system/config',
  },
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
            <SidebarMenuItem v-for="item in trade" :key="item.to">
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

      <SidebarGroup>
        <SidebarGroupLabel>Research</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem v-for="item in research" :key="item.to">
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

      <SidebarGroup>
        <SidebarGroupLabel>System</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu>
            <SidebarMenuItem v-for="item in system" :key="item.to">
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
    </SidebarFooter>

    <SidebarRail />
  </Sidebar>
</template>
