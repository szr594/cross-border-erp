<script setup lang="ts">
import type { OrderProcurementRequestPage, OrderProcurementRequest } from '@/api/orders'

defineProps<{ page: OrderProcurementRequestPage | null; loading: boolean }>()
const emit = defineEmits<{ pageChange: [page: number]; withdraw: [request: OrderProcurementRequest] }>()

function statusType(status: string) {
  if (status === 'REQUESTED') return 'warning'
  if (status === 'CONVERTED') return 'success'
  return 'info'
}
function statusName(status: string) {
  return { REQUESTED: '待处理', CONVERTED: '已转单', WITHDRAWN: '已撤回' }[status] ?? status
}
function formatDate(value: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <section class="panel procurement-panel" v-loading="loading">
    <div class="panel-heading"><div><span class="section-kicker">PROCUREMENT</span><h3>采购申请记录</h3></div><span>共 {{ page?.totalElements ?? 0 }} 条</span></div>
    <div v-if="page?.items.length" class="request-list">
      <article v-for="request in page.items" :key="request.id" class="request-card">
        <div class="request-main">
          <div class="request-title"><strong>{{ request.orderItem.productName }}</strong><el-tag :type="statusType(request.status)" effect="light">{{ statusName(request.status) }}</el-tag></div>
          <small>{{ request.orderItem.externalSku || '无 SKU' }}<template v-if="request.orderItem.variantName"> · {{ request.orderItem.variantName }}</template></small>
          <p v-if="request.note">{{ request.note }}</p>
        </div>
        <div class="request-quantity"><span>申请数量</span><strong>{{ request.requestedQuantity }}</strong></div>
        <div class="request-meta"><span>{{ request.requestedBy.displayName }}</span><small>{{ formatDate(request.requestedAt) }}</small><template v-if="request.withdrawnBy"><span>由 {{ request.withdrawnBy.displayName }} 撤回</span><small>{{ formatDate(request.withdrawnAt) }}</small></template></div>
        <el-button v-if="request.canWithdraw" type="danger" plain size="small" @click="emit('withdraw', request)">撤回</el-button>
      </article>
    </div>
    <el-empty v-else description="暂无采购申请" :image-size="72" />
    <el-pagination v-if="(page?.totalElements ?? 0) > (page?.size ?? 10)" :current-page="page?.page ?? 1" :page-size="page?.size ?? 10" :total="page?.totalElements ?? 0" layout="prev, pager, next" @current-change="emit('pageChange', $event)" />
  </section>
</template>

<style scoped>
.procurement-panel { padding: 22px; border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; }.panel-heading h3 { margin: 2px 0 0; font-size: 17px; }.panel-heading > span, .request-main small, .request-meta small { color: var(--erp-text-muted); font-size: 12px; }.section-kicker { color: var(--erp-accent); font-size: 10px; font-weight: 800; letter-spacing: .16em; }.request-list { display: grid; gap: 10px; }.request-card { display: grid; grid-template-columns: minmax(220px, 1fr) 90px 180px auto; gap: 16px; align-items: center; padding: 15px 16px; border: 1px solid var(--erp-border); border-radius: 9px; background: #f9fbfa; transition: border-color .18s ease-out, box-shadow .18s ease-out; }.request-card:hover { border-color: #b8cbc7; box-shadow: 0 5px 16px rgb(28 55 49 / 6%); }.request-title { display: flex; align-items: center; gap: 8px; }.request-main p { margin: 6px 0 0; color: var(--erp-text-muted); font-size: 12px; }.request-quantity, .request-meta { display: grid; gap: 3px; }.request-quantity span, .request-meta span { font-size: 12px; }.request-quantity strong { color: var(--erp-primary); font-size: 20px; }.procurement-panel :deep(.el-pagination) { justify-content: flex-end; margin-top: 16px; }@media (max-width: 760px) { .request-card { grid-template-columns: 1fr auto; }.request-quantity, .request-meta { grid-column: 1; }.request-card > .el-button { grid-column: 2; grid-row: 2; min-height: 44px; } }
</style>
