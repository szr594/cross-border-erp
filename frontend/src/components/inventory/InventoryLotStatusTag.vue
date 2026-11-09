<script setup lang="ts">
import { computed } from 'vue'

import type { InventoryLotStatus } from '@/api/inventory'

const props = defineProps<{ status: InventoryLotStatus }>()
const config = computed(() => ({
  AVAILABLE: { label: '可用', type: 'success' },
  ALLOCATED: { label: '已预留', type: 'primary' },
  QUARANTINED: { label: '隔离中', type: 'warning' },
  DEPLETED: { label: '已耗尽', type: 'info' },
  CLOSED: { label: '已关闭', type: 'info' },
} as const)[props.status])
</script>

<template><el-tag :type="config.type" effect="light" round>{{ config.label }}</el-tag></template>
