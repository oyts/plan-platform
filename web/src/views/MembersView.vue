<script setup lang="ts">
import { onMounted,onUnmounted, reactive, ref } from 'vue';
import axios from 'axios';
import { api, apiMessage } from '@/api/client';
import type { Invitation } from '@/types';
import AppSheet from '@/components/AppSheet.vue';
import OperationsView from '@/views/OperationsView.vue';
import {invitationState,invitationWait} from '@/adminCenter';

const rows = ref<Invitation[]>([]); const error = ref(''); const message = ref(''); const busy = ref(false); const oneTimeLink = ref(''); const draft = reactive({ email: '', expiresInHours: 168 });
const creating = ref(false); const loading = ref(true);
const now=ref(Date.now()),uncertain=ref(false);let pending:{email:string;expiresInHours:number;operationKey:string}|null=null;let timer:ReturnType<typeof setInterval>|undefined;
function remaining(row:Invitation){return Math.max(0,...rows.value.filter(r=>r.email===row.email).map(r=>invitationWait(r.retryAt,now.value)));}
async function load() { rows.value = (await api.get<Invitation[]>('/admin/invitations')).data; }
function status(row: Invitation) {return invitationState(row,now.value);}
async function create() {if(busy.value)return;busy.value=true;error.value='';message.value='';oneTimeLink.value='';pending??={...draft,operationKey:crypto.randomUUID()};try{const {data}=await api.post<Invitation>('/admin/invitations',pending);draft.email='';oneTimeLink.value=data.registrationUrl??'';message.value=data.replayed?'本次请求此前已创建成功，未再次发信。备用链接只在首次返回时显示；如遗失请等待冷却后重新邀请。':data.deliveryStatus==='SENT'?'邀请已创建，邮件已交给发件服务；这不保证对方已经收信。':'邀请已创建，但邮件投递不可用；本次备用注册链接只显示一次，请通过可信渠道发送。';pending=null;uncertain.value=false;await load();}catch(reason){uncertain.value=!(axios.isAxiosError(reason)&&[400,401,403,409,429].includes(reason.response?.status??0));if(!uncertain.value)pending=null;error.value=apiMessage(reason)+(uncertain.value?' 发送结果尚未确认；原请求编号保留，只能原样重试，不会自动发信。':'');}finally{busy.value=false;}}
async function revoke(row: Invitation) { if (!confirm(`撤销给 ${row.email} 的邀请？`)) return; busy.value = true; try { await api.delete(`/admin/invitations/${row.id}`); await load(); } catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; } }
async function copyLink() { try { await navigator.clipboard.writeText(oneTimeLink.value); message.value = '注册链接已复制。'; } catch { error.value = '浏览器无法访问剪贴板，请手动复制链接。'; } }
function renew(row:Invitation){if(uncertain.value||remaining(row)>0||!confirm(`为 ${row.email} 创建新邀请？该邮箱尚有效的旧邀请将失效。`))return;pending=null;draft.email=row.email;oneTimeLink.value='';message.value='请确认邮箱与有效期，然后创建新邀请。旧链接只在新邀请创建成功后失效。';creating.value=true;}
onMounted(() => load().catch(reason => error.value = apiMessage(reason)).finally(() => loading.value = false));
onMounted(()=>{timer=setInterval(()=>now.value=Date.now(),1000);});onUnmounted(()=>clearInterval(timer));
</script>
<template>
  <div class="page members-page">
    <header class="page-head"><div><h1>成员与邀请</h1><p>管理邀请，不查看成员的私人计划。周期计划、目标路线与今日待办始终属于各自账号。</p></div><button class="btn primary" :disabled="busy" @click="creating = true">{{uncertain?'核对上次邀请':'创建邀请'}}</button></header>
    <p v-if="error && !creating" class="error-text" role="alert">{{ error }}</p><p v-if="message && !creating" class="helper-text" role="status">{{ message }}</p>
    <div v-if="oneTimeLink && !creating" class="members-link-reminder"><p>本次注册链接只在当前页面保存，请及时发送给受邀人。</p><button class="btn" @click="creating = true">查看本次注册链接</button></div>
    <section class="section invitation-ledger" aria-labelledby="invitation-ledger-title">
      <header class="section-title"><h2 id="invitation-ledger-title">最近邀请记录（最多200条）</h2><span :aria-label="`${rows.length} 条邀请`">{{ rows.length }}</span></header>
      <p v-if="loading" class="empty" role="status">正在读取邀请…</p>
      <template v-else-if="rows.length">
        <div class="table-scroll invitation-table"><table><thead><tr><th>受邀邮箱</th><th>状态／重新邀请</th><th>到期时间</th><th>创建人</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td>{{ row.email }}</td><td><div class="invitation-inline"><span>{{status(row)}}</span><button v-if="!row.acceptedAt" class="btn" :aria-label="`重新邀请 ${row.email}`" :disabled="busy||uncertain||remaining(row)>0" @click="renew(row)">{{remaining(row)>0?`${remaining(row)}秒后重邀`:'重新邀请'}}</button></div><small>{{row.deliveryStatus==='SENT'?'发件服务已接受':row.deliveryStatus==='FALLBACK'?'创建成功，邮件未送出':'历史投递状态未知'}}</small></td><td>{{ new Date(row.expiresAt).toLocaleString('zh-CN') }}</td><td>{{ row.createdBy }}</td><td><button v-if="status(row)==='有效'" class="text-btn danger-text" :disabled="busy" @click="revoke(row)">撤销</button></td></tr></tbody></table></div>
        <div class="invitation-cards"><article v-for="row in rows" :key="row.id" class="invitation-card"><header><span class="invitation-status" :class="{ active: status(row)==='有效' }">{{ status(row) }}</span><button v-if="!row.acceptedAt" class="btn" :aria-label="`重新邀请 ${row.email}`" :disabled="busy||uncertain||remaining(row)>0" @click="renew(row)">{{remaining(row)>0?`${remaining(row)}秒后重邀`:'重新邀请'}}</button><button v-if="status(row)==='有效'" class="btn danger" :disabled="busy" @click="revoke(row)">撤销</button></header><h3>{{ row.email }}</h3><dl><div><dt>到期时间</dt><dd>{{ new Date(row.expiresAt).toLocaleString('zh-CN') }}</dd></div><div><dt>创建人</dt><dd>{{ row.createdBy }}</dd></div><div><dt>邮件投递</dt><dd>{{row.deliveryStatus==='SENT'?'发件服务已接受':row.deliveryStatus==='FALLBACK'?'未送出，首次返回备用链接':'历史状态未知'}}</dd></div></dl></article></div>
      </template>
      <p v-else-if="!error" class="empty">还没有邀请记录。创建邀请后，受邀人可使用链接注册。</p>
    </section>
    <p class="helper-text">链接过期或丢失时，可重新邀请同一邮箱；创建成功会使该邮箱旧的有效邀请失效，已注册账号请使用找回密码。</p>
    <OperationsView section="members"/>
    <AppSheet :open="creating" title="创建邀请" title-id="invite-editor-title" :busy="busy" @close="creating = false"><form class="sheet-form invitation-editor" @submit.prevent="create">
      <p v-if="error" class="error-text" role="alert">{{ error }}</p><p v-if="message" class="helper-text" role="status">{{ message }}</p>
      <label>受邀邮箱<input v-model="draft.email" :disabled="busy||uncertain" type="email" autocomplete="email" inputmode="email" required /></label><label>有效小时数<input v-model.number="draft.expiresInHours" :disabled="busy||uncertain" type="number" min="1" max="720" inputmode="numeric" required /></label><p class="helper-text">邀请有效期为1至720小时，默认7天；同邮箱60秒冷却。结果不确定时只重试同一编号，不自动重发。</p>
      <div v-if="oneTimeLink" class="invitation-link"><label>一次性注册链接<textarea :value="oneTimeLink" readonly rows="3" /></label><button class="btn" type="button" @click="copyLink">复制链接</button></div>
      <div class="button-row sheet-actions"><button class="btn" type="button" :disabled="busy" @click="creating = false">关闭</button><button class="btn primary" :disabled="busy">{{ busy ? '正在创建…' : '创建邀请' }}</button></div>
    </form></AppSheet>
  </div>
