<script setup lang="ts">
import { ArrowLeft, Clock, Location, Phone, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { authState } from '@/api/auth'
import {
  cancelOrder, changeResponsibleUser, createProcurementRequests, getOrderDetail, getOrderEvents,
  getOrderFilterOptions, getOrderFulfillment, getOrderProcurementRequests, openOrderException,
  resolveOrderException, withdrawProcurementRequest, type OrderDetail, type OrderEventPage,
  type OrderFilterOptions, type OrderFulfillment, type OrderItem, type OrderProcurementRequest,
  type OrderProcurementRequestPage, type ProcurementItemCommand,
} from '@/api/orders'
import CancelOrderDialog from '@/components/orders/CancelOrderDialog.vue'
import OrderExceptionDialog from '@/components/orders/OrderExceptionDialog.vue'
import OrderStageTag from '@/components/orders/OrderStageTag.vue'
import ProcurementRequestDialog from '@/components/orders/ProcurementRequestDialog.vue'
import ProcurementRequestPanel from '@/components/orders/ProcurementRequestPanel.vue'
import ResponsibleUserDialog from '@/components/orders/ResponsibleUserDialog.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const eventsLoading = ref(false)
const detail = ref<OrderDetail | null>(null)
const fulfillment = ref<OrderFulfillment | null>(null)
const events = ref<OrderEventPage | null>(null)
const procurementRequests = ref<OrderProcurementRequestPage | null>(null)
const eventPage = ref(1)
const procurementPage = ref(1)
const procurementLoading = ref(false)
const commandLoading = ref(false)
const responsibleDialog = ref(false)
const procurementDialog = ref(false)
const exceptionDialog = ref(false)
const cancelDialog = ref(false)
const options = ref<OrderFilterOptions>({ stores: [], responsibleUsers: [], externalStatuses: [], fulfillmentStages: [] })
const orderId = computed(() => Number(route.params.orderId))
const canViewProcurement = computed(() => authState.user?.roles.some(
  (role) => ['OWNER', 'OPERATIONS', 'PROCUREMENT'].includes(role),
) ?? false)
const fullAddress = computed(() => {
  const customer = detail.value?.customer
  if (!customer) return ''
  return [customer.addressLine1, customer.addressLine2, customer.district, customer.city, customer.province, customer.postalCode, customer.countryCode].filter(Boolean).join(' · ')
})

async function loadOrder() {
  if (!Number.isInteger(orderId.value) || orderId.value < 1) {
    ElMessage.error('订单编号无效')
    await router.replace('/orders')
    return
  }
  loading.value = true
  try {
    const [detailResult, fulfillmentResult, eventResult] = await Promise.all([
      getOrderDetail(orderId.value), getOrderFulfillment(orderId.value), getOrderEvents(orderId.value, 1, 10),
    ])
    detail.value = detailResult
    fulfillment.value = fulfillmentResult
    events.value = eventResult
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '订单详情加载失败')
  } finally { loading.value = false }
}

async function loadOptions() {
  try { options.value = await getOrderFilterOptions() }
  catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '员工选项加载失败') }
}

async function runCommand(action: () => Promise<unknown>, successMessage: string, close: () => void) {
  commandLoading.value = true
  try {
    await action()
    close()
    ElMessage.success(successMessage)
    await Promise.all([loadOrder(), loadProcurementRequests()])
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      ElMessage.warning('订单数据已经变化，已为你刷新，请确认后重试')
      close()
      await Promise.all([loadOrder(), loadProcurementRequests()])
    } else {
      ElMessage.error(error instanceof ApiError ? error.message : '操作失败，请稍后重试')
    }
  } finally { commandLoading.value = false }
}

function submitResponsible(value: { userId: number; reason: string }) {
  if (!detail.value) return
  void runCommand(
    () => changeResponsibleUser(orderId.value, { ...value, version: detail.value!.version }),
    '负责人已更新', () => { responsibleDialog.value = false },
  )
}

function submitProcurement(value: { items: ProcurementItemCommand[]; note: string }) {
  if (!detail.value) return
  void runCommand(
    () => createProcurementRequests(orderId.value, { ...value, version: detail.value!.version }),
    '采购申请已创建', () => { procurementDialog.value = false },
  )
}

function submitException(text: string) {
  if (!detail.value) return
  const action = detail.value.exception
    ? () => resolveOrderException(orderId.value, { resolution: text, version: detail.value!.version })
    : () => openOrderException(orderId.value, { reason: text, version: detail.value!.version })
  void runCommand(action, detail.value.exception ? '履约异常已解决' : '履约异常已上报', () => { exceptionDialog.value = false })
}

