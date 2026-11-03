<script setup lang="ts">
import { ArrowLeft, Link, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { getPurchaseOrderDetail, getPurchaseOrderEvents, type PurchaseOrderDetail, type PurchaseOrderEventPage } from '@/api/purchaseOrders'
import PurchaseOrderStatusTag from '@/components/procurement/PurchaseOrderStatusTag.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const eventLoading = ref(false)
const detail = ref<PurchaseOrderDetail | null>(null)
const events = ref<PurchaseOrderEventPage | null>(null)
const eventPage = ref(1)
const orderId = computed(() => Number(route.params.purchaseOrderId))

async function loadDetail() {
  if (!Number.isInteger(orderId.value) || orderId.value <= 0) {
    ElMessage.error('采购单编号无效')
    await router.replace('/procurement')
    return
  }
  loading.value = true
  try { detail.value = await getPurchaseOrderDetail(orderId.value) }
  catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '采购单详情加载失败') }
  finally { loading.value = false }
}
async function loadEvents(page = eventPage.value) {
  if (!Number.isInteger(orderId.value) || orderId.value <= 0) return
  eventLoading.value = true
  try { events.value = await getPurchaseOrderEvents(orderId.value, page, 10); eventPage.value = page }
  catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '采购事件加载失败') }
  finally { eventLoading.value = false }
}
function refresh() { void Promise.all([loadDetail(), loadEvents()]) }
function formatMoney(value: number | null, currency: string) {
  return value === null ? '待定价' : new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(value / 100)
}
function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value)) : '—'
}
function eventName(value: string) {
  return ({ PURCHASE_ORDER_CREATED: '采购单创建' } as Record<string, string>)[value] ?? value
}
function procurementStatusName(value: string) {
  return ({ REQUESTED: '待转单', CONVERTED: '已转单', WITHDRAWN: '已撤回' } as Record<string, string>)[value] ?? value
}
onMounted(refresh)
</script>

<template>
  <section class="detail-page">
    <div class="detail-actions"><el-button text :icon="ArrowLeft" @click="router.push('/procurement')">返回采购单工作台</el-button><el-button :icon="RefreshRight" :loading="loading" @click="refresh">刷新</el-button></div>
    <template v-if="detail">
      <section class="hero-card">
        <div><span class="section-kicker">{{ detail.supplier.name }}</span><h2>{{ detail.purchaseOrderNo }}</h2><p>{{ detail.externalOrderNo ? `外部单号 ${detail.externalOrderNo}` : '内部采购单' }}</p></div>
        <div class="hero-status"><PurchaseOrderStatusTag :status="detail.status" /><strong>{{ formatMoney(detail.totalAmountMinor, detail.currency) }}</strong><small>由 {{ detail.createdBy.displayName }} 创建</small></div>
      </section>

      <div class="content-grid">
        <main class="main-column">
          <section class="panel">
            <div class="panel-heading"><div><span class="section-kicker">PURCHASE ITEMS</span><h3>采购明细</h3></div><span>{{ detail.items.length }} 款商品</span></div>
            <article v-for="item in detail.items" :key="item.id" class="item-card">
              <div class="item-image"><img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.productName"><span v-else>无图</span></div>
              <div class="item-main">
                <div class="item-title"><div><h4>{{ item.productName }}</h4><p>{{ item.variantName || item.supplierSku || '默认规格' }}</p></div><a v-if="item.productUrl" :href="item.productUrl" target="_blank" rel="noreferrer"><el-icon><Link /></el-icon> 商品链接</a></div>
                <div class="item-metrics"><span><small>采购数量</small><strong>{{ item.purchasedQuantity }}</strong></span><span><small>已到货</small><strong>{{ item.receivedQuantity }}</strong></span><span><small>单价</small><strong>{{ formatMoney(item.unitPriceMinor, item.currency) }}</strong></span></div>
                <div class="source-block"><strong>需求来源</strong><div v-for="source in item.sources" :key="source.allocationId" class="source-row"><button type="button" @click="router.push(`/orders/${source.salesOrderId}`)">{{ source.salesOrderNo }}</button><span>{{ source.productName }}{{ source.variantName ? ` · ${source.variantName}` : '' }}</span><em>{{ source.allocatedQuantity }} 件 · {{ procurementStatusName(source.procurementStatus) }}</em></div><el-empty v-if="item.sources.length === 0" description="暂无需求来源" :image-size="48" /></div>
              </div>
            </article>
          </section>

          <section class="panel">
            <div class="panel-heading"><div><span class="section-kicker">AUDIT TRAIL</span><h3>采购事件</h3></div><span>共 {{ events?.totalElements ?? 0 }} 条</span></div>
            <div v-loading="eventLoading" class="timeline-wrap"><el-timeline v-if="events?.items.length"><el-timeline-item v-for="event in events.items" :key="event.id" :timestamp="formatDate(event.occurredAt)" placement="top"><strong>{{ eventName(event.eventType) }}</strong><p>{{ event.remark || `${event.fromValue || '—'} → ${event.toValue || '—'}` }}</p><small>{{ event.operator?.displayName || '系统' }}</small></el-timeline-item></el-timeline><el-empty v-else description="暂无采购事件" :image-size="72" /></div>
            <el-pagination v-if="(events?.totalPages ?? 0) > 1" class="event-pagination" :current-page="eventPage" :page-size="10" layout="prev, pager, next" :total="events?.totalElements ?? 0" @current-change="loadEvents" />
          </section>
        </main>

        <aside class="side-column">
          <section class="panel info-panel"><div class="panel-heading"><div><span class="section-kicker">SUPPLIER</span><h3>供应商</h3></div></div><dl><div><dt>供应商编码</dt><dd>{{ detail.supplier.code }}</dd></div><div><dt>平台</dt><dd>{{ detail.supplier.platform }}</dd></div><div><dt>联系人</dt><dd>{{ detail.supplier.contactName || '—' }}</dd></div><div><dt>联系电话</dt><dd>{{ detail.supplier.contactPhone || '—' }}</dd></div></dl><a v-if="detail.supplier.shopUrl" class="shop-link" :href="detail.supplier.shopUrl" target="_blank" rel="noreferrer">访问供应商店铺</a></section>
          <section class="panel info-panel"><div class="panel-heading"><div><span class="section-kicker">SCHEDULE</span><h3>采购信息</h3></div></div><dl><div><dt>下单时间</dt><dd>{{ formatDate(detail.orderedAt) }}</dd></div><div><dt>预计到货</dt><dd>{{ formatDate(detail.expectedArrivalAt) }}</dd></div><div><dt>创建时间</dt><dd>{{ formatDate(detail.createdAt) }}</dd></div><div><dt>更新版本</dt><dd>v{{ detail.version }}</dd></div></dl></section>
          <section class="panel note-panel"><div class="panel-heading"><div><span class="section-kicker">NOTE</span><h3>采购备注</h3></div></div><p>{{ detail.note || '暂无备注' }}</p></section>
        </aside>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="采购单不存在或暂时无法加载" />
    <div v-else class="loading-card" v-loading="loading" />
  </section>
