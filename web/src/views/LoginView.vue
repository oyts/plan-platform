<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { apiMessage } from '@/api/client';
import { useAuthStore } from '@/stores/auth';
import { isNativeClient as isDesktop } from '@/desktop';
import { workspaceEntry } from '@/workspace';
const email = ref(''); const password = ref(''); const error = ref(''); const busy = ref(false);
const auth = useAuthStore(); const router = useRouter();
async function submit() { if (!auth.ready || busy.value) return; busy.value = true; error.value = ''; try { await auth.login(email.value, password.value); await router.push(await workspaceEntry()); } catch (e) { error.value = apiMessage(e); } finally { busy.value = false; } }
</script>
<template>
  <div class="auth-page">
    <section class="auth-mark"><strong>01</strong><h1>把计划变成可积累的行动记录。</h1><p>周期计划、目标路线与今日待办，共用同一账户。</p></section>
    <form class="auth-card" @submit.prevent="submit">
      <h2>登录计划平台</h2>
      <p v-if="$route.query.passwordReset==='success'" class="success-text" role="status">密码已重置，请使用新密码重新登录。</p>
      <p v-if="$route.query.emailChanged==='1'" class="success-text" role="status">登录邮箱已变更，请使用新邮箱和原密码重新登录。其他设备也需要重新登录。</p>
      <p v-if="auth.connectionError" class="error-text" role="alert">{{ auth.connectionError }}</p>
      <button v-if="auth.connectionError" class="btn" type="button" :disabled="busy" @click="auth.bootstrap()">重新检查连接</button>
      <label>邮箱<input v-model="email" type="email" autocomplete="email" required /></label>
      <label>密码<input v-model="password" type="password" autocomplete="current-password" required /></label>
      <p v-if="error" class="error-text">{{ error }}</p>
      <button class="btn primary" :disabled="busy || !auth.ready">{{ !auth.ready ? '正在检查连接…' : busy ? '正在登录…' : '登录' }}</button>
      <router-link v-if="isDesktop()" to="/desktop">服务器连接与客户端更新</router-link><router-link v-else to="/welcome">返回产品首页</router-link>
      <router-link to="/help">帮助与本地演示</router-link>
      <router-link v-if="auth.registrationEnabled" to="/register">还没有账号？创建账号</router-link><router-link v-if="auth.passwordResetEnabled" to="/reset-password">忘记密码？</router-link>
      <p v-if="auth.ready && !auth.registrationEnabled && !auth.connectionError" class="auth-hint">当前未开放公开注册。</p>
    </form>
  </div>
</template>
