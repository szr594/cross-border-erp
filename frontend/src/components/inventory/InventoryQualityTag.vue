<script setup lang="ts">
import { computed } from 'vue'

import type { InventoryQualityStatus } from '@/api/inventory'

const props = defineProps<{ status: InventoryQualityStatus }>()
const config = computed(() => props.status === 'ACCEPTED'
  ? { label: '合格品', type: 'success' as const }
  : { label: '破损品', type: 'danger' as const })
</script>

<template><el-tag :type="config.type" effect="plain" round>{{ config.label }}</el-tag></template>