function submitCancellation(reason: string) {
  if (!detail.value) return
  void runCommand(
    () => cancelOrder(orderId.value, { reason, version: detail.value!.version }),
    '订单已取消', () => { cancelDialog.value = false },
  )
}

async function loadEvents(page: number) {
  eventsLoading.value = true
  try { events.value = await getOrderEvents(orderId.value, page, 10); eventPage.value = page }
  catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '事件记录加载失败') }
  finally { eventsLoading.value = false }
}

async function loadProcurementRequests(page = procurementPage.value) {
  if (!canViewProcurement.value) return
  procurementLoading.value = true
  try {
    procurementRequests.value = await getOrderProcurementRequests(orderId.value, page, 10)
    procurementPage.value = page
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '采购申请记录加载失败')
  } finally { procurementLoading.value = false }
}

async function confirmWithdraw(request: OrderProcurementRequest) {
  try {
    const result = await ElMessageBox.prompt(
      `撤回“${request.orderItem.productName}”的采购申请后，数量将重新计入采购缺口。`,
      '撤回采购申请',
      {
        type: 'warning', inputPlaceholder: '填写撤回原因', inputType: 'textarea',
        inputValidator: (value) => value.trim().length > 0 || '请填写撤回原因',
        confirmButtonText: '确认撤回', cancelButtonText: '返回', confirmButtonClass: 'el-button--danger',
      },
    )
    void runCommand(
      () => withdrawProcurementRequest(orderId.value, request.id, {
        reason: result.value.trim(), version: request.version,
      }),
      '采购申请已撤回', () => undefined,
    )
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('撤回确认未完成')
  }
}

function formatDate(value: string | null | undefined) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
function money(minor: number | null, currency: string | null) {
  if (minor === null || !currency) return '—'
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(minor / 100)
}
function progress(item: OrderItem) {
  if (item.quantity <= 0) return 0
  return Math.max(0, Math.min(100, Math.round(((item.quantity - item.missingQuantity) / item.quantity) * 100)))
}
function eventName(type: string) {
  const names: Record<string, string> = {
    ORDER_IMPORTED: '订单导入', PROCUREMENT_REQUESTED: '发起采购申请', RESPONSIBLE_CHANGED: '负责人变更',
    EXCEPTION_OPENED: '标记异常', EXCEPTION_RESOLVED: '解决异常', ORDER_CANCELED: '订单取消',
  }
  return names[type] ?? type
}

onMounted(() => { void Promise.all([loadOrder(), loadOptions(), loadProcurementRequests()]) })
</script>

