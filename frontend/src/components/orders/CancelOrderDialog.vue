<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{ modelValue: boolean; orderNo: string; loading: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; submit: [value: string] }>()
const reason = ref('')
watch(() => props.modelValue, (open) => { if (open) reason.value = '' })
</script>

<template>
  <el-dialog :model-value="modelValue" title="取消订单" width="min(92vw, 480px)" destroy-on-close @update:model-value="emit('update:modelValue', $event)">
    <el-alert type="error" :closable="false" show-icon :title="`即将取消订单 ${orderNo}`" description="待处理采购申请会同步撤回；已经转单的采购会阻止本次操作。" />
    <el-input v-model="reason" class="reason-input" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="填写取消原因" />
    <template #footer><el-button @click="emit('update:modelValue', false)">返回</el-button><el-button type="danger" :loading="loading" :disabled="!reason.trim()" @click="emit('submit', reason.trim())">确认取消订单</el-button></template>
  </el-dialog>
</template>

<style scoped>.reason-input { margin-top: 16px; }</style>
