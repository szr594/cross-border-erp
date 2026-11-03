<script setup lang="ts">
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/http'
import { getGoodsReceipt, type GoodsReceiptDetail } from '@/api/receiving'

const route = useRoute(); const router = useRouter(); const loading = ref(false); const detail = ref<GoodsReceiptDetail | null>(null)
const receiptId = computed(() => Number(route.params.receiptId))
async function load() { loading.value = true; try { detail.value = await getGoodsReceipt(receiptId.value) } catch (error) { ElMessage.error(error instanceof ApiError ? error.message : '收货批次加载失败') } finally { loading.value = false } }
function formatDate(value: string) { return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
onMounted(load)
</script>

<template><section class="receipt-page"><el-button text :icon="ArrowLeft" @click="detail ? router.push(`/receiving/packages/${detail.packageId}`) : router.push('/receiving')">返回包裹</el-button><template v-if="detail"><section class="receipt-hero"><div><span>CONFIRMED RECEIPT</span><h2>{{ detail.receiptNo }}</h2><p>{{ detail.packageNo }} · {{ formatDate(detail.confirmedAt) }}</p></div><div><el-tag type="success" round>已确认</el-tag><strong>{{ detail.operatorName }}</strong><small>库存入账：{{ detail.inventoryPosted ? '已完成' : '等待库存模块' }}</small></div></section><section class="panel"><header><h3>正常商品</h3><span>合格与破损分开记录</span></header><el-table :data="detail.items"><el-table-column label="商品" min-width="240"><template #default="{ row }"><strong>{{ row.productName }}</strong><p>{{ row.variantName || '默认规格' }}</p></template></el-table-column><el-table-column prop="acceptedQuantity" label="合格" width="100" /><el-table-column prop="damagedQuantity" label="破损" width="100" /><el-table-column prop="note" label="说明" min-width="220" /></el-table></section><section class="panel"><header><h3>错货记录</h3><span>{{ detail.unexpectedItems.length }} 条</span></header><el-table :data="detail.unexpectedItems"><el-table-column prop="description" label="描述" min-width="220" /><el-table-column prop="supplierSku" label="供应商 SKU" min-width="150" /><el-table-column prop="quantity" label="数量" width="90" /><el-table-column prop="note" label="说明" min-width="200" /><template #empty><el-empty description="本批次无错货" :image-size="64" /></template></el-table></section><section class="note"><strong>收货备注</strong><p>{{ detail.note || '暂无备注' }}</p></section></template><div v-else class="loading" v-loading="loading" /></section></template>

<style scoped>
.receipt-page { display: grid; gap: 18px; }.receipt-hero, .panel, .note { border: 1px solid var(--erp-border); border-radius: var(--erp-radius); background: var(--erp-surface); box-shadow: 0 8px 24px rgb(28 55 49 / 5%); }.receipt-hero { display: flex; justify-content: space-between; gap: 24px; padding: 24px 28px; border-left: 4px solid var(--erp-accent); }.receipt-hero span { color: var(--erp-accent); font-size: 10px; font-weight: 800; letter-spacing: .16em; }.receipt-hero h2 { margin: 3px 0; }.receipt-hero p, .receipt-hero small, .panel header span { margin: 0; color: var(--erp-text-muted); }.receipt-hero > div:last-child { display: grid; justify-items: end; gap: 5px; }.panel { overflow: hidden; }.panel header { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px; border-bottom: 1px solid var(--erp-border); }.panel h3 { margin: 0; }.panel p { margin: 3px 0 0; color: var(--erp-text-muted); }.note { padding: 18px 20px; }.note p { margin: 8px 0 0; color: var(--erp-text-muted); }.loading { min-height: 320px; }
@media (max-width: 620px) { .receipt-hero { flex-direction: column; }.receipt-hero > div:last-child { justify-items: start; } }
</style>
