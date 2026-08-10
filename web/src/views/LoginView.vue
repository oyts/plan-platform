<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { apiMessage } from '@/api/client';
import { useAuthStore } from '@/stores/auth';
const email = ref(''); const password = ref(''); const error = ref(''); const busy = ref(false);
const auth = useAuthStore(); const router = useRouter();
async function submit() { busy.value = true; error.value = ''; try { await auth.login(email.value, password.value); await router.push('/'); } catch (e) { error.value = apiMessage(e); } finally { busy.value = false; } }
</script>
<template><div class="auth-page"><section class="auth-mark"><strong>01</strong><h1>把计划变成可积累的行动记录。</h1><p>Web、桌面、手机和小程序将共用同一账户与数据。</p></section><form class="auth-card" @submit.prevent="submit"><p class="eyebrow">ACCOUNT / LOGIN</p><h2>登录计划平台</h2><label>邮箱<input v-model="email" type="email" autocomplete="email" required /></label><label>密码<input v-model="password" type="password" autocomplete="current-password" required /></label><p v-if="error" class="error-text">{{ error }}</p><button class="btn primary" :disabled="busy">{{ busy ? '正在登录…' : '登录' }}</button><router-link to="/register">还没有账号？创建账号</router-link></form></div></template>
