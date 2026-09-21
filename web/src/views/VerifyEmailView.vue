<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, apiMessage } from '@/api/client';
const route = useRoute(); const router = useRouter(); const token = ref(typeof route.query.token === 'string' ? route.query.token : ''); const error = ref(''); const completed = ref(false); const busy = ref(false); const valid = computed(() => Boolean(token.value));
async function verify() { busy.value = true; error.value = ''; try { await api.post('/auth/email-verification/confirm', { token: token.value }); completed.value = true; } catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; } }
</script>
<template><div class="auth-page"><section class="auth-mark"><strong>04</strong><h1>确认邮箱归属。</h1><p>验证完成后，邀请与账号恢复通知将发送到此邮箱。</p></section><section class="auth-card"><p class="eyebrow">ACCOUNT / EMAIL VERIFICATION</p><h2>验证邮箱</h2><template v-if="completed"><p class="success-text">邮箱验证完成。请返回登录或刷新当前会话。</p><router-link to="/login">返回登录</router-link></template><template v-else-if="valid"><p>验证链接只能使用一次。</p><p v-if="error" class="error-text">{{ error }}</p><button class="btn primary" :disabled="busy" @click="verify">{{ busy ? '正在验证…' : '确认验证' }}</button></template><template v-else><p class="error-text">验证链接不完整。</p><router-link to="/login">返回登录</router-link></template></section></div></template>
