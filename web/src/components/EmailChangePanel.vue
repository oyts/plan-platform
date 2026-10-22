<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref} from 'vue';
import {useRouter} from 'vue-router';
import {api,apiMessage} from '@/api/client';
import {requiresConnection} from '@/connectionState';
import {useAuthStore} from '@/stores/auth';
interface Status {enabled:boolean;configured:boolean;requestId:string|null;newEmail:string|null;state:string;oldVerified:boolean;newVerified:boolean;expiresAt:string|null;retryAfterSeconds:number}
const auth=useAuthStore(),router=useRouter(),status=ref<Status|null>(null),password=ref(''),newEmail=ref(''),confirmEmail=ref(''),confirmed=ref(false),busy=ref(false),wait=ref(0),error=ref(''),notice=ref('');
let disposed=false;
const pending=computed(()=>status.value?.state==='PENDING');
const ready=computed(()=>pending.value&&status.value?.oldVerified&&status.value?.newVerified&&confirmed.value&&Boolean(password.value));
async function read(){const {data}=await api.get<Status>('/auth/email-change/status');if(!disposed){status.value=data;wait.value=data.retryAfterSeconds;}}
async function refresh(){if(busy.value||requiresConnection())return;busy.value=true;error.value='';try{await read();}catch(reason){if(!disposed)error.value=apiMessage(reason);}finally{busy.value=false;}}
async function start(){
  if(busy.value||wait.value||requiresConnection()||!status.value?.enabled||!status.value.configured)return;
  if(newEmail.value.trim().toLowerCase()!==confirmEmail.value.trim().toLowerCase()){error.value='两次输入的新邮箱不一致。';return;}
  busy.value=true;error.value='';notice.value='';
  try{const {data}=await api.post<Status>('/auth/email-change/request',{currentPassword:password.value,newEmail:newEmail.value.trim()});if(!disposed){status.value=data;wait.value=data.retryAfterSeconds;notice.value='发件服务器已接受两封验证邮件，请分别在原邮箱和新邮箱主动确认，再返回这里重新读取状态。尚未更改登录邮箱。';confirmed.value=false;}}
  catch(reason){if(!disposed)error.value=apiMessage(reason);}finally{password.value='';try{await read();}catch{}busy.value=false;}
}
async function cancel(){
  if(!status.value?.requestId||busy.value||requiresConnection())return;busy.value=true;error.value='';
  try{await api.post(`/auth/email-change/${status.value.requestId}/cancel`);password.value='';confirmed.value=false;notice.value='请求已取消，两个验证链接均不可再使用。';await read();}
  catch(reason){error.value=apiMessage(reason);}finally{busy.value=false;}
}
async function complete(){
  if(!ready.value||busy.value||requiresConnection())return;busy.value=true;error.value='';
  try{await api.post('/auth/email-change/complete',{requestId:status.value!.requestId,currentPassword:password.value,confirmed:true});password.value='';await auth.logout().catch(()=>undefined);await router.replace({path:'/login',query:{emailChanged:'1'}});}
  catch(reason){error.value=apiMessage(reason);}finally{password.value='';busy.value=false;}
}
const clock=setInterval(()=>{if(wait.value>0)wait.value--;},1000);
onMounted(()=>void refresh());onUnmounted(()=>{disposed=true;password.value='';clearInterval(clock);});
</script>
<template>
  <section class="section email-change-panel" aria-labelledby="email-change-title">
    <header><h2 id="email-change-title">更改登录邮箱</h2><p>分别验证原邮箱和新邮箱，再由你最终确认。账号和计划保留，完成后所有设备需要重新登录。</p></header>
    <p v-if="requiresConnection()" class="helper-text">更改邮箱需要联网，不保存密码或把确认操作加入离线队列。</p>
    <p v-if="error" class="error-text" role="alert">{{error}}</p><p v-if="notice" role="status">{{notice}}</p>
    <p v-if="status&&!status.enabled">服务器暂未启用邮箱变更，当前邮箱仍可照常登录。</p>
    <p v-else-if="status&&!status.configured">邮件服务暂不可用，不能发起新的邮箱变更。</p>
    <template v-if="status?.enabled">
      <div v-if="pending" class="email-change-steps">
        <p>新邮箱：<strong>{{status.newEmail}}</strong> · 有效至 {{new Date(status.expiresAt!).toLocaleString()}}</p>
        <ol><li>原邮箱 {{auth.user?.email}}：<strong>{{status.oldVerified?'已验证':'等待邮件确认'}}</strong></li><li>新邮箱 {{status.newEmail}}：<strong>{{status.newVerified?'已验证':'等待邮件确认'}}</strong></li><li>回到本页最终确认：<strong>尚未更改</strong></li></ol>
        <p>只验证一个邮箱不会更改登录。链接有效期30分钟，不会抄送管理员。</p>
        <form @submit.prevent="complete"><label>再次输入当前密码<input v-model="password" type="password" autocomplete="current-password" required maxlength="200" :disabled="busy"/></label><label class="email-change-confirm"><input v-model="confirmed" type="checkbox" :disabled="busy"/>我确认使用新邮箱登录，理解所有设备的旧会话与系统提醒授权会撤销；本机待同步队列不会自动提交或删除。</label><div class="button-row"><button class="btn primary" :disabled="busy||!ready||requiresConnection()">确认变更并退出全部设备</button><button type="button" class="btn" :disabled="busy||requiresConnection()" @click="cancel">取消此次变更</button></div></form>
      </div>
      <form v-else class="email-change-form" @submit.prevent="start">
        <p v-if="status.state==='EXPIRED'||status.state==='INVALIDATED'||status.state==='CANCELLED'">上次请求{{status.state==='EXPIRED'?'已过期':status.state==='INVALIDATED'?'因账号安全信息变化失效':'已取消'}}，登录邮箱没有因此更改。</p>
        <label>新邮箱<input v-model="newEmail" type="email" autocomplete="off" maxlength="190" required :disabled="busy"/></label><label>再次输入新邮箱<input v-model="confirmEmail" type="email" autocomplete="off" maxlength="190" required :disabled="busy"/></label><label>当前密码<input v-model="password" type="password" autocomplete="current-password" required maxlength="200" :disabled="busy"/></label>
        <button class="btn primary" :disabled="busy||wait>0||!status.configured||requiresConnection()">{{wait>0?`请等待 ${wait} 秒`:'发送两封验证邮件'}}</button>
      </form>
    </template>
    <button type="button" class="btn email-change-refresh" :disabled="busy||requiresConnection()" @click="refresh">重新读取变更状态</button>
  </section>
</template>
<style scoped>
.email-change-panel{background:#fff;padding:24px;border:1px solid #d8d8df}.email-change-panel h2{margin-top:0}.email-change-panel p,.email-change-panel li{overflow-wrap:anywhere}.email-change-steps{border-top:1px solid #d8d8df;padding-top:12px}.email-change-steps ol{padding-left:24px;line-height:2}.email-change-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.email-change-form p{grid-column:1/-1}.email-change-form button{align-self:end}.email-change-confirm{display:flex;align-items:start;gap:12px;margin:16px 0}.email-change-confirm input{width:18px;height:18px;min-height:18px;padding:0;margin:3px 0;flex-shrink:0}.email-change-refresh{margin-top:16px}.email-change-panel .button-row{flex-wrap:wrap}
@media(max-width:700px){.email-change-panel{padding:16px}.email-change-form{grid-template-columns:1fr}.email-change-panel .btn{width:100%}}
</style>
