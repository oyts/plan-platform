<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, apiMessage } from '@/api/client';
import { useAuthStore } from '@/stores/auth';

const route = useRoute(); const router = useRouter(); const auth = useAuthStore();
const email = ref(typeof route.query.email === 'string' ? route.query.email : ''); const token = ref(typeof route.query.token === 'string' ? route.query.token : ''); const password = ref(''); const message = ref(''); const error = ref(''); const busy = ref(false);
const confirming = computed(() => Boolean(token.value));
async function requestReset() { busy.value = true; error.value = ''; try { await api.post('/auth/password-reset/request', { email: email.value }); message.value = '如果该邮箱已注册且邮件服务已启用，重置链接将发送到该邮箱。'; } catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; } }
async function confirmReset() { busy.value = true; error.value = ''; try { await api.post('/auth/password-reset/confirm', { email: email.value, token: token.value, newPassword: password.value }); await router.replace('/login'); } catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; } }
</script>
<template><div class="auth-page"><section class="auth-mark"><strong>03</strong><h1>恢复账号访问。</h1><p>重置链接只发送到已绑定的邮箱，且只能使用一次。</p></section><form v-if="auth.passwordResetEnabled" class="auth-card" @submit.prevent="confirming ? confirmReset() : requestReset()"><p class="eyebrow">ACCOUNT / PASSWORD RECOVERY</p><h2>{{ confirming ? '设置新密码' : '找回密码' }}</h2><label>邮箱<input v-model="email" type="email" autocomplete="email" required /></label><template v-if="confirming"><label>新密码<input v-model="password" type="password" minlength="10" pattern="(?=.*[A-Za-z])(?=.*\d).+" autocomplete="new-password" required /></label><p class="auth-hint">密码至少 10 位，并同时包含英文字母和数字。</p></template><p v-if="message" class="success-text">{{ message }}</p><p v-if="error" class="error-text">{{ error }}</p><button class="btn primary" :disabled="busy">{{ busy ? '处理中…' : confirming ? '设置新密码' : '发送重置链接' }}</button><router-link to="/login">返回登录</router-link></form><section v-else class="auth-card"><p class="eyebrow">ACCOUNT / PASSWORD RECOVERY</p><h2>找回密码暂未开放</h2><p>当前服务器尚未配置受控的邮件重置服务。请联系管理员处理账号恢复。</p><router-link to="/login">返回登录</router-link></section></div></template>
