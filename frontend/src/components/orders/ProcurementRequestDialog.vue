<script setup lang="ts">
import { computed, reactive, watch } from 'vue'

import type { OrderItem, ProcurementItemCommand } from '@/api/orders'

const props = defineProps<{ modelValue: boolean; items: OrderItem[]; loading: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; submit: [value: { items: ProcurementItemCommand[]; note: string }] }>()
const quantities = reactive<Record<number, number>>({})
const selected = reactive<Record<number, boolean>>({})
const form = reactive({ note: '' })
const canSubmit = computed(() => props.items.some(
  (item) => selected[item.id] && (quantities[item.id] ?? 0) > 0,
))

watch(() => props.modelValue, (open) => {
  if (!open) return
  form.note = ''
  props.items.forEach((item) => {
    selected[item.id] = item.missingQuantity > 0
    quantities[item.id] = item.missingQuantity
  })
})

function submit() {
  const items = props.items.filter((item) => selected[item.id] && (quantities[item.id] ?? 0) > 0)
    .map((item) => ({ orderItemId: item.id, quantity: quantities[item.id] ?? 0 }))
  if (items.length) emit('submit', { items, note: form.note.trim() })
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="创建采购申请" width="min(94vw, 720px)" destroy-on-close @update:model-value="emit('update:modelValue', $event)">
    <p class="dialog-tip">仅展示仍有缺口的商品。提交前请确认数量，系统会在事务中重新校验采购缺口。</p>
    <div class="procurement-list">
      <div v-for="item in items.filter((entry) => entry.missingQuantity > 0)" :key="item.id" class="procurement-row">
        <el-checkbox v-model="selected[item.id]" :aria-label="`选择 ${item.productName}`" />
        <div><strong>{{ item.productName }}</strong><small>{{ item.externalSku || '无 SKU' }} · 缺口 {{ item.missingQuantity }}</small></div>
        <el-input-number v-model="quantities[item.id]" :min="1" :max="item.missingQuantity" :disabled="!selected[item.id]" controls-position="right" />
      </div>
      <el-empty v-if="!items.some((item) => item.missingQuantity > 0)" description="当前没有采购缺口" :image-size="64" />
    </div>
    <el-form label-position="top"><el-form-item label="采购说明（选填）"><el-input v-model="form.note" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item></el-form>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="loading" :disabled="!canSubmit" @click="submit">提交采购申请</el-button></template>
  </el-dialog>
</template>

<style scoped>
.dialog-tip { margin: 0 0 16px; color: var(--erp-text-muted); line-height: 1.6; }.procurement-list { display: grid; gap: 8px; margin-bottom: 18px; }.procurement-row { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 12px; align-items: center; padding: 12px 14px; border: 1px solid var(--erp-border); border-radius: 8px; }.procurement-row strong, .procurement-row small { display: block; }.procurement-row small { margin-top: 3px; color: var(--erp-text-muted); }@media (max-width: 560px) { .procurement-row { grid-template-columns: auto 1fr; }.procurement-row .el-input-number { grid-column: 2; width: 100%; } }
</style>
