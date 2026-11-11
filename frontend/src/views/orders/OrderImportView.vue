<script setup lang="ts">
import { DocumentAdd, Download, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  confirmOrderImport,
  getOrderImportErrors,
  previewOrderImport,
  type OrderImportErrorPage,
  type OrderImportResult,
} from '@/api/orderImports'

const router = useRouter()
const file = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const previewing = ref(false)
const confirming = ref(false)
const result = ref<OrderImportResult | null>(null)
const errors = ref<OrderImportErrorPage | null>(null)
const errorPage = ref(1)
const canConfirm = computed(() => result.value?.status === 'VALIDATED' && result.value.errorCount === 0)

const headers = [
  'store_code', 'external_order_id', 'display_order_no', 'external_status', 'ordered_at',
  'requested_ship_at', 'currency', 'responsible_login', 'customer_id', 'customer_name',
  'recipient_name', 'recipient_phone', 'country_code', 'province', 'city', 'district',
  'address_line1', 'address_line2', 'postal_code', 'external_item_id', 'external_product_id',
  'external_variant_id', 'external_sku', 'product_name', 'variant_name', 'image_url',
  'quantity', 'unit_price_minor', 'note',
]

function chooseFile() { fileInput.value?.click() }
function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null
  result.value = null
  errors.value = null
}

async function preview() {
  if (!file.value) return ElMessage.warning('请先选择 CSV 文件')
  previewing.value = true
  try {
    result.value = await previewOrderImport(file.value)
    errorPage.value = 1
    await loadErrors(1)
    if (result.value.errorCount) ElMessage.warning(`预检发现 ${result.value.errorCount} 个问题`)
    else ElMessage.success('预检通过，可以确认导入')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : 'CSV 预检失败')
  } finally { previewing.value = false }
}

async function loadErrors(page: number) {
  if (!result.value || result.value.errorCount === 0) { errors.value = null; return }
  errorPage.value = page
  errors.value = await getOrderImportErrors(result.value.importId, page, 20)
}

async function confirmImport() {
  if (!result.value || !canConfirm.value) return
  await ElMessageBox.confirm(
    `将创建 ${result.value.creatableOrderCount} 个订单，跳过 ${result.value.skippedOrderCount} 个完全一致的订单。确认继续吗？`,
    '确认订单导入',
    { confirmButtonText: '确认导入', cancelButtonText: '取消', type: 'warning' },
  )
  confirming.value = true
  try {
    result.value = await confirmOrderImport(result.value.importId)
    ElMessage.success('订单导入完成')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '确认导入失败')
  } finally { confirming.value = false }
}

