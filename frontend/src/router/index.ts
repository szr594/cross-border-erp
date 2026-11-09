import { createRouter, createWebHistory } from 'vue-router'

import { ensureAuthenticated } from '@/api/auth'
import AppLayout from '@/layouts/AppLayout.vue'

const InventoryDetailView = () => import('@/views/inventory/InventoryDetailView.vue')
const InventoryListView = () => import('@/views/inventory/InventoryListView.vue')
const LoginView = () => import('@/views/auth/LoginView.vue')
const OrderDetailView = () => import('@/views/orders/OrderDetailView.vue')
const OrderListView = () => import('@/views/orders/OrderListView.vue')
const PackingDetailView = () => import('@/views/packing/PackingDetailView.vue')
const PackingView = () => import('@/views/packing/PackingView.vue')
const ProcurementView = () => import('@/views/procurement/ProcurementView.vue')
const PurchaseOrderDetailView = () => import('@/views/procurement/PurchaseOrderDetailView.vue')
const ReceivingView = () => import('@/views/receiving/ReceivingView.vue')
const ReceivingPackageDetailView = () => import('@/views/receiving/ReceivingPackageDetailView.vue')
const GoodsReceiptDetailView = () => import('@/views/receiving/GoodsReceiptDetailView.vue')
const NotFoundView = () => import('@/views/system/NotFoundView.vue')

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
        { path: 'inventory/:lotId', name: 'inventory-detail', component: InventoryDetailView, meta: { title: '库存批次详情' } },
        { path: 'receiving', name: 'receiving', component: ReceivingView, meta: { title: '到货收货' } },
        { path: 'receiving/packages/:packageId', name: 'receiving-package-detail', component: ReceivingPackageDetailView, meta: { title: '包裹详情' } },
        { path: 'receiving/receipts/:receiptId', name: 'goods-receipt-detail', component: GoodsReceiptDetailView, meta: { title: '收货批次' } },
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
