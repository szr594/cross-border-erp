<script setup lang="ts">
import { reactive, watch } from 'vue'

import type { UserSummary } from '@/api/orders'

const props = defineProps<{ modelValue: boolean; users: UserSummary[]; currentUserId?: number; loading: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; submit: [value: { userId: number; reason: string }] }>()
const form = reactive({ userId: undefined as number | undefined, reason: '' })

watch(() => props.modelValue, (open) => {
  if (open) Object.assign(form, { userId: props.currentUserId, reason: '' })
})

function submit() {
  if (!form.userId || !form.reason.trim()) return
  emit('submit', { userId: form.userId, reason: form.reason.trim() })
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="调整订单负责人" width="min(92vw, 480px)" destroy-on-close @update:model-value="emit('update:modelValue', $event)">
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="新负责人" required><el-select v-model="form.userId" filterable placeholder="选择员工" class="full-width"><el-option v-for="user in users" :key="user.id" :label="`${user.displayName} · ${user.loginName}`" :value="user.id" /></el-select></el-form-item>
      <el-form-item label="调整原因" required><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="例如：运营排班调整" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="loading" :disabled="!form.userId || !form.reason.trim()" @click="submit">确认调整</el-button></template>
  </el-dialog>
</template>

<style scoped>.full-width { width: 100%; }</style>
