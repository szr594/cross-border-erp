<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElCheckboxGroup } from 'element-plus/es/components/checkbox/index.mjs'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { getOrders, getOrderProcurementRequests, type OrderListItem, type OrderProcurementRequest } from '@/api/orders'
import { searchActiveSuppliers, type SupplierOption } from '@/api/suppliers'
import { preparePurchaseCreation } from '@/api/purchaseOrders'
const emit = defineEmits<{ created: [id: number] }>()
const orders = ref<OrderListItem[]>([])
const suppliers = ref<SupplierOption[]>([])
const demands = ref<OrderProcurementRequest[]>([])
const selected = ref<number[]>([])
const orderId = ref<number>()
const supplierId = ref<number>()
const loading = ref(false)
const submitting = ref(false)
const operation = ref<ReturnType<typeof preparePurchaseCreation> | null>(null)
const canEdit = computed(() => !operation.value && !submitting.value)
async function searchOrders(keyword = '') {
  try { orders.value = (await getOrders({ page: 1, size: 100, keyword, stage: 'ACTIONABLE' })).items }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '订单加载失败') }
}
async function searchSuppliers(keyword = '') {
  try { suppliers.value = (await searchActiveSuppliers(keyword)).items }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '供应商加载失败') }
}
async function loadDemands() {
  demands.value = []; selected.value = []
  if (!orderId.value) return
  loading.value = true
  try {
    let page = 1
    for (;;) {
      const result = await getOrderProcurementRequests(orderId.value, page, 100)
      demands.value.push(...result.items.filter(item => item.status === 'REQUESTED'))
      if (page >= result.totalPages) break
      page++
    }
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '需求加载失败') }
  finally { loading.value = false }
}
async function submit() {
  if (!operation.value) {
    if (!supplierId.value || !selected.value.length) return ElMessage.warning('请选择供应商和完整采购需求')
    operation.value = preparePurchaseCreation({ supplierId: supplierId.value, currency: 'CNY', items: demands.value
      .filter(item => selected.value.includes(item.id)).map(item => ({ productName: item.orderItem.productName,
        variantName: item.orderItem.variantName ?? undefined, supplierSku: item.orderItem.externalSku ?? undefined,
        unitPriceMinor: null, allocations: [{ procurementRequestId: item.id, quantity: item.requestedQuantity, version: item.version }] })) })
  }
  submitting.value = true
  try { const result = await operation.value.submit(); ElMessage.success('采购单创建成功'); emit('created', result.id) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败，请使用原请求重试') }
  finally { submitting.value = false }
}
onMounted(() => { void searchOrders(); void searchSuppliers() })
</script>
<template>
  <div class="creation-form">
    <p>选择一个供应商，将所选需求一次性完整转为采购单。采购数量自动等于分配数量合计，币种为人民币。</p>
    <el-form label-position="top">
      <el-form-item label="供应商"><el-select v-model="supplierId" aria-label="供应商" filterable remote :remote-method="searchSuppliers" :disabled="!canEdit" placeholder="搜索启用供应商"><el-option v-for="supplier in suppliers" :key="supplier.id" :value="supplier.id" :label="`${supplier.name} / ${supplier.code}`" /></el-select></el-form-item>
      <el-form-item label="来源订单"><el-select v-model="orderId" aria-label="来源订单" filterable remote :remote-method="searchOrders" :disabled="!canEdit" placeholder="搜索订单号或商品" @change="loadDemands"><el-option v-for="order in orders" :key="order.id" :value="order.id" :label="order.displayOrderNo || order.orderNo" /></el-select></el-form-item>
    </el-form>
    <div v-loading="loading">
      <el-empty v-if="!demands.length" description="暂无待转单需求，请先在订单详情申请采购" />
      <el-checkbox-group v-model="selected"><div v-for="demand in demands" :key="demand.id" class="demand-row"><el-checkbox :value="demand.id" :disabled="!canEdit">{{ demand.orderItem.productName }} · {{ demand.requestedQuantity }} 件</el-checkbox></div></el-checkbox-group>
    </div>
    <el-alert v-if="operation" title="当前请求已锁定；失败时重试会沿用原幂等键，请勿另开窗口重复创建。" type="info" :closable="false" />
    <el-button type="primary" :loading="submitting" :disabled="loading" @click="submit">{{ operation ? '重试原创建请求' : '认领需求并创建采购单' }}</el-button>
  </div>
</template>
<style scoped>
.creation-form { display: grid; gap: 16px; }
.creation-form p { color: var(--erp-text-muted); line-height: 1.7; margin: 0; }
.demand-row { padding: 8px 0; border-bottom: 1px solid var(--erp-border); }
.el-select { width: 100%; }
</style>