</template>
<style scoped>
.invitation-inline{display:flex;gap:12px;align-items:center;flex-wrap:wrap}.invitation-inline .btn{min-height:44px}.invitation-table small{display:block;color:var(--muted);font-size:.75rem;margin-top:8px}.invitation-card>header{flex-wrap:wrap}
.members-page .page-head p{max-width:640px;margin-bottom:0}.members-page .page-head>.btn{flex-shrink:0;min-height:44px}.invitation-ledger,.invitation-table{min-width:0;max-width:100%}.invitation-cards{display:none}.invitation-table td{overflow-wrap:anywhere}.invitation-editor{display:grid;gap:16px}.invitation-editor>label,.invitation-link label{display:grid;gap:8px;color:var(--muted);font-size:.8rem;font-weight:700}.invitation-editor .helper-text{margin:0}.invitation-link{display:grid;gap:12px;padding-top:16px;border-top:1px solid var(--line)}.invitation-link textarea{font-size:.8rem;overflow-wrap:anywhere}.invitation-link>.btn{justify-self:start;min-height:44px}.members-link-reminder{margin-top:18px;padding:16px;border:1px solid var(--line);background:var(--paper)}.members-link-reminder p{font-size:.85rem;color:var(--muted)}
@media(max-width:900px),(max-height:500px) and (pointer:coarse){
  .members-page .page-head{grid-template-columns:minmax(0,1fr);gap:16px}.members-page .page-head>.btn{justify-self:stretch}.invitation-ledger{margin-top:18px}.invitation-ledger .section-title{padding:16px;align-items:center;gap:12px}.invitation-ledger h2{font-size:1rem}.invitation-ledger .section-title>span{font-size:1.5rem;font-variant-numeric:tabular-nums}.invitation-table{display:none}.invitation-cards{display:grid}.invitation-card{min-width:0;padding:16px;border-bottom:1px solid var(--line)}.invitation-card:last-child{border-bottom:0}.invitation-card>header{display:flex;justify-content:space-between;align-items:center;gap:12px}.invitation-card .btn{min-height:44px}.invitation-status{color:var(--muted);font-size:.75rem;font-weight:700}.invitation-status.active{color:var(--blue)}.invitation-card h3{margin:12px 0;font-size:.95rem;line-height:1.5;overflow-wrap:anywhere}.invitation-card dl{display:grid;gap:8px;margin:0;font-size:.75rem}.invitation-card dl>div{display:grid;grid-template-columns:64px minmax(0,1fr);gap:12px}.invitation-card dt{color:var(--muted)}.invitation-card dd{margin:0;overflow-wrap:anywhere;line-height:1.5;font-variant-numeric:tabular-nums}.members-link-reminder{padding:14px}.members-link-reminder .btn{width:100%;min-height:44px}
}
</style>
