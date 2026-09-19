import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/market' },
    { path: '/market-data', redirect: '/market' },
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
  ],
})

export default router
