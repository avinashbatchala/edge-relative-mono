import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/market-data' },
    {
      path: '/market-data',
      name: 'market-data',
      component: () => import('@/views/MarketDataView.vue'),
      meta: { title: 'Market Data' },
    },
  ],
})

export default router
