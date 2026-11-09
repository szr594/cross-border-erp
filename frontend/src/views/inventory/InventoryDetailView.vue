<script setup lang="ts">
import { ArrowLeft, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { authState } from '@/api/auth'
import { ApiError } from '@/api/http'
import { adjustInventory, allocateInventory, getInventoryLot, getInventoryMovements, releaseInventoryAllocation, releaseQuarantine, type InventoryAllocation, type InventoryLotDetail, type InventoryMovementPage, type InventoryMovementType } from '@/api/inventory'
import InventoryLotStatusTag from '@/components/inventory/InventoryLotStatusTag.vue'
import InventoryQualityTag from '@/components/inventory/InventoryQualityTag.vue'

type CommandKind = 'allocate' | 'adjust' | 'quarantine' | 'release'
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const movementLoading = ref(false)
const submitting = ref(false)
const detail = ref<InventoryLotDetail | null>(null)
const movements = ref<InventoryMovementPage | null>(null)
const movementPage = ref(1)
const dialogKind = ref<CommandKind | null>(null)
const releaseTarget = ref<InventoryAllocation | null>(null)
const lotId = computed(() => Number(route.params.lotId))
const canManage = computed(() => authState.user?.roles.some(role => ['OWNER', 'WAREHOUSE'].includes(role)) ?? false)
const form = reactive({ orderItemId: null as number | null, quantity: 1, adjustmentType: 'IN' as 'IN' | 'OUT', reason: '' })
const movementNames: Record<InventoryMovementType, string> = {
  RECEIPT_ACCEPTED: '合格品入库', RECEIPT_DAMAGED: '破损品入库', ORDER_ALLOCATED: '订单预留',
  ALLOCATION_RELEASED: '释放预留', PACKING_CONSUMED: '打包消耗', ADJUSTMENT_IN: '库存调增',
  ADJUSTMENT_OUT: '库存调减', QUARANTINE_RELEASED: '解除隔离',
}
const allocationNames = { RESERVED: '预留中', CONSUMED: '已消耗', RELEASED: '已释放' } as const
const dialogTitles: Record<CommandKind, string> = { allocate: '人工分配库存', adjust: '调整库存', quarantine: '解除隔离', release: '释放库存预留' }

async function loadDetail() {
  if (!Number.isInteger(lotId.value) || lotId.value <= 0) { ElMessage.error('库存批次编号无效'); await router.replace('/inventory'); return }
  loading.value = true
  try { detail.value = await getInventoryLot(lotId.value) }
  catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '库存批次加载失败') }
  finally { loading.value = false }
}
async function loadMovements(page = movementPage.value) {
  if (!Number.isInteger(lotId.value) || lotId.value <= 0) return
  movementLoading.value = true
  try { movements.value = await getInventoryMovements(lotId.value, page, 10); movementPage.value = page }
  catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '库存流水加载失败') }
  finally { movementLoading.value = false }
}
function refresh() { void Promise.all([loadDetail(), loadMovements()]) }
function openCommand(kind: CommandKind) { Object.assign(form, { orderItemId: null, quantity: 1, adjustmentType: 'IN', reason: '' }); dialogKind.value = kind }
function openRelease(allocation: InventoryAllocation) { releaseTarget.value = allocation; openCommand('release') }
function closeDialog() { dialogKind.value = null; releaseTarget.value = null }
function idempotencyKey(kind: string) { return `inventory-ui-${kind}-${lotId.value}-${crypto.randomUUID()}` }
async function submitCommand() {
  if (!detail.value || !dialogKind.value || !Number.isInteger(form.quantity) || form.quantity <= 0 || !form.reason.trim()) { ElMessage.warning('请填写有效数量和操作原因'); return }
  if (dialogKind.value === 'allocate' && (!form.orderItemId || !Number.isInteger(form.orderItemId))) { ElMessage.warning('请填写订单明细 ID'); return }
  submitting.value = true
  try {
    if (dialogKind.value === 'allocate') await allocateInventory(detail.value.id, { orderItemId: form.orderItemId as number, quantity: form.quantity, reason: form.reason.trim(), idempotencyKey: idempotencyKey('allocate'), version: detail.value.version })
    if (dialogKind.value === 'adjust') await adjustInventory(detail.value.id, { type: form.adjustmentType, quantity: form.quantity, reason: form.reason.trim(), idempotencyKey: idempotencyKey('adjust'), version: detail.value.version })
    if (dialogKind.value === 'quarantine') await releaseQuarantine(detail.value.id, { quantity: form.quantity, reason: form.reason.trim(), idempotencyKey: idempotencyKey('quarantine'), version: detail.value.version })
    if (dialogKind.value === 'release' && releaseTarget.value) await releaseInventoryAllocation(releaseTarget.value.id, { reason: form.reason.trim(), idempotencyKey: idempotencyKey(`release-${releaseTarget.value.id}`), version: releaseTarget.value.version })
    ElMessage.success('库存操作成功'); closeDialog(); refresh()
  } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '库存操作失败') }
  finally { submitting.value = false }
}
function formatDate(value: string) { return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
function movementTypeTag(type: InventoryMovementType) { return type.includes('OUT') || type.includes('CONSUMED') || type.includes('RELEASED') ? 'warning' : 'success' }
onMounted(refresh)
</script>

<template>
  <section class="detail-page">
    <div class="detail-actions"><el-button text :icon="ArrowLeft" @click="router.push('/inventory')">返回库存工作台</el-button><el-button :icon="RefreshRight" :loading="loading" @click="refresh">刷新</el-button></div>
    <template v-if="detail">
      <section class="hero-card">
        <div><span class="section-kicker">INVENTORY LOT</span><h2>{{ detail.lotNo }}</h2><p>{{ detail.product.productName }}{{ detail.product.variantName ? ` · ${detail.product.variantName}` : '' }}</p></div>
        <div class="hero-status"><div><InventoryQualityTag :status="detail.qualityStatus" /><InventoryLotStatusTag :status="detail.status" /></div><small>入库于 {{ formatDate(detail.receivedAt) }}</small></div>
      </section>
      <section class="balance-panel">
        <article><small>初始数量</small><strong>{{ detail.initialQuantity }}</strong></article><article class="available"><small>可用数量</small><strong>{{ detail.availableQuantity }}</strong></article><article><small>预留数量</small><strong>{{ detail.reservedQuantity }}</strong></article><article class="warning"><small>隔离数量</small><strong>{{ detail.quarantinedQuantity }}</strong></article><article><small>已消耗</small><strong>{{ detail.consumedQuantity }}</strong></article><article><small>已调减</small><strong>{{ detail.adjustedOutQuantity }}</strong></article>
      </section>
      <section v-if="canManage" class="command-bar"><div><strong>库存操作</strong><span>写操作会校验当前批次版本，避免覆盖其他员工的变更。</span></div><div><el-button :disabled="detail.availableQuantity <= 0" @click="openCommand('allocate')">人工分配</el-button><el-button @click="openCommand('adjust')">库存调整</el-button><el-button type="warning" plain :disabled="detail.quarantinedQuantity <= 0" @click="openCommand('quarantine')">解除隔离</el-button></div></section>
      <div class="content-grid">
        <main class="main-column">
          <section class="panel"><div class="panel-heading"><div><span class="section-kicker">ALLOCATIONS</span><h3>订单分配记录</h3></div><span>{{ detail.allocations.length }} 条</span></div><el-table :data="detail.allocations"><el-table-column prop="allocationNo" label="分配单号" min-width="170" /><el-table-column label="关联订单" min-width="160"><template #default="{ row }: { row: InventoryAllocation }"><el-button link type="primary" @click="router.push(`/orders/${row.salesOrderId}`)">{{ row.orderNo }}</el-button></template></el-table-column><el-table-column label="商品" min-width="180"><template #default="{ row }: { row: InventoryAllocation }">{{ row.productName }}{{ row.variantName ? ` · ${row.variantName}` : '' }}</template></el-table-column><el-table-column prop="quantity" label="数量" width="80" /><el-table-column label="状态" width="100"><template #default="{ row }: { row: InventoryAllocation }"><el-tag :type="row.status === 'RESERVED' ? 'primary' : row.status === 'CONSUMED' ? 'success' : 'info'">{{ allocationNames[row.status] }}</el-tag></template></el-table-column><el-table-column v-if="canManage" label="操作" width="90"><template #default="{ row }: { row: InventoryAllocation }"><el-button v-if="row.status === 'RESERVED'" link type="danger" :loading="submitting" @click="openRelease(row)">释放</el-button></template></el-table-column><template #empty><el-empty description="该批次暂无订单分配" :image-size="64" /></template></el-table></section>
          <section class="panel"><div class="panel-heading"><div><span class="section-kicker">MOVEMENT LEDGER</span><h3>库存流水</h3></div><span>共 {{ movements?.totalElements ?? 0 }} 条</span></div><el-table v-loading="movementLoading" :data="movements?.items ?? []"><el-table-column label="类型" min-width="130"><template #default="{ row }"><el-tag :type="movementTypeTag(row.movementType)">{{ movementNames[row.movementType as InventoryMovementType] }}</el-tag></template></el-table-column><el-table-column label="变动" width="85"><template #default="{ row }"><strong :class="row.quantity > 0 ? 'positive' : 'negative'">{{ row.quantity > 0 ? '+' : '' }}{{ row.quantity }}</strong></template></el-table-column><el-table-column label="变动后余额" min-width="185"><template #default="{ row }"><span class="after-balance">可用 {{ row.availableAfter }} · 预留 {{ row.reservedAfter }} · 隔离 {{ row.quarantinedAfter }}</span></template></el-table-column><el-table-column label="原因 / 操作人" min-width="190"><template #default="{ row }"><div class="primary-cell"><span>{{ row.reason || '系统业务流转' }}</span><small>{{ row.operator?.displayName || '系统' }}</small></div></template></el-table-column><el-table-column label="发生时间" min-width="170"><template #default="{ row }">{{ formatDate(row.occurredAt) }}</template></el-table-column><template #empty><el-empty description="暂无库存流水" :image-size="64" /></template></el-table><el-pagination v-if="(movements?.totalPages ?? 0) > 1" class="movement-pagination" :current-page="movementPage" :page-size="10" layout="prev, pager, next" :total="movements?.totalElements ?? 0" @current-change="loadMovements" /></section>
        </main>
        <aside class="side-column">
          <section class="panel info-panel"><div class="panel-heading"><div><span class="section-kicker">PURCHASE</span><h3>采购来源</h3></div></div><dl><div><dt>采购单</dt><dd><el-button link type="primary" @click="router.push(`/procurement/${detail.purchaseSource.purchaseOrderId}`)">{{ detail.purchaseSource.purchaseOrderNo }}</el-button></dd></div><div><dt>供应商</dt><dd>{{ detail.purchaseSource.supplierName }}</dd></div><div><dt>供应商编码</dt><dd>{{ detail.purchaseSource.supplierCode }}</dd></div><div><dt>供应商 SKU</dt><dd>{{ detail.product.supplierSku || '—' }}</dd></div></dl></section>
          <section class="panel info-panel"><div class="panel-heading"><div><span class="section-kicker">RECEIVING</span><h3>收货追溯</h3></div></div><dl><div><dt>收货批次</dt><dd><el-button link type="primary" @click="router.push(`/receiving/receipts/${detail.receiptSource.receiptId}`)">{{ detail.receiptSource.receiptNo }}</el-button></dd></div><div><dt>国内包裹</dt><dd><el-button link type="primary" @click="router.push(`/receiving/packages/${detail.receiptSource.packageId}`)">{{ detail.receiptSource.packageNo }}</el-button></dd></div><div><dt>物流单号</dt><dd>{{ detail.receiptSource.trackingNumber }}</dd></div><div><dt>承运商</dt><dd>{{ detail.receiptSource.carrier }}</dd></div></dl></section>
          <section class="panel equation-panel"><div class="panel-heading"><div><span class="section-kicker">BALANCE CHECK</span><h3>数量恒等式</h3></div></div><p><strong>{{ detail.initialQuantity }}</strong><span>= 可用 {{ detail.availableQuantity }} + 预留 {{ detail.reservedQuantity }} + 隔离 {{ detail.quarantinedQuantity }} + 已消耗 {{ detail.consumedQuantity }} + 已调减 {{ detail.adjustedOutQuantity }}</span></p><small>批次版本 v{{ detail.version }} · 更新于 {{ formatDate(detail.updatedAt) }}</small></section>
        </aside>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="库存批次不存在或暂时无法加载" /><div v-else class="loading-card" v-loading="loading" />
    <el-dialog :model-value="dialogKind !== null" :title="dialogKind ? dialogTitles[dialogKind] : ''" width="min(500px, 92vw)" @close="closeDialog"><el-alert v-if="dialogKind === 'release' && releaseTarget" class="dialog-alert" type="warning" :closable="false" :title="`将释放 ${releaseTarget.allocationNo} 的 ${releaseTarget.quantity} 件预留库存`" /><el-form label-position="top"><el-form-item v-if="dialogKind === 'allocate'" label="订单明细 ID"><el-input-number v-model="form.orderItemId" :min="1" :controls="false" /></el-form-item><el-form-item v-if="dialogKind === 'adjust'" label="调整类型"><el-radio-group v-model="form.adjustmentType"><el-radio-button value="IN">调增</el-radio-button><el-radio-button value="OUT">调减</el-radio-button></el-radio-group></el-form-item><el-form-item v-if="dialogKind !== 'release'" label="数量"><el-input-number v-model="form.quantity" :min="1" :controls="false" /></el-form-item><el-form-item label="操作原因"><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请说明本次操作原因" /></el-form-item></el-form><template #footer><el-button @click="closeDialog">取消</el-button><el-button type="primary" :loading="submitting" @click="submitCommand">确认提交</el-button></template></el-dialog>
  </section>
</template>

<style scoped>
.detail-page { display: grid; gap: 18px; }.detail-actions { display: flex; justify-content: space-between; }.section-kicker { color: var(--erp-accent); font-size: 10px; font-weight: 800; letter-spacing: .16em; }.hero-card, .panel, .balance-panel, .command-bar { border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }.hero-card { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 24px 28px; border-left: 4px solid var(--erp-primary); }.hero-card h2 { margin: 2px 0; font-size: 26px; }.hero-card p { margin: 0; color: var(--erp-text-muted); }.hero-status { display: grid; justify-items: end; gap: 8px; }.hero-status > div { display: flex; gap: 6px; }.hero-status small { color: var(--erp-text-muted); }
.balance-panel { display: grid; grid-template-columns: repeat(6, 1fr); overflow: hidden; }.balance-panel article { display: grid; gap: 4px; padding: 18px 20px; border-right: 1px solid var(--erp-border); }.balance-panel article:last-child { border-right: 0; }.balance-panel small { color: var(--erp-text-muted); }.balance-panel strong { font-size: 25px; }.balance-panel .available strong { color: #16775b; }.balance-panel .warning strong { color: #aa6814; }.command-bar { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 15px 18px; border-color: #bddbd3; background: linear-gradient(120deg, #f1faf7, #fff); }.command-bar > div:first-child { display: grid; gap: 2px; }.command-bar span { color: var(--erp-text-muted); font-size: 12px; }.command-bar > div:last-child { display: flex; gap: 8px; }
.content-grid { display: grid; grid-template-columns: minmax(0, 1fr) 310px; gap: 18px; align-items: start; }.main-column, .side-column { display: grid; gap: 18px; }.panel { overflow: hidden; }.panel-heading { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px; border-bottom: 1px solid var(--erp-border); }.panel-heading h3 { margin: 1px 0 0; font-size: 17px; }.panel-heading > span { color: var(--erp-text-muted); font-size: 12px; }.primary-cell { display: grid; gap: 2px; }.primary-cell small, .after-balance { color: var(--erp-text-muted); font-size: 12px; }.positive { color: #16775b; }.negative { color: #b44a45; }.movement-pagination { justify-content: flex-end; padding: 14px 18px; border-top: 1px solid var(--erp-border); }
.info-panel dl { margin: 0; padding: 8px 20px 18px; }.info-panel dl div { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 9px 0; border-bottom: 1px dashed var(--erp-border); }.info-panel dt { color: var(--erp-text-muted); }.info-panel dd { margin: 0; text-align: right; }.equation-panel > p { display: grid; gap: 8px; margin: 0; padding: 18px 20px 8px; }.equation-panel > p strong { color: var(--erp-primary); font-size: 28px; }.equation-panel > p span, .equation-panel > small { color: var(--erp-text-muted); font-size: 12px; line-height: 1.7; }.equation-panel > small { display: block; padding: 0 20px 18px; }.loading-card { min-height: 320px; }.el-input-number { width: 100%; }.dialog-alert { margin-bottom: 18px; }
@media (max-width: 1100px) { .balance-panel { grid-template-columns: repeat(3, 1fr); }.balance-panel article:nth-child(3) { border-right: 0; }.balance-panel article:nth-child(-n+3) { border-bottom: 1px solid var(--erp-border); }.content-grid { grid-template-columns: 1fr; }.side-column { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 760px) { .hero-card, .command-bar { align-items: flex-start; flex-direction: column; }.hero-status { justify-items: start; }.side-column { grid-template-columns: 1fr; } }
@media (max-width: 560px) { .balance-panel { grid-template-columns: repeat(2, 1fr); }.balance-panel article { border-bottom: 1px solid var(--erp-border); }.balance-panel article:nth-child(2n) { border-right: 0; }.command-bar > div:last-child { width: 100%; flex-direction: column; }.command-bar .el-button { margin: 0; } }
</style>

