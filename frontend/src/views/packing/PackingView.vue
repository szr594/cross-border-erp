<script setup lang="ts">
import { Box, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { getPackingTasks, type PackingStatus, type PackingTaskListItem, type PackingTaskPage } from '@/api/packing'

const router = useRouter()
const loading = ref(false)
const page = ref<PackingTaskPage | null>(null)
const filters = reactive({ keyword: '', status: '' as PackingStatus | '' })
const statusNames: Record<PackingStatus, string> = { READY: '待开始', DRAFT: '打包中', COMPLETED: '已完成', CANCELED: '已取消' }
const statuses: PackingStatus[] = ['READY', 'DRAFT', 'COMPLETED', 'CANCELED']
async function load() { loading.value = true; try { page.value = await getPackingTasks({ keyword: filters.keyword.trim() || undefined, status: filters.status || undefined }) } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '打包任务加载失败') } finally { loading.value = false } }
function reset() { filters.keyword = ''; filters.status = ''; void load() }
function open(row: PackingTaskListItem) { void router.push({ name: 'packing-detail', params: { orderId: row.orderId } }) }
function formatDate(value: string | null) { return value ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—' }
onMounted(load)
</script>

<template>
  <section class="packing-workbench">
    <header class="intro"><div><span class="kicker">FULFILLMENT PACKING</span><h2>打包工作台</h2><p>将已完成库存预留的订单分箱装包，并在完成时扣减预留库存。</p></div><el-button :icon="RefreshRight" :loading="loading" @click="load">刷新</el-button></header>
    <section class="metrics"><article><el-icon><Box /></el-icon><div><small>待开始</small><strong>{{ page?.pendingCount ?? 0 }}</strong></div></article><article><el-icon><Box /></el-icon><div><small>打包中</small><strong>{{ page?.draftCount ?? 0 }}</strong></div></article><article><el-icon><Box /></el-icon><div><small>已完成</small><strong>{{ page?.completedCount ?? 0 }}</strong></div></article></section>
    <section class="filters"><el-input v-model="filters.keyword" clearable placeholder="订单号" :prefix-icon="Search" @keyup.enter="load" /><el-select v-model="filters.status" clearable placeholder="全部状态"><el-option v-for="status in statuses" :key="status" :label="statusNames[status]" :value="status" /></el-select><div><el-button type="primary" :icon="Search" @click="load">查询</el-button><el-button :icon="RefreshRight" @click="reset">重置</el-button></div></section>
    <section class="table-panel"><el-table v-loading="loading" :data="page?.items ?? []" row-class-name="clickable" @row-click="open"><el-table-column label="订单" min-width="200"><template #default="{ row }: { row: PackingTaskListItem }"><div class="primary"><strong>{{ row.orderNo }}</strong><small>{{ row.storeName }}</small></div></template></el-table-column><el-table-column label="状态" width="110"><template #default="{ row }: { row: PackingTaskListItem }"><span class="status-pill" :class="row.status.toLowerCase()">{{ statusNames[row.status] }}</span></template></el-table-column><el-table-column label="商品 / 数量" min-width="140"><template #default="{ row }: { row: PackingTaskListItem }">{{ row.itemCount }} 款 · {{ row.totalQuantity }} 件</template></el-table-column><el-table-column prop="boxCount" label="箱数" width="90" /><el-table-column label="要求发货" min-width="170"><template #default="{ row }: { row: PackingTaskListItem }">{{ formatDate(row.requestedShipAt) }}</template></el-table-column><el-table-column label="开始时间" min-width="170"><template #default="{ row }: { row: PackingTaskListItem }">{{ formatDate(row.startedAt) }}</template></el-table-column><template #empty><el-empty description="暂无符合条件的打包订单" :image-size="88" /></template></el-table></section>
  </section>
</template>

<style scoped>
.packing-workbench{display:grid;gap:20px}.intro{display:flex;justify-content:space-between;gap:20px}.intro h2{margin:2px 0 4px;font-size:24px}.intro p{margin:0;color:var(--erp-text-muted)}.kicker{color:var(--erp-accent);font-size:10px;font-weight:800;letter-spacing:.16em}.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.metrics article{display:flex;align-items:center;gap:14px;padding:18px;border:1px solid var(--erp-border);border-radius:var(--erp-radius);background:var(--erp-surface);box-shadow:0 8px 24px rgb(28 55 49 / 5%)}.metrics .el-icon{display:grid;width:42px;height:42px;place-items:center;border-radius:11px;color:#16775b;background:#e7f6f0;font-size:19px}.metrics article div,.primary{display:grid;gap:2px}.metrics small,.primary small{color:var(--erp-text-muted)}.metrics strong{font-size:24px}.filters{display:grid;grid-template-columns:minmax(240px,1fr) 180px auto;gap:12px;padding:18px;border:1px solid var(--erp-border);border-radius:var(--erp-radius);background:var(--erp-surface)}.table-panel{overflow:hidden;border:1px solid var(--erp-border);border-radius:var(--erp-radius);background:var(--erp-surface);box-shadow:0 8px 24px rgb(28 55 49 / 5%)}.table-panel :deep(.clickable){cursor:pointer}.primary strong{color:var(--erp-primary)}.status-pill{display:inline-flex;align-items:center;justify-content:center;padding:4px 9px;border-radius:999px;font-size:12px;font-weight:700;white-space:nowrap}.status-pill.ready{color:#275fa6;background:#eaf2fc}.status-pill.draft{color:#9a5a0a;background:#fff2dc}.status-pill.completed{color:#16775b;background:#e7f6f0}.status-pill.canceled{color:#68747b;background:#edf1f2}@media(max-width:700px){.metrics,.filters{grid-template-columns:1fr}.intro{display:grid}}
</style>

