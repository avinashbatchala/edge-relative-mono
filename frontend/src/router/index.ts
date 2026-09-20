import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/overview' },
    { path: '/market-data', redirect: '/market' },
    {
      path: '/overview',
      name: 'overview',
      component: () => import('@/views/OverviewView.vue'),
      meta: { title: 'Overview' },
    },
    {
      path: '/watchlist',
      name: 'watchlist',
      component: () => import('@/views/WatchlistView.vue'),
      meta: { title: 'Watchlist' },
    },
    {
      path: '/features',
      name: 'feature-dashboard',
      component: () => import('@/views/FeatureDashboardView.vue'),
      meta: { title: 'Feature Dashboard' },
    },
    {
      path: '/market',
      name: 'market-search',
      component: () => import('@/views/MarketSearchView.vue'),
      meta: { title: 'Market Data' },
    },
    {
      path: '/market/:symbol',
      name: 'market-ticker',
      component: () => import('@/views/TickerView.vue'),
      props: true,
      meta: { title: 'Market Data' },
    },
    {
      path: '/research',
      name: 'research-data',
      component: () => import('@/views/ResearchDataView.vue'),
      meta: { title: 'Research Data' },
    },
  ],
})

export default router
