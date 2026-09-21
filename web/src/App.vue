<script setup lang="ts">
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
const auth = useAuthStore(); const router = useRouter();
async function logout() { await auth.logout(); await router.push('/login'); }
</script>
<template>
  <div class="app-shell">
    <header v-if="auth.user" class="site-header">
      <router-link class="brand" to="/"><strong>PLAN / PLATFORM</strong><span>计划与时间管理</span></router-link>
      <nav aria-label="主导航"><router-link to="/">今日</router-link><router-link to="/schedule">日程</router-link><router-link to="/plans">计划</router-link><router-link to="/mainlines">主线</router-link><router-link to="/stats">统计</router-link><router-link to="/settings">设置</router-link></nav>
      <button class="account-button" type="button" @click="logout">{{ auth.user.displayName }} · 退出</button>
    </header>
    <main><router-view /></main>
  </div>
</template>