function downloadTemplate() {
  const example = [
    'DEMO-FAIRE', 'CSV-DEMO-10001', 'F-CSV-10001', 'PROCESSING', '2026-11-11T02:30:00Z',
    '2026-11-18T02:30:00Z', 'USD', 'owner', 'DEMO-CUSTOMER-CSV', '演示客户',
    'Demo Recipient', '+1 555 0199', 'US', 'California', 'San Francisco', '',
    '99 Market Street', '', '94105', 'CSV-ITEM-10001', 'CSV-PRODUCT-10001',
    'CSV-VARIANT-10001', 'SKU-CSV-DEMO', 'CSV 演示商品', '蓝色', '', '2', '2599', '仅供虚构演示',
  ]
  const escape = (value: string) => `"${value.replace(/"/g, '""')}"`
  const blob = new Blob([`\uFEFF${headers.join(',')}\r\n${example.map(escape).join(',')}\r\n`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'order-import-template-v1.csv'
  anchor.click()
  URL.revokeObjectURL(url)
}

function statusLabel(status: OrderImportResult['status']) {
  return ({ VALIDATING: '校验中', VALIDATED: '预检通过', INVALID: '预检失败', IMPORTING: '导入中',
    COMPLETED: '导入完成', FAILED: '导入失败', EXPIRED: '已过期' } as const)[status]
}
</script>

<template>
  <section class="import-page">
    <header class="workbench-intro">
      <div><span class="section-kicker">CSV ORDER IMPORT</span><h2>订单批量导入</h2><p>先预检，再确认写入；不会通过 CSV 覆盖已有订单。</p></div>
      <el-button :icon="Download" @click="downloadTemplate">下载模板 v1</el-button>
    </header>

    <el-alert title="仅使用虚构演示数据" description="文件必须为 UTF-8 编码，最大 5 MB、10000 行。每行代表一个订单商品。" type="warning" show-icon :closable="false" />

    <section class="upload-card">
      <div class="upload-icon"><el-icon><UploadFilled /></el-icon></div>
      <div class="upload-copy"><strong>{{ file?.name || '选择 CSV 订单文件' }}</strong><span>{{ file ? `${(file.size / 1024).toFixed(1)} KB` : '支持带 BOM 的 UTF-8 CSV' }}</span></div>
      <input ref="fileInput" class="file-input" type="file" accept=".csv,text/csv" @change="selectFile">
      <el-button @click="chooseFile">选择文件</el-button>
      <el-button type="primary" :icon="DocumentAdd" :loading="previewing" :disabled="!file" @click="preview">开始预检</el-button>
    </section>

    <template v-if="result">
      <section class="result-card">
        <div class="result-heading"><div><span class="section-kicker">PREVIEW RESULT</span><h3>{{ result.fileName }}</h3></div><el-tag :type="result.status === 'COMPLETED' || result.status === 'VALIDATED' ? 'success' : 'danger'" size="large">{{ statusLabel(result.status) }}</el-tag></div>
        <div class="metrics">
          <div><span>数据行</span><strong>{{ result.totalRows }}</strong></div>
          <div><span>订单</span><strong>{{ result.orderCount }}</strong></div>
          <div><span>可创建</span><strong>{{ result.creatableOrderCount }}</strong></div>
          <div><span>幂等跳过</span><strong>{{ result.skippedOrderCount }}</strong></div>
          <div :class="{ danger: result.errorCount > 0 }"><span>错误</span><strong>{{ result.errorCount }}</strong></div>
        </div>
        <div class="result-actions">
          <span v-if="result.status === 'VALIDATED'">预检结果有效至 {{ new Date(result.expiresAt).toLocaleString('zh-CN') }}</span>
          <span v-else-if="result.status === 'COMPLETED'">本批次已经成功写入业务订单。</span>
          <span v-else>请修正下面的错误后重新上传预检。</span>
          <div><el-button v-if="result.status === 'COMPLETED'" type="primary" @click="router.push('/orders')">查看订单</el-button><el-button v-else type="primary" :loading="confirming" :disabled="!canConfirm" @click="confirmImport">确认导入</el-button></div>
        </div>
      </section>

      <section v-if="errors" class="error-card">
        <div class="panel-heading"><div><span class="section-kicker">VALIDATION ERRORS</span><h3>预检错误</h3></div><span>共 {{ errors.totalElements }} 条</span></div>
        <el-table :data="errors.items"><el-table-column prop="rowNumber" label="CSV 行" width="90" /><el-table-column prop="field" label="字段" min-width="150"><template #default="{ row }">{{ row.field || '文件' }}</template></el-table-column><el-table-column prop="code" label="错误码" min-width="190" /><el-table-column prop="message" label="说明" min-width="280" /></el-table>
        <el-pagination v-if="errors.totalPages > 1" :current-page="errorPage" :page-size="20" layout="prev, pager, next" :total="errors.totalElements" @current-change="loadErrors" />
      </section>
    </template>
  </section>
</template>

<style scoped>
.import-page { display: grid; gap: 20px; max-width: 1100px; }.workbench-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }.workbench-intro h2 { margin: 2px 0 4px; font-size: 24px; }.workbench-intro p { margin: 0; color: var(--erp-text-muted); }.section-kicker { color: var(--erp-accent); font-size: 10px; font-weight: 800; letter-spacing: .16em; }.upload-card, .result-card, .error-card { border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }.upload-card { display: grid; grid-template-columns: auto minmax(220px, 1fr) auto auto; gap: 12px; align-items: center; padding: 22px; }.upload-icon { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 12px; color: var(--erp-primary); background: #e9f3f0; font-size: 24px; }.upload-copy { display: grid; gap: 4px; }.upload-copy span, .result-actions > span { color: var(--erp-text-muted); font-size: 13px; }.file-input { display: none; }.result-card { padding: 22px; }.result-heading, .result-actions, .panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; }.result-heading h3, .panel-heading h3 { margin: 2px 0 0; }.metrics { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin: 22px 0; }.metrics > div { display: grid; gap: 6px; padding: 14px; border-radius: 10px; background: var(--erp-canvas); }.metrics span { color: var(--erp-text-muted); font-size: 12px; }.metrics strong { color: var(--erp-primary); font-size: 24px; }.metrics .danger strong { color: var(--el-color-danger); }.panel-heading { padding: 18px 20px; border-bottom: 1px solid var(--erp-border); }.panel-heading > span { color: var(--erp-text-muted); }.error-card { overflow: hidden; }.error-card :deep(.el-pagination) { justify-content: flex-end; padding: 16px 20px; }
@media (max-width: 760px) { .workbench-intro, .result-actions { align-items: flex-start; flex-direction: column; }.upload-card { grid-template-columns: auto 1fr; }.upload-card > .el-button { grid-column: span 2; }.metrics { grid-template-columns: repeat(2, 1fr); } }
</style>
