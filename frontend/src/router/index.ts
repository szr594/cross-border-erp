import { createRouter, createWebHistory } from 'vue-router'

import { ensureAuthenticated } from '@/api/auth'
import AppLayout from '@/layouts/AppLayout.vue'
import InventoryDetailView from '@/views/inventory/InventoryDetailView.vue'
import InventoryListView from '@/views/inventory/InventoryListView.vue'
import LoginView from '@/views/auth/LoginView.vue'
import OrderDetailView from '@/views/orders/OrderDetailView.vue'
import OrderListView from '@/views/orders/OrderListView.vue'
import PackingDetailView from '@/views/packing/PackingDetailView.vue'
import PackingView from '@/views/packing/PackingView.vue'
import ProcurementView from '@/views/procurement/ProcurementView.vue'
import PurchaseOrderDetailView from '@/views/procurement/PurchaseOrderDetailView.vue'
import ReceivingView from '@/views/receiving/ReceivingView.vue'
import NotFoundView from '@/views/system/NotFoundView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { title: '登录', public: true },
    },
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', redirect: '/orders' },
        { path: 'orders', name: 'orders', component: OrderListView, meta: { title: '订单工作台' } },
        { path: 'orders/:orderId', name: 'order-detail', component: OrderDetailView, meta: { title: '订单详情' } },
        { path: 'procurement', name: 'procurement', component: ProcurementView, meta: { title: '采购管理' } },
        { path: 'procurement/:purchaseOrderId', name: 'purchase-order-detail', component: PurchaseOrderDetailView, meta: { title: '采购单详情' } },
        { path: 'inventory', name: 'inventory', component: InventoryListView, meta: { title: '库存管理' } },
        { path: 'inventory/:skuId', name: 'inventory-detail', component: InventoryDetailView, meta: { title: '库存详情' } },
        { path: 'receiving', name: 'receiving', component: ReceivingView, meta: { title: '到货收货' } },
        { path: 'packing', name: 'packing', component: PackingView, meta: { title: '打包管理' } },
        { path: 'packing/:orderId', name: 'packing-detail', component: PackingDetailView, meta: { title: '打包详情' } },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: NotFoundView,
      meta: { title: '页面不存在' },
    },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    const user = await ensureAuthenticated()
    return user && to.path === '/login' ? '/orders' : true
  }
  const user = await ensureAuthenticated()
  if (!user) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '工作台')} · Cross Border ERP`
})

export default router
