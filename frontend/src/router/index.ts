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
      path: '/opportunities',
      name: 'opportunities',
      component: () => import('@/views/OpportunitiesView.vue'),
      meta: { title: 'Opportunities' },
    },
    {
      path: '/features',
      name: 'feature-dashboard',
      component: () => import('@/views/FeatureDashboardView.vue'),
      meta: { title: 'Feature Dashboard' },
    },
    {
      path: '/features/:symbol',
      name: 'feature-ticker',
      component: () => import('@/views/FeatureTickerView.vue'),
      props: true,
      meta: { title: 'Feature Detail' },
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
    {
      path: '/backtests',
      name: 'backtests',
      component: () => import('@/views/BacktestsView.vue'),
      meta: { title: 'Backtests' },
    },
    {
      path: '/backtests/:runKey',
      name: 'backtest-run',
      component: () => import('@/views/BacktestRunView.vue'),
      props: true,
      meta: { title: 'Backtest Run' },
    },
    {
      path: '/strategies',
      name: 'strategies',
      component: () => import('@/views/StrategiesView.vue'),
      meta: { title: 'Strategies & Risk' },
    },
  ],
})

export default router
