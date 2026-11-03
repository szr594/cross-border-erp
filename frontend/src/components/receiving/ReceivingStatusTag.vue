<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string }>()
const label = computed(() => ({
  EXPECTED: '待到货', RECEIVING: '收货中', RECEIVED: '已收货', EXCEPTION: '异常', CANCELED: '已取消',
} as Record<string, string>)[props.status] ?? props.status)
const type = computed(() => {
  if (props.status === 'RECEIVED') return 'success'
  if (props.status === 'EXCEPTION') return 'danger'
  if (props.status === 'RECEIVING') return 'warning'
  return 'info'
})
</script>

<template><el-tag :type="type" effect="light" round>{{ label }}</el-tag></template>
