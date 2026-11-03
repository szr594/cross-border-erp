<script setup lang="ts">
import { RefreshRight, Search, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { getOrderFilterOptions, getOrders, type OrderFilterOptions, type OrderListItem, type OrderPage } from '@/api/orders'
import OrderStageTag from '@/components/orders/OrderStageTag.vue'

const router = useRouter()
const loading = ref(false)
const optionsLoading = ref(false)
const pageData = ref<OrderPage | null>(null)
const options = ref<OrderFilterOptions>({ stores: [], responsibleUsers: [], externalStatuses: [], fulfillmentStages: [] })
const orderedRange = ref<[string, string] | null>(null)
const filters = reactive({
  page: 1, size: 20, keyword: '', storeId: undefined as number | undefined,
  responsibleUserId: undefined as number | undefined, externalStatus: '', stage: 'ACTIONABLE',
  exceptionOnly: false, sort: 'requestedShipAt,asc',
})

async function loadOptions() {
  optionsLoading.value = true
  try { options.value = await getOrderFilterOptions() }
  catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '筛选选项加载失败') }
  finally { optionsLoading.value = false }
}

async function loadOrders() {
  loading.value = true
  try {
    pageData.value = await getOrders({
      ...filters,
      keyword: filters.keyword.trim() || undefined,
      externalStatus: filters.externalStatus || undefined,
      orderedFrom: orderedRange.value?.[0],
      orderedTo: orderedRange.value?.[1],
    })
  } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '订单加载失败') }
  finally { loading.value = false }
}

