import { createRouter, createWebHistory } from 'vue-router'

/**
 * Pipeline IA: Desk → Scanner → Chart → Research → Validate → Catalog.
 * Route names are kept stable so navigation-by-name across the app and tests does not churn;
 * legacy paths redirect to the new workspace paths.
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/desk' },

    // Legacy aliases → pipeline paths.
    { path: '/overview', redirect: '/desk' },
    { path: '/market', redirect: '/chart' },
    {
      path: '/market/:symbol',
      redirect: (to) => ({
        name: 'market-ticker',
        params: { symbol: to.params.symbol },
      }),
    },
    { path: '/opportunities', redirect: '/setups' },
    { path: '/features', redirect: '/scanner' },
    {
      path: '/features/:symbol',
      redirect: (to) => ({
        name: 'feature-ticker',
        params: { symbol: to.params.symbol },
      }),
    },
    { path: '/backtests', redirect: '/validate' },
    {
      path: '/backtests/:runKey',
      redirect: (to) => ({
        name: 'backtest-run',
        params: { runKey: to.params.runKey },
      }),
    },
    { path: '/strategies', redirect: '/catalog' },

    {
      path: '/desk',
      name: 'overview',
      component: () => import('@/views/OverviewView.vue'),
      meta: { title: 'Desk' },
    },
    {
      path: '/watchlist',
      name: 'watchlist',
      component: () => import('@/views/WatchlistView.vue'),
      meta: { title: 'Watchlist' },
    },
    {
      path: '/scanner',
      name: 'feature-dashboard',
      component: () => import('@/views/FeatureDashboardView.vue'),
      meta: { title: 'Scanner' },
    },
    {
      path: '/scanner/:symbol',
      name: 'feature-ticker',
      component: () => import('@/views/FeatureTickerView.vue'),
      props: true,
      meta: { title: 'Feature Detail' },
    },
    {
      path: '/setups',
      name: 'opportunities',
      component: () => import('@/views/OpportunitiesView.vue'),
      meta: { title: 'Setups' },
    },
    {
      path: '/chart',
      name: 'market-search',
      component: () => import('@/views/MarketSearchView.vue'),
      meta: { title: 'Chart' },
    },
    {
      path: '/chart/:symbol',
      name: 'market-ticker',
      component: () => import('@/views/TickerView.vue'),
      props: true,
      meta: { title: 'Chart' },
    },
    {
      path: '/research',
      name: 'research-data',
      component: () => import('@/views/ResearchDataView.vue'),
      meta: { title: 'Research' },
    },
    {
      path: '/validate',
      name: 'backtests',
      component: () => import('@/views/BacktestsView.vue'),
      meta: { title: 'Validate' },
    },
    {
      path: '/validate/:runKey',
      name: 'backtest-run',
      component: () => import('@/views/BacktestRunView.vue'),
      props: true,
      meta: { title: 'Validation Run' },
    },
    {
      path: '/catalog',
      name: 'strategies',
      component: () => import('@/views/StrategiesView.vue'),
      meta: { title: 'Catalog' },
    },
    {
      path: '/fundamentals',
      name: 'fundamentals',
      component: () => import('@/views/FundamentalsView.vue'),
      meta: { title: 'Fundamentals' },
    },
    {
      path: '/trades',
      name: 'trades',
      component: () => import('@/views/TradesView.vue'),
      meta: { title: 'Trades' },
    },
  ],
})

export default router
