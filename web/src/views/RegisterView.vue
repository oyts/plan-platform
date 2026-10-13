<script setup lang="ts">
import {computed,ref,watch} from 'vue';
import {useRoute,useRouter} from 'vue-router';
import {apiMessage} from '@/api/client';
import {useAuthStore} from '@/stores/auth';
import {PASSWORD_MISMATCH,registrationPasswordError} from '@/registration';
const route=useRoute(),router=useRouter(),auth=useAuthStore();
const displayName=ref(''),email=ref(typeof route.query.email==='string'?route.query.email:''),password=ref(''),confirmation=ref('');
const invitationToken=ref(typeof route.query.invite==='string'?route.query.invite:''),error=ref(''),busy=ref(false);
const confirmationInput=ref<HTMLInputElement|null>(null);
const mismatched=computed(()=>error.value===PASSWORD_MISMATCH);
watch([password,confirmation],()=>{error.value='';});
async function submit(){
  if(busy.value)return;
  error.value=registrationPasswordError(password.value,confirmation.value);
  if(error.value){if(mismatched.value)confirmationInput.value?.focus();return;}
  busy.value=true;
  try{
    await auth.register(email.value,password.value,displayName.value,invitationToken.value);
    password.value='';confirmation.value='';await router.push('/');
  }catch(e){error.value=apiMessage(e);}finally{busy.value=false;}
}
</script>
<template>
  <div class="auth-page">
    <section class="auth-mark"><strong>02</strong><h1>建立属于自己的长期计划系统。</h1><p>每位用户的数据严格隔离，后端统一负责并发与持久化。</p></section>
    <form class="auth-card" @submit.prevent="submit">
      <h2>创建账号</h2>
      <template v-if="auth.registrationEnabled||invitationToken">
        <label>显示名称<input v-model="displayName" maxlength="80" :disabled="busy" required /></label>
        <label>邮箱<input v-model="email" type="email" autocomplete="email" :disabled="busy" required /></label>
        <label>密码<input v-model="password" type="password" minlength="10" maxlength="72" pattern="(?=.*[A-Za-z])(?=.*\d).+" autocomplete="new-password" aria-describedby="register-password-rules" :disabled="busy" required /></label>
        <p id="register-password-rules" class="auth-hint">密码需为 10—72 位，并同时包含英文字母和数字。</p>
        <label>确认密码<input ref="confirmationInput" v-model="confirmation" type="password" minlength="10" maxlength="72" autocomplete="new-password" aria-describedby="register-password-confirm-hint register-password-error" :aria-invalid="mismatched ? 'true' : undefined" :disabled="busy" required /></label>
        <p id="register-password-confirm-hint" class="auth-hint">再次输入密码，避免输入错误。</p>
        <p v-if="invitationToken" class="auth-hint">正在使用邀请注册链接。邮箱必须与收到邀请的邮箱一致。</p>
        <p v-if="error" id="register-password-error" class="error-text" role="alert">{{error}}</p>
        <button class="btn primary" :disabled="busy">{{busy?'正在创建…':'创建账号'}}</button>
      </template>
      <p v-else class="error-text">当前仅接受邀请注册链接注册。</p>
      <router-link to="/login">已有账号？返回登录</router-link>
    </form>
  </div>
</template>
<style scoped>
.auth-card{gap:16px}.auth-card .auth-hint{margin:0;font-size:.82rem;line-height:1.6;color:var(--muted)}.auth-card input[aria-invalid=true]{border-color:var(--danger)}
</style>
