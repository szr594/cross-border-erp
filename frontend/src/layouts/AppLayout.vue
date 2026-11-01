<script setup lang="ts">
import {
  Box,
  Goods,
  List,
  ShoppingCart,
  Suitcase,
  TakeawayBox,
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'

import { authState, logout } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const pageTitle = computed(() => String(route.meta.title ?? '工作台'))

function navigate(path: string) {
  void router.push(path)
}

async function signOut() {
  await logout()
  await router.replace('/login')
}
</script>

<template>
  <el-container class="app-shell">
    <el-aside class="app-sidebar" width="232px">
      <div class="brand">
        <span class="brand-mark">C</span>
        <div>
          <strong>Cross Border ERP</strong>
          <small>跨境履约系统</small>
        </div>
      </div>

      <el-menu :default-active="route.path" class="app-menu" @select="navigate">
        <el-menu-item index="/orders"><el-icon><List /></el-icon><span>订单工作台</span></el-menu-item>
        <el-menu-item index="/procurement"><el-icon><ShoppingCart /></el-icon><span>采购管理</span></el-menu-item>
        <el-menu-item index="/inventory"><el-icon><Goods /></el-icon><span>库存管理</span></el-menu-item>
        <el-menu-item index="/receiving"><el-icon><TakeawayBox /></el-icon><span>到货收货</span></el-menu-item>
        <el-menu-item index="/packing"><el-icon><Box /></el-icon><span>打包管理</span></el-menu-item>
      </el-menu>

      <div class="sidebar-foot">
        <el-icon><Suitcase /></el-icon>
        <span>Portfolio MVP</span>
      </div>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div>
          <span class="eyebrow">OPERATIONS</span>
          <h1>{{ pageTitle }}</h1>
        </div>
        <el-dropdown trigger="click" @command="signOut">
          <button class="user-button" type="button">
            <el-avatar>{{ authState.user?.name.slice(0, 1) || '员' }}</el-avatar>
            <span>
              <strong>{{ authState.user?.name || '员工' }}</strong>
              <small>{{ authState.user?.roles.join(' · ') }}</small>
            </span>
          </button>
          <template #dropdown>
            <el-dropdown-menu><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="app-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>