<template>
  <section class="detail-page" v-loading="loading">
    <div class="detail-toolbar">
      <el-button text :icon="ArrowLeft" @click="router.push('/orders')">返回订单工作台</el-button>
      <div v-if="detail" class="toolbar-actions">
        <el-button v-if="detail.availableActions.includes('CHANGE_RESPONSIBLE')" @click="responsibleDialog = true">调整负责人</el-button>
        <el-button v-if="detail.availableActions.includes('REQUEST_PROCUREMENT')" type="primary" plain @click="procurementDialog = true">申请采购</el-button>
        <el-button v-if="detail.availableActions.includes('OPEN_EXCEPTION') || detail.availableActions.includes('RESOLVE_EXCEPTION')" :type="detail.exception ? 'success' : 'warning'" plain @click="exceptionDialog = true">{{ detail.exception ? '解决异常' : '标记异常' }}</el-button>
        <el-button v-if="detail.availableActions.includes('CANCEL')" type="danger" plain @click="cancelDialog = true">取消订单</el-button>
      </div>
    </div>

    <template v-if="detail && fulfillment">
      <header class="detail-hero">
        <div><span class="section-kicker">{{ detail.store.name }}</span><h2>{{ detail.orderNo }}</h2><p>外部单号 {{ detail.displayOrderNo || detail.externalOrderId }}</p></div>
        <div class="hero-status"><OrderStageTag :stage="fulfillment.stage" :label="fulfillment.stageName" /><span>更新版本 v{{ detail.version }}</span></div>
      </header>

      <el-alert v-if="detail.exception" class="exception-alert" type="error" :closable="false" show-icon :title="detail.exceptionReason || '该订单存在待处理异常'" />

      <div class="facts-grid">
        <div class="fact-card"><span>下单时间</span><strong>{{ formatDate(detail.orderedAt) }}</strong></div>
        <div class="fact-card"><span>要求发货</span><strong>{{ formatDate(detail.requestedShipAt) }}</strong></div>
        <div class="fact-card"><span>负责人</span><strong>{{ detail.responsibleUser?.displayName || '待分配' }}</strong></div>
        <div class="fact-card"><span>外部状态</span><strong>{{ detail.externalStatus }}</strong></div>
      </div>

      <div class="detail-layout">
        <main class="detail-main">
          <section class="panel">
            <div class="panel-heading"><div><span class="section-kicker">FULFILLMENT</span><h3>商品履约进度</h3></div><span>{{ fulfillment.items.length }} 款商品</span></div>
            <div class="item-list">
              <article v-for="item in fulfillment.items" :key="item.id" class="fulfillment-item">
                <div class="item-identity"><div class="item-thumb">{{ item.productName.slice(0, 1) }}</div><div><strong>{{ item.productName }}</strong><small>{{ item.externalSku || '无 SKU' }}<template v-if="item.variantName"> · {{ item.variantName }}</template></small></div></div>
                <div class="item-progress"><div class="progress-copy"><span>已覆盖 {{ item.quantity - item.missingQuantity }} / {{ item.quantity }}</span><strong>{{ progress(item) }}%</strong></div><el-progress :percentage="progress(item)" :show-text="false" :stroke-width="8" /></div>
                <div class="quantity-grid"><span>库存分配<strong>{{ item.inventoryAllocatedQuantity }}</strong></span><span>采购申请<strong>{{ item.procurementRequestedQuantity }}</strong></span><span>采购中<strong>{{ item.procurementInProgressQuantity }}</strong></span><span>仍缺<strong class="missing">{{ item.missingQuantity }}</strong></span></div>
                <div class="item-price">{{ money(item.unitPriceMinor, item.currency) }}<small>× {{ item.quantity }}</small></div>
              </article>
            </div>
          </section>

          <ProcurementRequestPanel v-if="canViewProcurement" :page="procurementRequests" :loading="procurementLoading" @page-change="loadProcurementRequests" @withdraw="confirmWithdraw" />

          <section class="panel events-panel" v-loading="eventsLoading">
            <div class="panel-heading"><div><span class="section-kicker">AUDIT TRAIL</span><h3>订单事件</h3></div><span>共 {{ events?.totalElements ?? 0 }} 条</span></div>
            <el-timeline v-if="events?.items.length">
              <el-timeline-item v-for="event in events.items" :key="event.id" :timestamp="formatDate(event.occurredAt)" placement="top" color="#215c52">
                <div class="event-card"><strong>{{ eventName(event.eventType) }}</strong><p>{{ event.remark || `${event.fromValue || '—'} → ${event.toValue || '—'}` }}</p><small>{{ event.operator?.displayName || '系统' }} · {{ event.source }}</small></div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无订单事件" :image-size="72" />
            <el-pagination v-if="(events?.totalElements ?? 0) > 10" v-model:current-page="eventPage" layout="prev, pager, next" :page-size="10" :total="events?.totalElements ?? 0" @current-change="loadEvents" />
          </section>
        </main>

        <aside class="detail-side">
          <section class="panel customer-panel">
            <div class="panel-heading"><div><span class="section-kicker">CUSTOMER</span><h3>客户与收货信息</h3></div></div>
            <div class="contact-row"><el-icon><User /></el-icon><div><span>客户</span><strong>{{ detail.customer.customerName || '—' }}</strong><small>{{ detail.customer.recipientName || '—' }}</small></div></div>
            <div v-if="detail.customer.recipientPhone" class="contact-row"><el-icon><Phone /></el-icon><div><span>联系电话</span><strong>{{ detail.customer.recipientPhone }}</strong></div></div>
            <div v-if="fullAddress" class="contact-row"><el-icon><Location /></el-icon><div><span>完整地址</span><strong>{{ fullAddress }}</strong></div></div>
            <el-alert v-if="!detail.customer.recipientPhone && !fullAddress" type="info" :closable="false" title="当前角色不可查看客户联系方式" />
          </section>
          <section class="panel note-panel"><div class="panel-heading"><div><span class="section-kicker">NOTE</span><h3>订单备注</h3></div></div><p>{{ detail.note || '暂无备注' }}</p></section>
          <section class="panel deadline-panel"><el-icon><Clock /></el-icon><div><span>履约截止时间</span><strong>{{ formatDate(detail.requestedShipAt) }}</strong><small>所有时间以 Asia/Shanghai 展示</small></div></section>
        </aside>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="订单不存在或暂时无法加载" />

    <ResponsibleUserDialog v-if="detail" v-model="responsibleDialog" :users="options.responsibleUsers" :current-user-id="detail.responsibleUser?.id" :loading="commandLoading" @submit="submitResponsible" />
    <ProcurementRequestDialog v-if="detail" v-model="procurementDialog" :items="detail.items" :loading="commandLoading" @submit="submitProcurement" />
    <OrderExceptionDialog v-if="detail" v-model="exceptionDialog" :resolving="detail.exception" :loading="commandLoading" @submit="submitException" />
    <CancelOrderDialog v-if="detail" v-model="cancelDialog" :order-no="detail.orderNo" :loading="commandLoading" @submit="submitCancellation" />
  </section>
