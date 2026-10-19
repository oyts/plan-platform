<script setup lang="ts">
import {onMounted,onUnmounted,ref} from 'vue';
import {api,apiMessage} from '@/api/client';
import {requiresConnection} from '@/connectionState';
import {useAuthStore} from '@/stores/auth';
import type {User} from '@/types';
interface VerificationStatus {enabled:boolean;configured:boolean;verified:boolean;retryAfterSeconds:number}
const auth=useAuthStore(),status=ref<VerificationStatus|null>(null),busy=ref(false),wait=ref(0),notice=ref(''),error=ref('');
let disposed=false;
async function read(){
  const {data}=await api.get<VerificationStatus>('/auth/email-verification/status');
  if(disposed)return;status.value=data;wait.value=Math.max(0,data.retryAfterSeconds);
}
async function refresh(){
  if(busy.value||requiresConnection())return;busy.value=true;error.value='';
  try{await read();const {data}=await api.get<User>('/auth/me');if(!disposed)auth.updateProfile(data);}
  catch(reason){if(!disposed)error.value=apiMessage(reason);}
  finally{busy.value=false;}
}
async function send(){
  if(busy.value||wait.value||requiresConnection()||!status.value?.enabled||!status.value.configured||status.value.verified)return;
  busy.value=true;error.value='';notice.value='';
  try{await api.post('/auth/email-verification/request');if(!disposed)notice.value='发件服务器已接受验证邮件，请查看收件箱或垃圾箱。这不等于已投递到收件箱。';}
  catch(reason){if(!disposed)error.value=apiMessage(reason);}
  finally{try{await read();}catch{/* Keep the original delivery result visible. */}busy.value=false;}
}
const clock=setInterval(()=>{if(wait.value>0)wait.value--;},1000);
onMounted(()=>{void refresh();});onUnmounted(()=>{disposed=true;clearInterval(clock);});
</script>
<template>
  <section class="section email-verification" aria-labelledby="email-verification-title">
    <header><div><h2 id="email-verification-title">邮箱验证</h2><p>确认登录邮箱属于你；不会改变原有计划或要求退出账号。</p></div><strong v-if="status" class="verification-state">{{status.verified?'已验证':'尚未验证'}}</strong></header>
    <p v-if="requiresConnection()" class="helper-text">验证状态和发送邮件需要联网；离线时不发送或排队重试。</p>
    <p v-else-if="!status&&!error" role="status">正在读取验证状态…</p>
    <p v-if="error" class="error-text" role="alert">{{error}}</p><p v-if="notice" role="status">{{notice}}</p>
    <template v-if="status"><p v-if="!status.enabled">服务器暂未启用邮箱验证，原账号仍可正常使用。</p><p v-else-if="!status.configured&&!status.verified">发件服务暂不可用，请联系管理员；不会把发送失败显示为已投递。</p><p v-else-if="!status.verified">邮件只发往你的登录邮箱 {{auth.user?.email}}，不会抄送管理员。打开邮件链接确认后，点击“重新读取验证状态”。</p></template>
    <div class="button-row"><button v-if="status?.enabled&&!status.verified" type="button" class="btn primary" :disabled="busy||wait>0||!status.configured||requiresConnection()" @click="send">{{wait>0?`请等待 ${wait} 秒`:'发送验证邮件'}}</button><button type="button" class="btn" :disabled="busy||requiresConnection()" @click="refresh">重新读取验证状态</button></div>
  </section>
</template>
<style scoped>
.email-verification{background:#fff;border:1px solid #d8d8df;padding:24px;margin:24px 0}.email-verification header{display:flex;justify-content:space-between;align-items:start;gap:20px}.email-verification h2{margin-top:0}.email-verification p{overflow-wrap:anywhere}.verification-state{color:#002fa7;flex-shrink:0;border-bottom:2px solid #002fa7;padding-bottom:6px}.email-verification .button-row{flex-wrap:wrap}
@media(max-width:700px){.email-verification{padding:16px}.email-verification header{display:grid;gap:8px}.verification-state{justify-self:start}.email-verification .btn{flex:1 1 100%}}
</style>
