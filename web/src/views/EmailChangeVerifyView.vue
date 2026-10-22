<script setup lang="ts">
import {computed,onMounted,ref,watch} from 'vue';
import {useRoute,useRouter} from 'vue-router';
import {api,apiMessage} from '@/api/client';
const route=useRoute(),router=useRouter(),error=ref(''),busy=ref(false),done=ref(false);
const requestId=ref(''),side=ref(''),token=ref('');let generation=0;
function capture(hash:string){if(!hash)return;generation++;const fields=new URLSearchParams(hash.replace(/^#/,''));requestId.value=fields.get('request')??'';side.value=fields.get('side')??'';token.value=fields.get('token')??'';done.value=false;error.value='';}
capture(route.hash);
const valid=computed(()=>/^[a-f0-9-]{36}$/i.test(requestId.value)&&['old','new'].includes(side.value)&&token.value.length>=60);
function clean(){if(route.hash)void router.replace({path:'/change-email',query:{},hash:''});}
watch(()=>route.hash,hash=>{if(hash){capture(hash);clean();}});onMounted(clean);
async function verify(){
  if(!valid.value||busy.value||done.value)return;const expected=generation;busy.value=true;error.value='';
  try{await api.post('/auth/email-change/verify',{requestId:requestId.value,side:side.value,token:token.value});if(expected===generation){done.value=true;token.value='';}}
  catch(reason){if(expected===generation)error.value=apiMessage(reason);}finally{busy.value=false;}
}
</script>
<template>
  <div class="auth-page"><section class="auth-mark"><strong>{{side==='old'?'01':'02'}}</strong><h1>确认邮箱变更验证。</h1><p>只有原邮箱与新邮箱都验证完成，再由原账号最终确认，才会更改登录邮箱。</p></section>
    <section class="auth-card"><h2>{{side==='old'?'原邮箱':'新邮箱'}}验证</h2><p v-if="done" class="success-text" role="status">本邮箱验证完成，登录邮箱尚未更改。请返回原账号的“账号安全与数据迁移”，重新读取状态并完成最终确认。</p><template v-else><p v-if="!valid" class="error-text">链接不完整，请从本次验证邮件重新打开。</p><p v-else>请确认这是你本人发起的更改登录邮箱请求。打开此页不会自动验证。</p><p v-if="error" class="error-text" role="alert">{{error}}</p><button v-if="valid" class="btn primary" :disabled="busy" @click="verify">{{busy?'正在确认…':'确认本邮箱验证'}}</button></template><router-link to="/settings">返回账号设置</router-link></section>
  </div>
</template>
