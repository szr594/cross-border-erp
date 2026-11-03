<script setup lang="ts">
import { ArrowLeft, Plus, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { confirmGoodsReceipt, getDomesticPackage, type DomesticPackageDetail } from '@/api/receiving'
import ReceivingStatusTag from '@/components/receiving/ReceivingStatusTag.vue'

interface ItemInput { purchaseOrderItemId: number; acceptedQuantity: number; damagedQuantity: number; note: string }
interface UnexpectedInput { description: string; supplierSku: string; quantity: number; note: string }
const route = useRoute()
const router = useRouter()
const detail = ref<DomesticPackageDetail | null>(null)
const loading = ref(false)
const submitting = ref(false)
const receiptDialog = ref(false)
const itemInputs = ref<ItemInput[]>([])
const unexpectedInputs = ref<UnexpectedInput[]>([])
const receiptNote = ref('')
const packageId = computed(() => Number(route.params.packageId))
const inspectedTotal = computed(() => detail.value?.items.reduce((sum, item) => sum + item.acceptedQuantity + item.damagedQuantity, 0) ?? 0)
const purchasedTotal = computed(() => detail.value?.items.reduce((sum, item) => sum + item.purchasedQuantity, 0) ?? 0)

async function loadDetail() {
  if (!Number.isInteger(packageId.value) || packageId.value <= 0) { await router.replace('/receiving'); return }
  loading.value = true
  try { detail.value = await getDomesticPackage(packageId.value) }
  catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '包裹详情加载失败') }
  finally { loading.value = false }
}
function openReceipt() {
  if (!detail.value) return
  itemInputs.value = detail.value.items.filter(item => item.remainingQuantity > 0).map(item => ({
    purchaseOrderItemId: item.id, acceptedQuantity: 0, damagedQuantity: 0, note: '',
  }))
  unexpectedInputs.value = []
  receiptNote.value = ''
  receiptDialog.value = true
}
function addUnexpected() { unexpectedInputs.value.push({ description: '', supplierSku: '', quantity: 1, note: '' }) }
function removeUnexpected(index: number) { unexpectedInputs.value.splice(index, 1) }
async function submitReceipt() {
  if (!detail.value) return
  const items = itemInputs.value.filter(item => item.acceptedQuantity + item.damagedQuantity > 0)
  if (!items.length && !unexpectedInputs.value.length) { ElMessage.warning('请至少录入一条合格、破损或错货明细'); return }
  if (items.some(item => item.damagedQuantity > 0 && !item.note.trim())) { ElMessage.warning('破损商品必须填写说明'); return }
  if (unexpectedInputs.value.some(item => !item.description.trim() || item.quantity <= 0)) { ElMessage.warning('请完整填写错货描述和数量'); return }
  submitting.value = true
  try {
    const result = await confirmGoodsReceipt(packageId.value, {
      idempotencyKey: crypto.randomUUID(), packageVersion: detail.value.version,
      items: items.map(item => ({ ...item, note: item.note.trim() || undefined })),
      unexpectedItems: unexpectedInputs.value.map(item => ({
        description: item.description.trim(), supplierSku: item.supplierSku.trim() || undefined,
        quantity: item.quantity, note: item.note.trim() || undefined,
      })), note: receiptNote.value.trim() || undefined,
    })
    receiptDialog.value = false
    ElMessage.success('收货确认成功')
    await loadDetail()
    await router.push({ name: 'goods-receipt-detail', params: { receiptId: result.receiptId } })
  } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '收货确认失败') }
  finally { submitting.value = false }
}
function itemName(id: number) { return detail.value?.items.find(item => item.id === id)?.productName ?? `商品 #${id}` }
function remaining(id: number) { return detail.value?.items.find(item => item.id === id)?.remainingQuantity ?? 0 }
function formatDate(value: string | null) { return value ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—' }
onMounted(loadDetail)
</script>

<template>
  <section class="detail-page">
    <div class="detail-actions"><el-button text :icon="ArrowLeft" @click="router.push('/receiving')">返回收货工作台</el-button><el-button :icon="RefreshRight" :loading="loading" @click="loadDetail">刷新</el-button></div>
    <template v-if="detail">
      <section class="hero-card">
        <div><span class="section-kicker">{{ detail.carrier }}</span><h2>{{ detail.trackingNumber }}</h2><p>{{ detail.packageNo }} · {{ detail.purchaseOrderNo }} · {{ detail.supplier.name }}</p></div>
        <div class="hero-status"><ReceivingStatusTag :status="detail.status" /><strong>{{ inspectedTotal }} / {{ purchasedTotal }}</strong><small>累计验货进度</small><el-button v-if="detail.availableActions.includes('CONFIRM_RECEIPT')" type="primary" @click="openReceipt">确认本次收货</el-button></div>
      </section>
      <div class="content-grid">
        <main class="main-column">
          <section class="panel"><div class="panel-heading"><div><span class="section-kicker">INSPECTION</span><h3>采购商品验货</h3></div><span>{{ detail.items.length }} 款</span></div>
            <el-table :data="detail.items"><el-table-column label="商品" min-width="220"><template #default="{ row }"><div class="primary-cell"><strong>{{ row.productName }}</strong><small>{{ row.variantName || row.supplierSku || '默认规格' }}</small></div></template></el-table-column><el-table-column prop="purchasedQuantity" label="采购" width="80" align="center" /><el-table-column prop="acceptedQuantity" label="合格" width="80" align="center" /><el-table-column prop="damagedQuantity" label="破损" width="80" align="center" /><el-table-column prop="remainingQuantity" label="待验" width="80" align="center" /></el-table>
          </section>
          <section class="panel"><div class="panel-heading"><div><span class="section-kicker">RECEIPT HISTORY</span><h3>分次收货记录</h3></div><span>{{ detail.receipts.length }} 次</span></div>
            <div v-if="detail.receipts.length" class="receipt-list"><button v-for="receipt in detail.receipts" :key="receipt.id" type="button" @click="router.push({ name: 'goods-receipt-detail', params: { receiptId: receipt.id } })"><div><strong>{{ receipt.receiptNo }}</strong><small>{{ formatDate(receipt.confirmedAt) }} · {{ receipt.operatorName }}</small></div><span>合格 {{ receipt.acceptedQuantity }}</span><span :class="{ alert: receipt.damagedQuantity > 0 }">破损 {{ receipt.damagedQuantity }}</span><span :class="{ alert: receipt.unexpectedQuantity > 0 }">错货 {{ receipt.unexpectedQuantity }}</span></button></div><el-empty v-else description="尚未确认收货" :image-size="72" />
          </section>
        </main>
        <aside class="side-column"><section class="panel info-panel"><div class="panel-heading"><div><span class="section-kicker">PACKAGE</span><h3>包裹信息</h3></div></div><dl><div><dt>采购单</dt><dd>{{ detail.purchaseOrderNo }}</dd></div><div><dt>采购状态</dt><dd>{{ detail.purchaseOrderStatus }}</dd></div><div><dt>登记人</dt><dd>{{ detail.createdByName }}</dd></div><div><dt>预计到货</dt><dd>{{ formatDate(detail.expectedAt) }}</dd></div><div><dt>首次收货</dt><dd>{{ formatDate(detail.firstReceivedAt) }}</dd></div><div><dt>版本</dt><dd>v{{ detail.version }}</dd></div></dl></section><section class="panel note-panel"><div class="panel-heading"><div><span class="section-kicker">NOTE</span><h3>包裹备注</h3></div></div><p>{{ detail.note || '暂无备注' }}</p></section></aside>
      </div>
    </template>
    <div v-else class="loading-card" v-loading="loading" />

    <el-dialog v-model="receiptDialog" title="确认本次收货" width="min(860px, 95vw)" :close-on-click-modal="false">
      <el-alert title="提交后不可修改；合格品计入采购收货数量，破损和错货只保留追溯事实。" type="warning" :closable="false" show-icon />
      <div class="receipt-form"><h4>采购商品</h4><div v-for="input in itemInputs" :key="input.purchaseOrderItemId" class="receipt-row"><div><strong>{{ itemName(input.purchaseOrderItemId) }}</strong><small>剩余待验 {{ remaining(input.purchaseOrderItemId) }}</small></div><label>合格<el-input-number v-model="input.acceptedQuantity" :min="0" :max="remaining(input.purchaseOrderItemId)" /></label><label>破损<el-input-number v-model="input.damagedQuantity" :min="0" :max="remaining(input.purchaseOrderItemId)" /></label><el-input v-model="input.note" placeholder="破损时必填说明" maxlength="500" /></div><div class="section-title"><h4>错货</h4><el-button text type="primary" :icon="Plus" @click="addUnexpected">添加错货</el-button></div><div v-for="(input, index) in unexpectedInputs" :key="index" class="unexpected-row"><el-input v-model="input.description" placeholder="错货描述" /><el-input v-model="input.supplierSku" placeholder="供应商 SKU（可选）" /><el-input-number v-model="input.quantity" :min="1" /><el-button type="danger" text @click="removeUnexpected(index)">删除</el-button></div><el-input v-model="receiptNote" type="textarea" placeholder="本次收货备注（可选）" maxlength="500" show-word-limit /></div>
      <template #footer><el-button @click="receiptDialog = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitReceipt">确认并入账</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.detail-page { display: grid; gap: 18px; }.detail-actions { display: flex; justify-content: space-between; }.section-kicker { color: var(--erp-accent); font-size: 10px; font-weight: 800; letter-spacing: .16em; }.hero-card, .panel { border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }.hero-card { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 24px 28px; border-left: 4px solid var(--erp-primary); }.hero-card h2 { margin: 2px 0; font-size: 26px; }.hero-card p { margin: 0; color: var(--erp-text-muted); }.hero-status { display: grid; justify-items: end; gap: 5px; }.hero-status strong { font-size: 22px; }.hero-status small { color: var(--erp-text-muted); }.content-grid { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 18px; align-items: start; }.main-column, .side-column { display: grid; gap: 18px; }.panel { overflow: hidden; }.panel-heading { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px; border-bottom: 1px solid var(--erp-border); }.panel-heading h3 { margin: 1px 0 0; font-size: 17px; }.panel-heading > span { color: var(--erp-text-muted); }.primary-cell { display: grid; gap: 3px; }.primary-cell strong { color: var(--erp-primary); }.primary-cell small { color: var(--erp-text-muted); }.receipt-list { display: grid; }.receipt-list button { display: grid; grid-template-columns: minmax(180px, 1fr) repeat(3, auto); gap: 18px; align-items: center; padding: 16px 20px; border: 0; border-bottom: 1px solid var(--erp-border); background: white; cursor: pointer; text-align: left; transition: background 180ms ease-out; }.receipt-list button:hover { background: #f4f8f7; }.receipt-list button > div { display: grid; gap: 3px; }.receipt-list small { color: var(--erp-text-muted); }.receipt-list .alert { color: var(--el-color-danger); font-weight: 700; }.info-panel dl { margin: 0; padding: 8px 20px 18px; }.info-panel dl div { display: flex; justify-content: space-between; gap: 12px; padding: 9px 0; border-bottom: 1px dashed var(--erp-border); }.info-panel dt { color: var(--erp-text-muted); }.info-panel dd { margin: 0; text-align: right; }.note-panel p { margin: 0; padding: 18px 20px; color: var(--erp-text-muted); }.loading-card { min-height: 360px; }.receipt-form { display: grid; gap: 14px; margin-top: 18px; }.receipt-form h4 { margin: 0; }.receipt-row { display: grid; grid-template-columns: minmax(180px, 1fr) 140px 140px minmax(180px, 1fr); gap: 12px; align-items: end; padding: 14px; border-radius: 8px; background: var(--erp-canvas); }.receipt-row > div, .receipt-row label { display: grid; gap: 5px; }.receipt-row small { color: var(--erp-text-muted); }.section-title { display: flex; align-items: center; justify-content: space-between; }.unexpected-row { display: grid; grid-template-columns: 1fr 1fr 120px auto; gap: 10px; }
@media (max-width: 1050px) { .content-grid { grid-template-columns: 1fr; }.side-column { grid-template-columns: 1fr 1fr; }.receipt-row { grid-template-columns: 1fr 1fr; } }
@media (max-width: 680px) { .hero-card { align-items: flex-start; flex-direction: column; }.hero-status { justify-items: start; }.side-column, .receipt-row, .unexpected-row { grid-template-columns: 1fr; }.receipt-list button { grid-template-columns: 1fr 1fr; }.receipt-list button > div { grid-column: span 2; } }
</style>
