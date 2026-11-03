<script setup lang="ts">
import { Box, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { authState } from '@/api/auth'
import { ApiError } from '@/api/http'
import { createDomesticPackage, getDomesticPackages, lookupDomesticPackage, type DomesticPackageListItem, type DomesticPackagePage } from '@/api/receiving'
import ReceivingStatusTag from '@/components/receiving/ReceivingStatusTag.vue'

const router = useRouter()
const loading = ref(false)
const scanning = ref(false)
const registering = ref(false)
const registerDialog = ref(false)
const pageData = ref<DomesticPackagePage | null>(null)
const scanCode = ref('')
const filters = reactive({ page: 1, size: 20, keyword: '', status: '', sort: 'createdAt,desc' })
const packageForm = reactive({ purchaseOrderId: 0, trackingNumber: '', carrier: 'SF_EXPRESS', note: '' })
const canReceive = computed(() => authState.user?.roles.some(role => ['OWNER', 'WAREHOUSE'].includes(role)) ?? false)
const statuses = [
  { value: 'EXPECTED', label: '待到货' }, { value: 'RECEIVING', label: '收货中' },
  { value: 'RECEIVED', label: '已收货' }, { value: 'EXCEPTION', label: '异常' },
  { value: 'CANCELED', label: '已取消' },
]

async function loadPackages() {
  loading.value = true
  try {
    pageData.value = await getDomesticPackages({
      ...filters, keyword: filters.keyword.trim() || undefined, status: filters.status || undefined,
    })
  } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '包裹列表加载失败') }
  finally { loading.value = false }
}
async function scan() {
  const value = scanCode.value.trim()
  if (!value) { ElMessage.warning('请扫描或输入物流单号'); return }
  scanning.value = true
  try {
    const result = await lookupDomesticPackage(value)
    await router.push({ name: 'receiving-package-detail', params: { packageId: result.id } })
  } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '未找到该包裹') }
  finally { scanning.value = false }
}
async function registerPackage() {
  if (!Number.isInteger(packageForm.purchaseOrderId) || packageForm.purchaseOrderId <= 0
    || !packageForm.trackingNumber.trim() || !packageForm.carrier.trim()) {
    ElMessage.warning('请填写采购单 ID、物流单号和承运商'); return
  }
  registering.value = true
  try {
    const result = await createDomesticPackage({
      purchaseOrderId: packageForm.purchaseOrderId,
      trackingNumber: packageForm.trackingNumber.trim(), carrier: packageForm.carrier.trim(),
      note: packageForm.note.trim() || undefined,
    })
    registerDialog.value = false
    Object.assign(packageForm, { purchaseOrderId: 0, trackingNumber: '', carrier: 'SF_EXPRESS', note: '' })
    ElMessage.success('国内包裹登记成功')
    await router.push({ name: 'receiving-package-detail', params: { packageId: result.id } })
  } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '包裹登记失败') }
  finally { registering.value = false }
}
function search() { filters.page = 1; void loadPackages() }
function reset() { Object.assign(filters, { page: 1, size: 20, keyword: '', status: '', sort: 'createdAt,desc' }); void loadPackages() }
function openPackage(row: DomesticPackageListItem) { void router.push({ name: 'receiving-package-detail', params: { packageId: row.id } }) }
function progress(row: DomesticPackageListItem) {
  const inspected = row.acceptedQuantity + row.damagedQuantity
  return row.purchasedQuantity ? Math.min(100, Math.round(inspected / row.purchasedQuantity * 100)) : 0
}
function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
}
onMounted(loadPackages)
</script>

