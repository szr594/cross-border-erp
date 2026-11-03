<script setup lang="ts">
import { RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { getPurchaseOrders, type PurchaseOrderListItem, type PurchaseOrderPage } from '@/api/purchaseOrders'
import PurchaseOrderStatusTag from '@/components/procurement/PurchaseOrderStatusTag.vue'

const router = useRouter()
const loading = ref(false)
const pageData = ref<PurchaseOrderPage | null>(null)
const filters = reactive({ page: 1, size: 20, keyword: '', status: '', sort: 'createdAt,desc' })
const statuses = [
  { value: 'DRAFT', label: '草稿' }, { value: 'ORDERED', label: '已下单' },
  { value: 'PARTIALLY_RECEIVED', label: '部分到货' }, { value: 'RECEIVED', label: '已到货' },
  { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELED', label: '已取消' },
]

async function loadOrders() {
  loading.value = true
  try {
    pageData.value = await getPurchaseOrders({
      ...filters, keyword: filters.keyword.trim() || undefined, status: filters.status || undefined,
    })
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '采购单加载失败')
  } finally { loading.value = false }
}
function search() { filters.page = 1; void loadOrders() }
function reset() {
  Object.assign(filters, { page: 1, size: 20, keyword: '', status: '', sort: 'createdAt,desc' })
  void loadOrders()
}
function openOrder(row: PurchaseOrderListItem) {
  void router.push({ name: 'purchase-order-detail', params: { purchaseOrderId: row.id } })
}
function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(value / 100)
}
function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
onMounted(() => { void loadOrders() })
</script>

<template>
  <section class="purchase-workbench">
    <header class="workbench-intro">
      <div><span class="section-kicker">PURCHASE OPERATIONS</span><h2>采购单工作台</h2><p>集中查看供应商订单、采购数量和到货进度，追踪每一笔采购来源。</p></div>
      <el-button :icon="RefreshRight" :loading="loading" @click="loadOrders">刷新数据</el-button>
    </header>
    <section class="filter-panel" aria-label="采购单筛选">
      <el-input v-model="filters.keyword" clearable placeholder="采购单号、供应商名称或编码" :prefix-icon="Search" @keyup.enter="search" />
      <el-select v-model="filters.status" clearable placeholder="全部状态"><el-option v-for="status in statuses" :key="status.value" :label="status.label" :value="status.value" /></el-select>
      <el-select v-model="filters.sort" placeholder="排序方式"><el-option label="最新创建" value="createdAt,desc" /><el-option label="最早创建" value="createdAt,asc" /><el-option label="金额从高到低" value="totalAmountMinor,desc" /><el-option label="金额从低到高" value="totalAmountMinor,asc" /></el-select>
      <div class="filter-actions"><el-button @click="reset">重置</el-button><el-button type="primary" :icon="Search" @click="search">查询</el-button></div>
    </section>
    <section class="table-panel" aria-label="采购单列表">
      <el-table :data="pageData?.items ?? []" v-loading="loading" row-class-name="clickable-row" @row-click="openOrder">
        <el-table-column label="采购单" min-width="190"><template #default="{ row }: { row: PurchaseOrderListItem }"><div class="primary-cell"><strong>{{ row.purchaseOrderNo }}</strong><small>{{ formatDate(row.createdAt) }}</small></div></template></el-table-column>
        <el-table-column label="供应商" min-width="200"><template #default="{ row }: { row: PurchaseOrderListItem }"><div class="primary-cell"><span>{{ row.supplier.name }}</span><small>{{ row.supplier.code }}</small></div></template></el-table-column>
        <el-table-column label="状态" width="120"><template #default="{ row }: { row: PurchaseOrderListItem }"><PurchaseOrderStatusTag :status="row.status" /></template></el-table-column>
        <el-table-column label="采购内容" min-width="150"><template #default="{ row }: { row: PurchaseOrderListItem }">{{ row.itemCount }} 款 · {{ row.purchasedQuantity }} 件</template></el-table-column>
        <el-table-column label="采购金额" min-width="150" align="right"><template #default="{ row }: { row: PurchaseOrderListItem }"><strong>{{ formatMoney(row.totalAmountMinor, row.currency) }}</strong></template></el-table-column>
        <template #empty><el-empty description="没有符合条件的采购单" :image-size="88" /></template>
      </el-table>
      <footer class="pagination-row"><span>共 {{ pageData?.totalElements ?? 0 }} 条采购单</span><el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" layout="sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="pageData?.totalElements ?? 0" @change="loadOrders" /></footer>
    </section>
  </section>
</template>

<style scoped>
.purchase-workbench { display: grid; gap: 20px; }
.workbench-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.workbench-intro h2 { margin: 2px 0 4px; font-size: 24px; letter-spacing: -.02em; }.workbench-intro p { margin: 0; color: var(--erp-text-muted); }
.section-kicker { color: var(--erp-accent); font-size: 10px; font-weight: 800; letter-spacing: .16em; }
.filter-panel { display: grid; grid-template-columns: minmax(260px, 1.5fr) minmax(150px, .7fr) minmax(170px, .8fr) auto; gap: 12px; padding: 18px; border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); }
.filter-actions { display: flex; gap: 8px; }
.table-panel { overflow: hidden; border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }
.table-panel :deep(.clickable-row) { cursor: pointer; }.table-panel :deep(.clickable-row:hover td) { background: #f4f8f7 !important; }
.primary-cell { display: grid; gap: 3px; }.primary-cell strong { color: var(--erp-primary); }.primary-cell small { color: var(--erp-text-muted); font-size: 12px; }
.pagination-row { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px; border-top: 1px solid var(--erp-border); color: var(--erp-text-muted); font-size: 13px; }
@media (max-width: 980px) { .filter-panel { grid-template-columns: repeat(2, 1fr); }.filter-actions { justify-content: flex-end; } }
@media (max-width: 680px) { .workbench-intro { display: grid; }.filter-panel { grid-template-columns: 1fr; }.filter-actions .el-button { flex: 1; }.pagination-row { align-items: flex-start; gap: 12px; flex-direction: column; } }
</style>
