<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, apiMessage } from '@/api/client';
import { useAuthStore } from '@/stores/auth';
import {registrationPasswordError,PASSWORD_MISMATCH} from '@/registration';

const route = useRoute(); const router = useRouter(); const auth = useAuthStore();
const email = ref(typeof route.query.email === 'string' ? route.query.email : ''); const token = ref(typeof route.query.token === 'string' ? route.query.token : ''); const password = ref(''); const message = ref(''); const error = ref(''); const busy = ref(false);
const confirming = computed(() => Boolean(token.value)),confirmation=ref(''),cooldown=ref(0),confirmationInput=ref<HTMLInputElement|null>(null);
let timer:ReturnType<typeof setInterval>|undefined;
function startCooldown(){clearInterval(timer);cooldown.value=60;timer=setInterval(()=>{if(--cooldown.value<=0)clearInterval(timer);},1000);}
async function requestReset(){if(busy.value||cooldown.value)return;busy.value=true;error.value='';try{await api.post('/auth/password-reset/request',{email:email.value});message.value='如果该邮箱已注册且可接收重置邮件，链接将发送到该邮箱。请检查收件箱和垃圾箱；重发后旧链接失效。';startCooldown();}catch(reason){error.value=apiMessage(reason);}finally{busy.value=false;}}
async function confirmReset(){if(busy.value)return;error.value=registrationPasswordError(password.value,confirmation.value);if(error.value){if(error.value===PASSWORD_MISMATCH)confirmationInput.value?.focus();return;}busy.value=true;try{await api.post('/auth/password-reset/confirm',{email:email.value,token:token.value,newPassword:password.value});password.value='';confirmation.value='';token.value='';message.value='密码已重置，请使用新密码重新登录。';await router.replace('/login?passwordReset=success');}catch(reason){error.value=apiMessage(reason);}finally{busy.value=false;}}
async function requestAgain(){token.value='';password.value='';confirmation.value='';error.value='';message.value='';await router.replace({path:'/reset-password',query:{email:email.value}});}
onUnmounted(()=>clearInterval(timer));
</script>
<template><div class="auth-page"><section class="auth-mark"><strong>03</strong><h1>恢复账号访问。</h1><p>重置链接只发送到已绑定的邮箱，且只能使用一次。</p></section>
<form v-if="auth.passwordResetEnabled" class="auth-card" @submit.prevent="confirming?confirmReset():requestReset()"><h2>{{confirming?'设置新密码':'找回密码'}}</h2>
<label>邮箱<input v-model="email" type="email" autocomplete="email" :disabled="busy" required /></label>
<template v-if="confirming"><label>新密码<input v-model="password" type="password" minlength="10" maxlength="72" pattern="(?=.*[A-Za-z])(?=.*\d).+" autocomplete="new-password" :disabled="busy" required /></label><p class="auth-hint">密码需为 10—72 位，并同时包含英文字母和数字。</p>
<label>确认新密码<input ref="confirmationInput" v-model="confirmation" type="password" minlength="10" maxlength="72" autocomplete="new-password" :aria-invalid="error===PASSWORD_MISMATCH?'true':undefined" :disabled="busy" required /></label><p class="auth-hint">重置成功后，所有设备的刷新会话会失效，请使用新密码重新登录。</p></template>
<p v-if="message" class="success-text" role="status">{{message}}</p><p v-if="error" class="error-text" role="alert">{{error}}</p>
<button class="btn primary" :disabled="busy||(!confirming&&cooldown>0)">{{busy?'处理中…':confirming?'设置新密码':cooldown>0?`${cooldown} 秒后可重发`:'发送重置链接'}}</button>
<button v-if="confirming" type="button" class="btn" :disabled="busy" @click="requestAgain">链接失效？重新获取</button><router-link to="/login">返回登录</router-link></form>
<section v-else class="auth-card"><h2>找回密码暂未开放</h2><p>当前服务器尚未启用邮件重置服务。请联系管理员；不要将账号密码发给他人。</p><router-link to="/login">返回登录</router-link></section></div></template>
<style scoped>.auth-card{gap:16px}.auth-hint{font-size:.82rem;color:var(--muted);line-height:1.7;margin:0}.auth-card input[aria-invalid=true]{border-color:var(--danger)}</style>
