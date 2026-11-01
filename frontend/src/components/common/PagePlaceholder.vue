<script setup lang="ts">
defineProps<{
  title: string
  description: string
}>()
</script>

<template>
  <section class="page-placeholder">
    <el-empty :description="description">
      <template #image>
        <div class="placeholder-mark">{{ title.slice(0, 1) }}</div>
      </template>
      <el-tag effect="plain">页面骨架已创建</el-tag>
    </el-empty>
  </section>
</template>

