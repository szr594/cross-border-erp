<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ stage: string; label: string }>()
const tagType = computed(() => {
  if (['COMPLETED', 'READY_FOR_PACKING', 'PACKED', 'SHIPPED'].includes(props.stage)) return 'success'
  if (['EXCEPTION', 'CANCELED'].includes(props.stage)) return 'danger'
  if (['WAITING_PROCUREMENT', 'PROCUREMENT_IN_PROGRESS', 'DOMESTIC_TRANSIT'].includes(props.stage)) return 'warning'
  return 'info'
})
</script>

<template><el-tag :type="tagType" effect="light" round>{{ label }}</el-tag></template>
