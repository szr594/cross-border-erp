<script setup lang="ts">
import { Goods, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { getInventoryLots, type InventoryLotListItem, type InventoryLotPage, type InventoryLotStatus, type InventoryQualityStatus } from '@/api/inventory'
import InventoryLotStatusTag from '@/components/inventory/InventoryLotStatusTag.vue'
import InventoryQualityTag from '@/components/inventory/InventoryQualityTag.vue'

const router = useRouter()
const loading = ref(false)
const pageData = ref<InventoryLotPage | null>(null)
const filters = reactive({ page: 1, size: 20, keyword: '', purchaseOrderId: null as number | null, status: '' as InventoryLotStatus | '', qualityStatus: '' as InventoryQualityStatus | '' })
const statuses: { value: InventoryLotStatus; label: string }[] = [
  { value: 'AVAILABLE', label: '可用' }, { value: 'ALLOCATED', label: '已预留' },
  { value: 'QUARANTINED', label: '隔离中' }, { value: 'DEPLETED', label: '已耗尽' }, { value: 'CLOSED', label: '已关闭' },
]
const qualityStatuses: { value: InventoryQualityStatus; label: string }[] = [
  { value: 'ACCEPTED', label: '合格品' }, { value: 'DAMAGED', label: '破损品' },
]
const pageTotals = computed(() => (pageData.value?.items ?? []).reduce((totals, item) => ({
  available: totals.available + item.availableQuantity,
  reserved: totals.reserved + item.reservedQuantity,
  quarantined: totals.quarantined + item.quarantinedQuantity,
}), { available: 0, reserved: 0, quarantined: 0 }))

async function loadLots() {
  loading.value = true
  try {
    pageData.value = await getInventoryLots({
      page: filters.page, size: filters.size, keyword: filters.keyword.trim() || undefined,
      purchaseOrderId: filters.purchaseOrderId ?? undefined, status: filters.status || undefined,
      qualityStatus: filters.qualityStatus || undefined,
    })
  } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '库存批次加载失败') }
  finally { loading.value = false }
}
function search() { filters.page = 1; void loadLots() }
function reset() { Object.assign(filters, { page: 1, size: 20, keyword: '', purchaseOrderId: null, status: '', qualityStatus: '' }); void loadLots() }
function openLot(row: InventoryLotListItem) { void router.push({ name: 'inventory-detail', params: { lotId: row.id } }) }
function formatDate(value: string) { return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
onMounted(loadLots)
</script>

<template>
  <section class="inventory-workbench">
    <header class="workbench-intro"><div><span class="section-kicker">INVENTORY CONTROL</span><h2>库存工作台</h2><p>按批次追踪可用、预留与隔离库存，查看完整采购和收货来源。</p></div><el-button :icon="RefreshRight" :loading="loading" @click="loadLots">刷新</el-button></header>
    <section class="metric-grid" aria-label="当前页库存概览">
      <article><span class="metric-icon available"><el-icon><Goods /></el-icon></span><div><small>当前页可用</small><strong>{{ pageTotals.available }}</strong></div></article>
      <article><span class="metric-icon reserved"><el-icon><Goods /></el-icon></span><div><small>当前页已预留</small><strong>{{ pageTotals.reserved }}</strong></div></article>
      <article><span class="metric-icon quarantined"><el-icon><Goods /></el-icon></span><div><small>当前页隔离</small><strong>{{ pageTotals.quarantined }}</strong></div></article>
      <article><span class="metric-icon lots"><el-icon><Goods /></el-icon></span><div><small>筛选批次数</small><strong>{{ pageData?.totalElements ?? 0 }}</strong></div></article>
    </section>
    <section class="filter-panel">
      <el-input v-model="filters.keyword" clearable placeholder="批次号 / 商品 / SKU / 采购单号" :prefix-icon="Search" @keyup.enter="search" />
      <el-input-number v-model="filters.purchaseOrderId" :min="1" :controls="false" placeholder="采购单 ID" />
      <el-select v-model="filters.status" clearable placeholder="全部批次状态"><el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-select v-model="filters.qualityStatus" clearable placeholder="全部质量状态"><el-option v-for="item in qualityStatuses" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <div class="filter-actions"><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="RefreshRight" @click="reset">重置</el-button></div>
    </section>
    <section class="table-panel">
      <el-table v-loading="loading" :data="pageData?.items ?? []" row-class-name="clickable-row" @row-click="openLot">
        <el-table-column label="批次与商品" min-width="250"><template #default="{ row }: { row: InventoryLotListItem }"><div class="product-cell"><div class="product-image"><img v-if="row.product.imageUrl" :src="row.product.imageUrl" :alt="row.product.productName"><span v-else>LOT</span></div><div><strong>{{ row.lotNo }}</strong><span>{{ row.product.productName }}</span><small>{{ row.product.variantName || row.product.supplierSku || '默认规格' }}</small></div></div></template></el-table-column>
        <el-table-column label="采购来源" min-width="190"><template #default="{ row }: { row: InventoryLotListItem }"><div class="primary-cell"><span>{{ row.purchaseSource.purchaseOrderNo }}</span><small>{{ row.purchaseSource.supplierName }}</small></div></template></el-table-column>
        <el-table-column label="质量 / 状态" min-width="170"><template #default="{ row }: { row: InventoryLotListItem }"><div class="tag-pair"><InventoryQualityTag :status="row.qualityStatus" /><InventoryLotStatusTag :status="row.status" /></div></template></el-table-column>
        <el-table-column label="库存数量" min-width="245"><template #default="{ row }: { row: InventoryLotListItem }"><div class="quantity-grid"><span><small>初始</small><strong>{{ row.initialQuantity }}</strong></span><span class="available-text"><small>可用</small><strong>{{ row.availableQuantity }}</strong></span><span><small>预留</small><strong>{{ row.reservedQuantity }}</strong></span><span class="warning-text"><small>隔离</small><strong>{{ row.quarantinedQuantity }}</strong></span></div></template></el-table-column>
        <el-table-column label="入库时间" min-width="170"><template #default="{ row }: { row: InventoryLotListItem }">{{ formatDate(row.receivedAt) }}</template></el-table-column>
        <template #empty><el-empty description="暂无匹配的库存批次" :image-size="88" /></template>
      </el-table>
      <footer class="pagination-row"><span>共 {{ pageData?.totalElements ?? 0 }} 个批次</span><el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" layout="sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="pageData?.totalElements ?? 0" @change="loadLots" /></footer>
    </section>
  </section>
</template>

<style scoped>
.inventory-workbench { display: grid; gap: 20px; }.workbench-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }.workbench-intro h2 { margin: 2px 0 4px; font-size: 24px; letter-spacing: -.02em; }.workbench-intro p { margin: 0; color: var(--erp-text-muted); }.section-kicker { color: var(--erp-accent); font-size: 10px; font-weight: 800; letter-spacing: .16em; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }.metric-grid article { display: flex; align-items: center; gap: 14px; padding: 16px 18px; border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }.metric-grid article > div { display: grid; gap: 2px; }.metric-grid small { color: var(--erp-text-muted); }.metric-grid strong { font-size: 24px; line-height: 1.1; }.metric-icon { display: grid; width: 42px; height: 42px; place-items: center; border-radius: 11px; font-size: 19px; }.metric-icon.available { color: #16775b; background: #e7f6f0; }.metric-icon.reserved { color: #275fa6; background: #eaf2fc; }.metric-icon.quarantined { color: #aa6814; background: #fff3df; }.metric-icon.lots { color: #6852a5; background: #f0ecfb; }
.filter-panel { display: grid; grid-template-columns: minmax(230px, 1.4fr) 140px 170px 160px auto; gap: 12px; padding: 18px; border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); }.filter-panel :deep(.el-input-number) { width: 100%; }.filter-actions { display: flex; gap: 8px; }
.table-panel { overflow: hidden; border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }.table-panel :deep(.clickable-row) { cursor: pointer; }.table-panel :deep(.clickable-row:hover td) { background: #f4f8f7 !important; }.product-cell { display: flex; align-items: center; gap: 11px; }.product-image { display: grid; width: 44px; height: 44px; flex: 0 0 44px; place-items: center; overflow: hidden; border-radius: 9px; color: var(--erp-text-muted); background: var(--erp-canvas); font-size: 10px; font-weight: 800; }.product-image img { width: 100%; height: 100%; object-fit: cover; }.product-cell > div:last-child, .primary-cell { display: grid; gap: 2px; }.product-cell strong { color: var(--erp-primary); }.product-cell small, .primary-cell small { color: var(--erp-text-muted); font-size: 12px; }.tag-pair { display: flex; flex-wrap: wrap; gap: 6px; }.quantity-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }.quantity-grid span { display: grid; gap: 1px; }.quantity-grid small { color: var(--erp-text-muted); font-size: 11px; }.available-text strong { color: #16775b; }.warning-text strong { color: #aa6814; }.pagination-row { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px; border-top: 1px solid var(--erp-border); color: var(--erp-text-muted); font-size: 13px; }
@media (max-width: 1180px) { .filter-panel { grid-template-columns: repeat(2, 1fr); }.filter-actions { grid-column: 1 / -1; justify-content: flex-end; } }
@media (max-width: 900px) { .metric-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 620px) { .workbench-intro { display: grid; }.metric-grid, .filter-panel { grid-template-columns: 1fr; }.filter-actions { grid-column: auto; }.filter-actions .el-button { flex: 1; }.pagination-row { align-items: flex-start; flex-direction: column; } }
</style>

