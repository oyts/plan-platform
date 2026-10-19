<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, apiMessage } from '@/api/client';
import {useAuthStore} from '@/stores/auth';
const auth=useAuthStore();
const route = useRoute(); const router = useRouter(); const token = ref(typeof route.query.token === 'string' ? route.query.token : ''); const error = ref(''); const completed = ref(false); const busy = ref(false); const valid = computed(() => Boolean(token.value));
let generation=0;
watch(()=>route.query.token,value=>{if(completed.value&&value===undefined)return;generation++;token.value=typeof value==='string'?value:'';completed.value=false;error.value='';});
async function verify() { if(busy.value||!valid.value)return;const expected=generation;busy.value = true; error.value = ''; try { await api.post('/auth/email-verification/confirm', { token: token.value });if(expected!==generation)return;completed.value = true;token.value='';await router.replace({path:'/verify-email',query:{}}); } catch (reason) { if(expected===generation)error.value = apiMessage(reason); } finally { busy.value = false; } }
</script>
<template><div class="auth-page"><section class="auth-mark"><strong>04</strong><h1>确认邮箱归属。</h1><p>验证只确认邮箱归属，不会修改你的计划或密码。</p></section><section class="auth-card"><h2>验证邮箱</h2><template v-if="completed"><p class="success-text" role="status">邮箱验证完成。可返回账号设置重新读取验证状态。</p><router-link v-if="auth.user" to="/settings">返回账号设置</router-link><router-link v-else to="/login">返回登录</router-link></template><template v-else><p v-if="valid">验证链接只能使用一次，请确认是你本人请求的邮件。</p><p v-if="error" class="error-text" role="alert">{{error}}</p><p v-if="!valid" class="error-text">验证链接不完整。</p><button v-if="valid" class="btn primary" :disabled="busy" @click="verify">{{busy?'正在验证…':'确认验证'}}</button><p>链接过期或已使用时，可登录后在账号设置重新发送；不要把验证链接发给其他人。</p><router-link :to="auth.user?'/settings':'/login'">{{auth.user?'前往账号设置':'前往登录'}}</router-link></template></section></div></template>
