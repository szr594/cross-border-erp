<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{ modelValue: boolean; resolving: boolean; loading: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; submit: [value: string] }>()
const text = ref('')
watch(() => props.modelValue, (open) => { if (open) text.value = '' })
</script>

<template>
  <el-dialog :model-value="modelValue" :title="resolving ? '解决履约异常' : '上报履约异常'" width="min(92vw, 500px)" destroy-on-close @update:model-value="emit('update:modelValue', $event)">
    <el-alert :type="resolving ? 'success' : 'warning'" :closable="false" :title="resolving ? '说明处理结果，订单将恢复正常履约。' : '异常会显示在订单工作台，并写入审计事件。'" />
    <el-input v-model="text" class="reason-input" type="textarea" :rows="4" maxlength="500" show-word-limit :placeholder="resolving ? '填写解决结果' : '描述发现的问题'" />
    <template #footer><el-button @click="emit('update:modelValue', false)">取消</el-button><el-button :type="resolving ? 'success' : 'warning'" :loading="loading" :disabled="!text.trim()" @click="emit('submit', text.trim())">{{ resolving ? '确认解决' : '确认上报' }}</el-button></template>
  </el-dialog>
</template>

<style scoped>.reason-input { margin-top: 16px; }</style>
