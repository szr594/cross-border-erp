<script setup lang="ts">
import { Lock, User } from '@element-plus/icons-vue'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { login } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const form = reactive({ loginName: '', password: '' })
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await login(form.loginName.trim(), form.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/orders'
    await router.replace(redirect)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <span class="brand-mark large">C</span>
      <p class="eyebrow">CROSS BORDER OPERATIONS</p>
      <h1>欢迎回来</h1>
      <p>使用员工账号进入跨境履约工作台</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-form class="login-form" label-position="top" @submit.prevent="submit">
        <el-form-item label="登录名">
          <el-input v-model="form.loginName" :prefix-icon="User" autocomplete="username" maxlength="100" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            type="password"
            autocomplete="current-password"
            maxlength="255"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button
          class="login-submit"
          type="primary"
          native-type="submit"
          :loading="loading"
          :disabled="!form.loginName.trim() || !form.password"
        >
          登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>
