<script setup lang="ts">
defineProps<{ status: string }>()

const labels: Record<string, string> = {
  DRAFT: '草稿', ORDERED: '已下单', PARTIALLY_RECEIVED: '部分到货',
  RECEIVED: '已到货', COMPLETED: '已完成', CANCELED: '已取消',
}
const types: Record<string, 'info' | 'warning' | 'primary' | 'success' | 'danger'> = {
  DRAFT: 'info', ORDERED: 'primary', PARTIALLY_RECEIVED: 'warning',
  RECEIVED: 'success', COMPLETED: 'success', CANCELED: 'danger',
}
</script>

<template><el-tag :type="types[status] ?? 'info'" effect="light">{{ labels[status] ?? status }}</el-tag></template>