</template>

<style scoped>
.detail-page { display: grid; gap: 18px; min-height: 400px; }.detail-toolbar, .detail-hero, .panel-heading, .progress-copy { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.toolbar-actions { display: flex; gap: 8px; }.detail-hero { padding: 24px 28px; border-radius: var(--erp-radius); color: white; background: linear-gradient(120deg, #173b35, #2c685e); box-shadow: var(--erp-shadow); }
.detail-hero h2 { margin: 3px 0; font-size: 28px; }.detail-hero p { margin: 0; color: #bed2ce; }.hero-status { display: grid; justify-items: end; gap: 8px; }.hero-status span:last-child { color: #bed2ce; font-size: 12px; }
.section-kicker { color: var(--erp-accent); font-size: 10px; font-weight: 800; letter-spacing: .16em; }.exception-alert { border-radius: var(--erp-radius); }
.facts-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }.fact-card { display: grid; gap: 5px; padding: 16px 18px; border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); }.fact-card span, .contact-row span { color: var(--erp-text-muted); font-size: 12px; }.fact-card strong { font-size: 14px; }
.detail-layout { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(280px, .7fr); gap: 18px; align-items: start; }.detail-main, .detail-side { display: grid; gap: 18px; }.panel { padding: 22px; border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }.panel-heading { margin-bottom: 18px; }.panel-heading h3 { margin: 2px 0 0; font-size: 17px; }.panel-heading > span { color: var(--erp-text-muted); font-size: 12px; }
.item-list { display: grid; }.fulfillment-item { display: grid; grid-template-columns: minmax(220px, 1.4fr) minmax(150px, .8fr) minmax(230px, 1fr) 85px; gap: 18px; align-items: center; padding: 18px 0; border-top: 1px solid var(--erp-border); }.fulfillment-item:first-child { border-top: 0; padding-top: 0; }.item-identity { display: flex; align-items: center; gap: 12px; }.item-thumb { display: grid; width: 44px; height: 44px; flex: 0 0 44px; place-items: center; border-radius: 9px; color: var(--erp-primary); background: var(--erp-primary-soft); font-weight: 800; }.item-identity strong, .item-identity small { display: block; }.item-identity small { margin-top: 3px; color: var(--erp-text-muted); font-size: 11px; }.progress-copy { margin-bottom: 6px; font-size: 11px; }.quantity-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 7px; }.quantity-grid span { color: var(--erp-text-muted); font-size: 10px; text-align: center; }.quantity-grid strong { display: block; color: var(--erp-text); font-size: 15px; }.quantity-grid .missing { color: #c45656; }.item-price { text-align: right; font-weight: 700; }.item-price small { display: block; color: var(--erp-text-muted); font-weight: 400; }
.event-card { padding: 13px 15px; border: 1px solid var(--erp-border); border-radius: 8px; background: #f8faf9; }.event-card p { margin: 3px 0; color: var(--erp-text-muted); }.event-card small { color: #94a09d; }.events-panel :deep(.el-pagination) { justify-content: flex-end; }.contact-row { display: flex; gap: 12px; padding: 14px 0; border-top: 1px solid var(--erp-border); }.contact-row:first-of-type { border-top: 0; }.contact-row .el-icon { margin-top: 3px; color: var(--erp-primary); }.contact-row strong, .contact-row small { display: block; }.contact-row strong { margin: 2px 0; font-size: 13px; line-height: 1.6; }.contact-row small { color: var(--erp-text-muted); }.note-panel p { margin: 0; color: var(--erp-text-muted); }.deadline-panel { display: flex; gap: 12px; color: white; background: #263f3a; }.deadline-panel .el-icon { margin-top: 3px; color: #e6b26f; }.deadline-panel span, .deadline-panel strong, .deadline-panel small { display: block; }.deadline-panel span, .deadline-panel small { color: #b8cbc7; font-size: 11px; }.deadline-panel strong { margin: 3px 0; }
@media (max-width: 1180px) { .detail-layout { grid-template-columns: 1fr; }.detail-side { grid-template-columns: repeat(2, 1fr); }.customer-panel { grid-row: span 2; }.fulfillment-item { grid-template-columns: 1fr 1fr; }.item-price { text-align: left; } }
@media (max-width: 720px) { .detail-toolbar, .detail-hero { align-items: flex-start; flex-direction: column; }.hero-status { justify-items: start; }.facts-grid, .detail-side { grid-template-columns: 1fr 1fr; }.fulfillment-item { grid-template-columns: 1fr; }.quantity-grid { grid-template-columns: repeat(4, 1fr); }.customer-panel { grid-row: auto; }.toolbar-actions { flex-wrap: wrap; } }
</style>