function search() { filters.page = 1; void loadOrders() }
function reset() {
  Object.assign(filters, {
    page: 1, size: 20, keyword: '', storeId: undefined, responsibleUserId: undefined,
    externalStatus: '', stage: 'ACTIONABLE', exceptionOnly: false, sort: 'requestedShipAt,asc',
  })
  orderedRange.value = null
  void loadOrders()
}
function openOrder(row: OrderListItem) { void router.push({ name: 'order-detail', params: { orderId: row.id } }) }
function formatDate(value: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
function statusName(value: string) {
  return options.value.externalStatuses.find(option => option.value === value)?.label ?? value
}

onMounted(() => { void Promise.all([loadOptions(), loadOrders()]) })
</script>

<template>
  <section class="order-workbench">
    <div class="workbench-intro">
      <div><span class="section-kicker">ORDER OPERATIONS</span><h2>订单履约总览</h2><p>集中查看时效、采购缺口与异常订单，快速进入下一步处理。</p></div>
      <el-button :icon="RefreshRight" :loading="loading" @click="loadOrders">刷新数据</el-button>
    </div>

    <div class="summary-grid">
      <button class="summary-card" type="button" @click="filters.stage = 'PENDING'; search()"><span>待处理</span><strong>{{ pageData?.summary.pending ?? 0 }}</strong><small>尚未形成履约动作</small></button>
      <button class="summary-card amber" type="button" @click="filters.stage = 'WAITING_PROCUREMENT'; search()"><span>待采购</span><strong>{{ pageData?.summary.waitingProcurement ?? 0 }}</strong><small>已有采购申请</small></button>
      <button class="summary-card green" type="button" @click="filters.stage = 'READY_FOR_PACKING'; search()"><span>已齐货</span><strong>{{ pageData?.summary.readyForPacking ?? 0 }}</strong><small>可以进入打包</small></button>
      <button class="summary-card red" type="button" @click="filters.stage = 'EXCEPTION'; search()"><span>异常订单</span><strong>{{ pageData?.summary.exception ?? 0 }}</strong><small>需要人工关注</small></button>
    </div>

    <div class="filter-panel" v-loading="optionsLoading">
      <el-input v-model="filters.keyword" clearable placeholder="订单号、商品名称或 SKU" :prefix-icon="Search" @keyup.enter="search" />
      <el-select v-model="filters.storeId" clearable placeholder="全部店铺"><el-option v-for="store in options.stores" :key="store.id" :label="store.name" :value="store.id" /></el-select>
      <el-select v-model="filters.responsibleUserId" clearable placeholder="全部负责人"><el-option v-for="user in options.responsibleUsers" :key="user.id" :label="user.displayName" :value="user.id" /></el-select>
      <el-select v-model="filters.externalStatus" clearable placeholder="外部状态"><el-option v-for="status in options.externalStatuses" :key="status.value" :label="status.label" :value="status.value" /></el-select>
      <el-select v-model="filters.stage" placeholder="履约阶段"><el-option v-for="stage in options.fulfillmentStages" :key="stage.value" :label="stage.label" :value="stage.value" /></el-select>
      <el-date-picker v-model="orderedRange" type="datetimerange" start-placeholder="下单开始" end-placeholder="下单结束" value-format="YYYY-MM-DDTHH:mm:ssZ" />
      <el-checkbox v-model="filters.exceptionOnly">只看异常</el-checkbox>
      <div class="filter-actions"><el-button @click="reset">重置</el-button><el-button type="primary" :icon="Search" @click="search">查询</el-button></div>
    </div>

    <div class="table-panel">
      <el-table :data="pageData?.items ?? []" v-loading="loading" row-class-name="clickable-row" @row-click="openOrder">
        <el-table-column label="订单" min-width="190"><template #default="{ row }: { row: OrderListItem }"><div class="order-cell"><strong>{{ row.orderNo }}</strong><small>{{ row.displayOrderNo || row.store.name }}</small></div></template></el-table-column>
        <el-table-column label="商品" min-width="240"><template #default="{ row }: { row: OrderListItem }"><div class="product-cell"><span>{{ row.productNames.join('、') || '暂无商品' }}</span><small>{{ row.itemCount }} 款 · {{ row.totalQuantity }} 件</small></div></template></el-table-column>
        <el-table-column label="店铺 / 负责人" min-width="160"><template #default="{ row }: { row: OrderListItem }"><div>{{ row.store.name }}</div><small class="muted">{{ row.responsibleUser?.displayName || '待分配' }}</small></template></el-table-column>
        <el-table-column label="履约阶段" width="130"><template #default="{ row }: { row: OrderListItem }"><OrderStageTag :stage="row.stage" :label="row.stageName" /></template></el-table-column>
        <el-table-column label="外部状态" width="110"><template #default="{ row }: { row: OrderListItem }">{{ statusName(row.externalStatus) }}</template></el-table-column>
        <el-table-column label="要求发货" width="150"><template #default="{ row }: { row: OrderListItem }"><span :class="{ 'deadline-alert': row.exception }">{{ formatDate(row.requestedShipAt) }}</span></template></el-table-column>
        <el-table-column width="56" align="center"><template #default="{ row }: { row: OrderListItem }"><el-icon v-if="row.exception" class="exception-icon"><WarningFilled /></el-icon></template></el-table-column>
        <template #empty><el-empty description="没有符合条件的订单" :image-size="88" /></template>
      </el-table>
      <div class="pagination-row"><span>共 {{ pageData?.totalElements ?? 0 }} 条订单</span><el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" layout="sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="pageData?.totalElements ?? 0" @change="loadOrders" /></div>
    </div>
  </section>
</template>

<style scoped>
.order-workbench { display: grid; gap: 20px; }
.workbench-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.workbench-intro h2 { margin: 2px 0 4px; font-size: 24px; letter-spacing: -.02em; }
.workbench-intro p { margin: 0; color: var(--erp-text-muted); }
.section-kicker { color: var(--erp-accent); font-size: 10px; font-weight: 800; letter-spacing: .16em; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.summary-card { display: grid; gap: 2px; padding: 18px 20px; border: 1px solid var(--erp-border); border-left: 4px solid #82928e; border-radius: var(--erp-radius); color: var(--erp-text); background: var(--erp-surface); cursor: pointer; text-align: left; transition: transform .18s ease-out, box-shadow .18s ease-out; }
.summary-card:hover { transform: translateY(-2px); box-shadow: var(--erp-shadow); }
.summary-card strong { margin: 2px 0; font-size: 28px; line-height: 1.2; }
.summary-card span { color: var(--erp-text-muted); font-size: 13px; font-weight: 700; }
.summary-card small { color: #98a39f; }
.summary-card.amber { border-left-color: var(--erp-accent); }.summary-card.green { border-left-color: var(--erp-primary); }.summary-card.red { border-left-color: #c45656; }
.filter-panel { display: grid; grid-template-columns: 1.4fr repeat(4, minmax(140px, .8fr)); gap: 12px; padding: 18px; border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); }
.filter-panel :deep(.el-date-editor) { width: 100%; grid-column: span 2; }.filter-actions { display: flex; justify-content: flex-end; gap: 8px; grid-column: -3 / -1; }
.table-panel { overflow: hidden; border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }
.table-panel :deep(.clickable-row) { cursor: pointer; }.table-panel :deep(.clickable-row:hover td) { background: #f4f8f7 !important; }
.order-cell, .product-cell { display: grid; gap: 3px; }.order-cell strong { color: var(--erp-primary); }.order-cell small, .product-cell small, .muted { color: var(--erp-text-muted); font-size: 12px; }
.deadline-alert, .exception-icon { color: #c45656; }.deadline-alert { font-weight: 700; }.exception-icon { font-size: 18px; }
.pagination-row { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px; border-top: 1px solid var(--erp-border); color: var(--erp-text-muted); font-size: 13px; }
@media (max-width: 1180px) { .summary-grid { grid-template-columns: repeat(2, 1fr); }.filter-panel { grid-template-columns: repeat(2, 1fr); }.filter-panel :deep(.el-date-editor), .filter-actions { grid-column: span 2; } }
@media (max-width: 720px) { .summary-grid, .filter-panel { grid-template-columns: 1fr; }.filter-panel :deep(.el-date-editor), .filter-actions { grid-column: span 1; }.filter-actions { justify-content: stretch; }.filter-actions .el-button { flex: 1; } }
</style>
