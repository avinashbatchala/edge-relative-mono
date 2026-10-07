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
      // The standalone feature-detail page is merged into the Instrument Workspace; keep the name as
      // a redirecting alias so existing navigation by name still works.
      path: '/scanner/:symbol',
      name: 'feature-ticker',
      redirect: (to) => ({
        name: 'market-ticker',
        params: { symbol: to.params.symbol },
      }),
    },
    {
      path: '/setups',
      name: 'opportunities',
      component: () => import('@/views/OpportunitiesView.vue'),
      meta: { title: 'Opportunities' },
    },
    {
      path: '/chart',
      name: 'market-search',
      component: () => import('@/views/MarketSearchView.vue'),
      meta: { title: 'Instrument' },
    },
    {
      path: '/chart/:symbol',
      name: 'market-ticker',
      component: () => import('@/views/TickerView.vue'),
      props: true,
      meta: { title: 'Instrument' },
    },
    {
      path: '/research',
      name: 'research-data',
      component: () => import('@/views/ResearchDataView.vue'),
      meta: { title: 'Data' },
    },
    {
      path: '/research/ml',
      name: 'ml-lab',
      component: () => import('@/views/MlLabView.vue'),
      meta: { title: 'ML Lab' },
    },
    {
      path: '/research/ml/models',
      name: 'ml-models',
      component: () => import('@/views/MlModelsView.vue'),
      meta: { title: 'Models & bindings' },
    },
    {
      path: '/research/ml/:runKey',
      name: 'ml-run',
      component: () => import('@/views/MlRunView.vue'),
      props: true,
      meta: { title: 'ML analysis' },
    },
    {
      path: '/validate',
      name: 'backtests',
      component: () => import('@/views/BacktestsView.vue'),
      meta: { title: 'Backtests' },
    },
    {
      path: '/validate/:runKey',
      name: 'backtest-run',
      component: () => import('@/views/BacktestRunView.vue'),
      props: true,
      meta: { title: 'Backtest run' },
    },
    {
      path: '/catalog',
      name: 'strategies',
      component: () => import('@/views/StrategiesView.vue'),
      meta: { title: 'Strategies' },
    },
    {
      path: '/fundamentals',
      name: 'fundamentals',
      component: () => import('@/views/FundamentalsView.vue'),
      meta: { title: 'Fundamentals' },
    },
    {
      path: '/positions',
      alias: '/trades',
      name: 'trades',
      component: () => import('@/views/TradesView.vue'),
      meta: { title: 'Positions' },
    },
    {
      path: '/system/health',
      name: 'system-health',
      component: () => import('@/views/SystemHealthView.vue'),
      meta: { title: 'System health' },
    },
    {
      path: '/system/risk',
      name: 'system-risk',
      component: () => import('@/views/RiskCenterView.vue'),
      meta: { title: 'Risk center' },
    },
    {
      path: '/system/config',
      name: 'system-config',
      component: () => import('@/views/ConfigView.vue'),
      meta: { title: 'Configuration' },
    },
  ],
})

export default router