</template>

<style scoped>
.detail-page { display: grid; gap: 18px; }.detail-actions { display: flex; justify-content: space-between; }.section-kicker { color: var(--erp-accent); font-size: 10px; font-weight: 800; letter-spacing: .16em; }
.hero-card, .panel { border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }
.hero-card { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 24px 28px; border-left: 4px solid var(--erp-primary); }.hero-card h2 { margin: 2px 0; font-size: 26px; }.hero-card p { margin: 0; color: var(--erp-text-muted); }.hero-status { display: grid; justify-items: end; gap: 4px; }.hero-status strong { font-size: 24px; }.hero-status small { color: var(--erp-text-muted); }
.content-grid { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 18px; align-items: start; }.main-column, .side-column { display: grid; gap: 18px; }.panel { overflow: hidden; }.panel-heading { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px; border-bottom: 1px solid var(--erp-border); }.panel-heading h3 { margin: 1px 0 0; font-size: 17px; }.panel-heading > span { color: var(--erp-text-muted); font-size: 12px; }
.item-card { display: grid; grid-template-columns: 76px minmax(0, 1fr); gap: 16px; padding: 20px; border-bottom: 1px solid var(--erp-border); }.item-card:last-child { border-bottom: 0; }.item-image { display: grid; width: 76px; height: 76px; place-items: center; overflow: hidden; border-radius: 8px; color: var(--erp-text-muted); background: var(--erp-canvas); font-size: 12px; }.item-image img { width: 100%; height: 100%; object-fit: cover; }.item-main { min-width: 0; }.item-title { display: flex; justify-content: space-between; gap: 16px; }.item-title h4 { margin: 0; font-size: 16px; }.item-title p { margin: 2px 0 0; color: var(--erp-text-muted); font-size: 13px; }.item-title a { display: flex; align-items: center; gap: 4px; color: var(--erp-primary); font-size: 13px; text-decoration: none; }
.item-metrics { display: flex; gap: 32px; margin: 16px 0; }.item-metrics span { display: grid; gap: 2px; }.item-metrics small { color: var(--erp-text-muted); }.source-block { padding: 12px 14px; border-radius: 8px; background: var(--erp-canvas); }.source-block > strong { font-size: 12px; }.source-row { display: grid; grid-template-columns: 130px minmax(0, 1fr) auto; gap: 10px; align-items: center; margin-top: 8px; font-size: 12px; }.source-row button { padding: 0; border: 0; color: var(--erp-primary); background: transparent; cursor: pointer; font-weight: 700; text-align: left; }.source-row em { color: var(--erp-text-muted); font-style: normal; }
.timeline-wrap { min-height: 120px; padding: 24px 24px 4px; }.timeline-wrap p { margin: 4px 0; color: var(--erp-text-muted); }.timeline-wrap small { color: #98a39f; }.event-pagination { justify-content: flex-end; padding: 0 18px 18px; }
.info-panel dl { margin: 0; padding: 8px 20px 18px; }.info-panel dl div { display: flex; justify-content: space-between; gap: 12px; padding: 9px 0; border-bottom: 1px dashed var(--erp-border); }.info-panel dt { color: var(--erp-text-muted); }.info-panel dd { margin: 0; text-align: right; }.shop-link { display: block; margin: 0 20px 20px; color: var(--erp-primary); text-align: right; text-decoration: none; }.note-panel > p { margin: 0; padding: 18px 20px; color: var(--erp-text-muted); white-space: pre-wrap; }.loading-card { min-height: 320px; }
@media (max-width: 1050px) { .content-grid { grid-template-columns: 1fr; }.side-column { grid-template-columns: repeat(2, 1fr); }.note-panel { grid-column: span 2; } }
@media (max-width: 680px) { .hero-card, .item-title { align-items: flex-start; flex-direction: column; }.hero-status { justify-items: start; }.item-card { grid-template-columns: 1fr; }.item-metrics { gap: 18px; flex-wrap: wrap; }.source-row { grid-template-columns: 1fr; }.side-column { grid-template-columns: 1fr; }.note-panel { grid-column: span 1; } }
</style>