<template>
  <section class="receiving-workbench">
    <header class="workbench-intro">
      <div><span class="section-kicker">DOMESTIC RECEIVING</span><h2>到货收货</h2><p>扫描国内物流单，核对合格、破损与错货数量。</p></div>
      <el-button v-if="canReceive" type="primary" :icon="Plus" @click="registerDialog = true">登记包裹</el-button>
    </header>
    <section class="scan-card">
      <div class="scan-icon"><el-icon><Box /></el-icon></div>
      <div><strong>扫码收货</strong><span>光标停留在输入框，扫描后按回车进入包裹</span></div>
      <el-input v-model="scanCode" size="large" clearable placeholder="扫描或输入物流单号" aria-label="物流单号" @keyup.enter="scan" />
      <el-button type="primary" size="large" :loading="scanning" @click="scan">定位包裹</el-button>
    </section>
    <section class="filter-panel">
      <el-input v-model="filters.keyword" clearable placeholder="包裹号 / 物流单号 / 采购单号" :prefix-icon="Search" @keyup.enter="search" />
      <el-select v-model="filters.status" clearable placeholder="全部状态"><el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <div class="filter-actions"><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="RefreshRight" @click="reset">重置</el-button></div>
    </section>
    <section class="table-panel">
      <el-table v-loading="loading" :data="pageData?.items ?? []" row-class-name="clickable-row" @row-click="openPackage">
        <el-table-column label="包裹与物流" min-width="220"><template #default="{ row }: { row: DomesticPackageListItem }"><div class="primary-cell"><strong>{{ row.trackingNumber }}</strong><small>{{ row.packageNo }} · {{ row.carrier }}</small></div></template></el-table-column>
        <el-table-column label="采购单" min-width="190"><template #default="{ row }: { row: DomesticPackageListItem }"><div class="primary-cell"><span>{{ row.purchaseOrderNo }}</span><small>{{ row.supplier.name }}</small></div></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }: { row: DomesticPackageListItem }"><ReceivingStatusTag :status="row.status" /></template></el-table-column>
        <el-table-column label="验货进度" min-width="210"><template #default="{ row }: { row: DomesticPackageListItem }"><div class="progress-cell"><el-progress :percentage="progress(row)" :stroke-width="7" /><small>合格 {{ row.acceptedQuantity }} · 破损 {{ row.damagedQuantity }} / 采购 {{ row.purchasedQuantity }}</small></div></template></el-table-column>
        <el-table-column label="首次收货" min-width="170"><template #default="{ row }: { row: DomesticPackageListItem }">{{ formatDate(row.firstReceivedAt) }}</template></el-table-column>
        <template #empty><el-empty description="暂无国内包裹" :image-size="88" /></template>
      </el-table>
      <footer class="pagination-row"><span>共 {{ pageData?.totalElements ?? 0 }} 个包裹</span><el-pagination v-model:current-page="filters.page" v-model:page-size="filters.size" layout="sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" :total="pageData?.totalElements ?? 0" @change="loadPackages" /></footer>
    </section>
    <el-dialog v-model="registerDialog" title="登记国内包裹" width="min(520px, 92vw)">
      <el-form label-position="top"><el-form-item label="采购单 ID"><el-input-number v-model="packageForm.purchaseOrderId" :min="1" :controls="false" /></el-form-item><el-form-item label="物流单号"><el-input v-model="packageForm.trackingNumber" maxlength="128" /></el-form-item><el-form-item label="承运商"><el-input v-model="packageForm.carrier" maxlength="100" /></el-form-item><el-form-item label="备注"><el-input v-model="packageForm.note" type="textarea" maxlength="500" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="registerDialog = false">取消</el-button><el-button type="primary" :loading="registering" @click="registerPackage">确认登记</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.receiving-workbench { display: grid; gap: 20px; }.workbench-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }.workbench-intro h2 { margin: 2px 0 4px; font-size: 24px; letter-spacing: -.02em; }.workbench-intro p { margin: 0; color: var(--erp-text-muted); }.section-kicker { color: var(--erp-accent); font-size: 10px; font-weight: 800; letter-spacing: .16em; }
.scan-card { display: grid; grid-template-columns: 48px minmax(180px, .6fr) minmax(260px, 1fr) auto; gap: 16px; align-items: center; padding: 18px; border: 1px solid #bddbd3; border-radius: var(--erp-radius); background: linear-gradient(120deg, #f1faf7, #fff); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }.scan-icon { display: grid; width: 48px; height: 48px; place-items: center; border-radius: 12px; color: white; background: var(--erp-primary); font-size: 24px; }.scan-card > div:nth-child(2) { display: grid; gap: 3px; }.scan-card span { color: var(--erp-text-muted); font-size: 12px; }
.filter-panel { display: grid; grid-template-columns: minmax(260px, 1.4fr) minmax(160px, .6fr) auto; gap: 12px; padding: 18px; border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); }.filter-actions { display: flex; gap: 8px; }.table-panel { overflow: hidden; border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }.table-panel :deep(.clickable-row) { cursor: pointer; }.table-panel :deep(.clickable-row:hover td) { background: #f4f8f7 !important; }.primary-cell { display: grid; gap: 3px; }.primary-cell strong { color: var(--erp-primary); }.primary-cell small, .progress-cell small { color: var(--erp-text-muted); font-size: 12px; }.progress-cell { display: grid; gap: 5px; }.pagination-row { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px; border-top: 1px solid var(--erp-border); color: var(--erp-text-muted); font-size: 13px; }
@media (max-width: 900px) { .scan-card { grid-template-columns: 48px 1fr; }.scan-card .el-input { grid-column: 1 / -1; }.scan-card > .el-button { grid-column: 1 / -1; }.filter-panel { grid-template-columns: 1fr 1fr; }.filter-actions { grid-column: 1 / -1; justify-content: flex-end; } }
@media (max-width: 620px) { .workbench-intro { display: grid; }.scan-card, .filter-panel { grid-template-columns: 1fr; }.scan-icon { display: none; }.scan-card .el-input, .scan-card > .el-button, .filter-actions { grid-column: auto; }.filter-actions .el-button { flex: 1; }.pagination-row { align-items: flex-start; flex-direction: column; } }
</style>
