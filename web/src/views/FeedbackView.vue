<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import axios from 'axios';
import appPackage from '../../package.json';
import { api, apiMessage, currentQueueOwner, serverScope } from '@/api/client';
import { isAndroid, isDesktop } from '@/desktop';
import { requiresConnection } from '@/connectionState';
import { listOperations } from '@/offlineQueue';
import { useAuthStore } from '@/stores/auth';
import { feedbackCategories, feedbackStatuses, newFeedback, safeDiagnostic, type FeedbackDetail, type FeedbackStatus, type FeedbackSummary } from '@/feedbackModel';
import { feedbackFailure, feedbackServer } from '@/feedbackDiagnostics';
import { loadFeedbackDraft, removeFeedbackDraft, saveFeedbackDraft } from '@/feedbackStorage';
import { downloadFile } from '@/download';
import type { PageResponse } from '@/types';

const auth=useAuthStore(), owner=currentQueueOwner(), scope=serverScope();
const draft=reactive(newFeedback()), saved=ref(JSON.stringify(draft)), reply=ref('');
const busy=ref(false), ready=ref(false), error=ref(''), notice=ref(''), admin=ref(false), status=ref('ALL'), page=ref(0);
const storageError=ref('');
const composeOpen=ref(true);
const rows=ref<PageResponse<FeedbackSummary>|null>(null), selected=ref<FeedbackDetail|null>(null), handling=ref<FeedbackStatus>('IN_PROGRESS');
const preview=ref(false), consent=ref(false); let active=true, readNumber=0;
const dirty=computed(()=>JSON.stringify(draft)!==saved.value), edited=computed(()=>dirty.value||Boolean(reply.value.trim()));
const valid=computed(()=>[draft.title,draft.steps,draft.expectedResult,draft.actualResult].every(value=>value.trim().length>0));
const root=computed(()=>admin.value?'/admin/feedback':'/feedback');
const inContext=()=>active&&owner===currentQueueOwner()&&scope===serverScope();
function clean(){saved.value=JSON.stringify(draft);}
watch(()=>[draft.title,draft.steps,draft.category,draft.expectedResult,draft.actualResult,draft.diagnostic],()=>{preview.value=false;consent.value=false;});
async function saveLocal(){
  if(!owner||busy.value||!ready.value)return;busy.value=true;error.value='';notice.value='';
  try{await saveFeedbackDraft(scope,owner,JSON.parse(JSON.stringify(draft)));if(inContext()){clean();notice.value='反馈草稿已保存在当前设备，仅当前服务器和账号可读取；尚未发送。';}}
  catch(e){error.value=apiMessage(e);}finally{busy.value=false;}
}
async function loadList(){
  if(requiresConnection()){rows.value=null;return;}
  const n=++readNumber, path=root.value;error.value='';
  try{const data=(await api.get<PageResponse<FeedbackSummary>>(path,{params:{status:status.value,page:page.value,size:10}})).data;if(inContext()&&n===readNumber)rows.value=data;}
  catch(e){if(inContext()&&n===readNumber)error.value=apiMessage(e);}
}
async function readTicket(id:string){
  if(reply.value.trim()&&!confirm('当前补充文字尚未发送，切换反馈会丢失它。确定切换吗？'))return;
  reply.value='';await refreshTicket(id);
}
async function refreshTicket(id=selected.value?.ticket.id){
  if(!id)return;const n=++readNumber;error.value='';
  try{const data=(await api.get<FeedbackDetail>(`${root.value}/${id}`)).data;if(inContext()&&n===readNumber){selected.value=data;handling.value=data.ticket.status;}}
  catch(e){if(inContext()&&n===readNumber)error.value=apiMessage(e);}
}
async function chooseAdmin(value:boolean,compose=false){if(busy.value||reply.value.trim()&&!confirm('切换列表将丢失尚未发送的补充文字，确定继续吗？'))return;admin.value=value;composeOpen.value=compose;page.value=0;selected.value=null;reply.value='';rows.value=null;await loadList();}
async function filter(){page.value=0;await loadList();}
async function move(delta:number){page.value+=delta;await loadList();}
async function diagnostic(){
  if(!owner||busy.value||draft.uncertain)return;busy.value=true;error.value='';
  try{
    const operations=await listOperations(owner);
    const summary=safeDiagnostic({schemaVersion:1,clientVersion:appPackage.version,platform:isAndroid()?'ANDROID':isDesktop()?'WINDOWS':'WEB',...feedbackServer(scope),...feedbackFailure(JSON.stringify([scope,owner])),queueCount:operations.length,conflictCount:operations.filter(row=>row.status==='CONFLICT').length});
    if(inContext())draft.diagnostic=summary;
  }catch(e){error.value=`无法生成诊断摘要：${apiMessage(e)}。可不附带诊断，直接反馈。`;}finally{busy.value=false;}
}
function removeDiagnostic(){draft.diagnostic=null;}
async function exportDiagnostic(){if(draft.diagnostic)try{await downloadFile(new Blob([`# 计划舱脱敏诊断\n\n${JSON.stringify(draft.diagnostic,null,2)}\n`],{type:'text/markdown;charset=utf-8'}),'plan-feedback-diagnostic.md');}catch(e){error.value=apiMessage(e);}}
async function submit(){
  if(!owner||busy.value||!ready.value||!valid.value||!preview.value||!consent.value||requiresConnection())return;
  busy.value=true;error.value='';notice.value='';
  // Persist the exact body and key before dispatch. A lost response must not turn into a second ticket.
  const wasUncertain=draft.uncertain;let dispatched=false;draft.uncertain=true;
  try{
    await saveFeedbackDraft(scope,owner,JSON.parse(JSON.stringify(draft)));clean();
    const {uncertain:_,...body}=JSON.parse(JSON.stringify(draft));
    dispatched=true;
    const result=(await api.post<FeedbackDetail>('/feedback',body)).data;
    if(!inContext())return;
    await removeFeedbackDraft(scope,owner);
    Object.assign(draft,newFeedback());clean();preview.value=false;consent.value=false;
    admin.value=false;composeOpen.value=false;selected.value=result;reply.value='';page.value=0;status.value='ALL';
    notice.value='反馈已发送。可在下方查看处理状态与管理员回复。';await loadList();
  }catch(e){
    if(!inContext())return;
    // Only definite rejection before creation unlocks editing. A replay conflict stays frozen for investigation.
    if(!dispatched||!wasUncertain&&axios.isAxiosError(e)&&[400,401,403,429].includes(e.response?.status??0)){
      draft.uncertain=false;try{await saveFeedbackDraft(scope,owner,JSON.parse(JSON.stringify(draft)));clean();}catch{ /* Leave dirty and warn through the main error. */ }
    }
    error.value=`${apiMessage(e)}${draft.uncertain?' 原提交内容和编号已保留；请联网后重新预览并重试同一份，不要重复新建。':''}`;
  }finally{busy.value=false;}
}
async function sendReply(){
  if(!selected.value||busy.value||requiresConnection()||(!admin.value&&!reply.value.trim()))return;
  busy.value=true;error.value='';notice.value='';const item=selected.value;
  try{
    const data=admin.value?(await api.put<FeedbackDetail>(`${root.value}/${item.ticket.id}`,{status:handling.value,body:reply.value,version:item.ticket.version})).data
      :(await api.post<FeedbackDetail>(`${root.value}/${item.ticket.id}/replies`,{body:reply.value,version:item.ticket.version})).data;
    if(inContext()){selected.value=data;reply.value='';notice.value=admin.value?'处理记录已保存。':'补充已发送，反馈重新进入待处理列表。';await loadList();}
  }catch(e){if(inContext())error.value=`${apiMessage(e)} 当前文字仍保留；可读取最新记录后核对再提交。`;}finally{busy.value=false;}
}
function mayLeave(){return !currentQueueOwner()||!busy.value&&(!edited.value||confirm('反馈有尚未保存在本机的修改或未发送的补充，确定离开吗？'));}
function beforeUnload(event:BeforeUnloadEvent){if(edited.value||busy.value){event.preventDefault();event.returnValue='';}}
onBeforeRouteLeave(mayLeave);
onMounted(async()=>{window.addEventListener('beforeunload',beforeUnload);try{if(owner){const row=await loadFeedbackDraft(scope,owner);if(inContext()&&row){Object.assign(draft,row);clean();notice.value='已恢复当前设备的反馈草稿，尚未自动发送。';}}ready.value=true;}catch(e){storageError.value=apiMessage(e);/* Do not overwrite an unreadable draft. */}await loadList();});
onUnmounted(()=>{active=false;readNumber++;window.removeEventListener('beforeunload',beforeUnload);});
</script>

<template><div class="page feedback-page">
  <header class="page-head"><div><p class="feedback-eyebrow">一起改进计划舱</p><h1>反馈与诊断</h1><p>告诉我们遇到了什么。你的反馈仅本人和管理员可见，不会公开展示。</p></div><router-link class="btn" to="/learn">查看使用教学</router-link></header>
  <nav class="button-row feedback-tabs" aria-label="反馈功能"><button class="btn" :aria-pressed="composeOpen&&!admin" :disabled="busy" @click="chooseAdmin(false,true)">填写反馈</button><button class="btn" :aria-pressed="!composeOpen&&!admin" :disabled="busy" @click="chooseAdmin(false)">我的反馈</button><button v-if="auth.user?.role==='ADMIN'" class="btn" :aria-pressed="admin" :disabled="busy" @click="chooseAdmin(true)">管理员处理</button></nav>
  <p v-if="error" class="error-text" role="alert">{{error}}</p><p v-if="notice" class="success-text" role="status">{{notice}}</p>
  <p v-if="storageError" class="error-text" role="alert">{{storageError}} 本机草稿暂不可读取，为避免覆盖，已暂停新建反馈；请勿清理存储。</p>
  <p v-if="requiresConnection()" class="feedback-offline" role="status">当前离线：可编辑并手动保存本机反馈草稿。提交、历史与处理回复需要联网，不会加入任务同步队列。</p>
  <div class="feedback-layout" :class="{'feedback-layout--single':admin||!composeOpen}"><section v-show="composeOpen&&!admin" class="feedback-compose" aria-labelledby="feedback-create"><h2 id="feedback-create">写一条反馈</h2>
    <p class="helper-text">请勿填写密码、验证码、邀请链接、API Key、私人计划或其他人的信息。首版不接收附件、截图或完整日志。</p>
    <p v-if="draft.uncertain" class="feedback-offline">上次发送结果尚未确认，内容已锁定。可先查阅“我的反馈”，或原样重试；服务端会按同一编号防止重复创建。</p>
    <form @submit.prevent="submit"><fieldset :disabled="busy||!ready||draft.uncertain">
      <label>反馈分类<select v-model="draft.category"><option v-for="(label,key) in feedbackCategories" :key="key" :value="key">{{label}}</option></select></label>
      <label>问题摘要<input v-model="draft.title" maxlength="120" required placeholder="例如：教学日期提示不清楚"/></label>
      <label>操作步骤<textarea v-model="draft.steps" rows="4" maxlength="4000" required placeholder="从哪个入口开始，进行了哪些操作？请使用不含私人数据的例子。"/></label>
      <label>期望的结果<textarea v-model="draft.expectedResult" rows="3" maxlength="2000" required/></label>
      <label>实际的结果<textarea v-model="draft.actualResult" rows="3" maxlength="2000" required/></label>
    </fieldset>
    <section class="feedback-diagnostic" aria-label="可选诊断摘要"><h3>可选 · 脱敏诊断</h3><p>只包含版本、平台、时间、错误分类、随机关联编号与队列数量。不包含任务内容、账号、设备序列号或网络地址。不会自动上传。</p>
      <button v-if="!draft.diagnostic" class="btn" type="button" :disabled="busy||draft.uncertain" @click="diagnostic">生成并查看诊断摘要</button>
      <template v-else><pre data-testid="diagnostic-preview">{{JSON.stringify(draft.diagnostic,null,2)}}</pre><div class="button-row"><button class="btn" type="button" @click="exportDiagnostic">仅导出诊断到本机</button><button class="btn" type="button" :disabled="busy||draft.uncertain" @click="removeDiagnostic">不附带诊断</button></div></template>
    </section>
    <div class="button-row"><button class="btn" type="button" :disabled="busy||!ready" @click="saveLocal">保存本机草稿</button><button class="btn" type="button" :disabled="busy||!ready||!valid" @click="preview=true;consent=false">预览待发送内容</button></div>
    <p class="helper-text">本机草稿需手动保存，不跨设备同步。{{dirty?'有尚未保存的修改。':'当前输入无未保存修改。'}}</p>
    <section v-if="preview" class="feedback-preview" aria-label="发送前预览"><h3>发送前核对</h3><dl><dt>分类</dt><dd>{{feedbackCategories[draft.category]}}</dd><dt>摘要</dt><dd>{{draft.title}}</dd><dt>步骤</dt><dd>{{draft.steps}}</dd><dt>期望</dt><dd>{{draft.expectedResult}}</dd><dt>实际</dt><dd>{{draft.actualResult}}</dd><dt>诊断</dt><dd>{{draft.diagnostic?'附带上方显示的诊断摘要':'不附带'}}</dd></dl><label class="feedback-check"><input v-model="consent" type="checkbox"/>我已核对内容不含敏感信息，同意将以上内容发送给管理员。</label><button class="btn primary" :disabled="busy||!consent||requiresConnection()">{{busy?'正在保存并提交…':draft.uncertain?'重试同一份反馈':'确认发送反馈'}}</button></section>
    </form>
  </section>
  <section class="feedback-inbox" aria-labelledby="feedback-inbox"><header><h2 id="feedback-inbox">{{admin?'管理员处理':'我的反馈'}}</h2></header>
    <div class="feedback-filters"><label>处理状态<select v-model="status" :disabled="busy" @change="filter"><option value="ALL">全部</option><option v-for="(label,key) in feedbackStatuses" :key="key" :value="key">{{label}}</option></select></label><button class="btn" :disabled="busy||requiresConnection()" @click="loadList">刷新列表</button></div>
    <p v-if="!rows" class="helper-text">联网后可读取反馈记录。草稿不会自动发送。</p><p v-else-if="!rows.content.length" class="empty">这个列表还没有反馈。</p>
    <button v-for="item in rows?.content" :key="item.id" class="feedback-item" :aria-current="selected?.ticket.id===item.id?'true':undefined" :disabled="busy" @click="readTicket(item.id)"><span class="feedback-status">{{feedbackStatuses[item.status]}} · {{feedbackCategories[item.category]}}</span><strong>{{item.title}}</strong><small>{{new Date(item.updatedAt).toLocaleString('zh-CN')}}</small></button>
    <footer v-if="rows" class="pagination-bar"><span>共 {{rows.totalElements}} 条</span><div class="button-row"><button class="btn" :disabled="busy||page===0" @click="move(-1)">上一页</button><button class="btn" :disabled="busy||page>=rows.totalPages-1" @click="move(1)">下一页</button></div></footer>
    <article v-if="selected" class="feedback-detail" aria-label="反馈详情"><p class="feedback-status">{{feedbackStatuses[selected.ticket.status]}} · {{selected.ticket.id}}</p><h3>{{selected.ticket.title}}</h3><dl><dt>操作步骤</dt><dd>{{selected.steps}}</dd><dt>期望结果</dt><dd>{{selected.expectedResult}}</dd><dt>实际结果</dt><dd>{{selected.actualResult}}</dd></dl><details v-if="selected.diagnostic"><summary>已提交的诊断摘要</summary><pre>{{JSON.stringify(selected.diagnostic,null,2)}}</pre></details>
      <h3>处理与补充</h3><p v-if="!selected.messages.length" class="helper-text">暂时没有处理记录。</p><ol class="feedback-messages"><li v-for="message in selected.messages" :key="message.id"><header><strong>{{message.authorKind==='ADMIN'?'管理员':'反馈用户'}}</strong><time>{{new Date(message.createdAt).toLocaleString('zh-CN')}}</time></header><p>{{message.body}}</p></li></ol>
      <form @submit.prevent="sendReply"><label v-if="admin">更新状态<select v-model="handling" aria-label="更新状态" :disabled="busy"><option v-for="(label,key) in feedbackStatuses" :key="key" :value="key">{{label}}</option></select></label><label>{{admin?'处理说明':'补充信息'}}<textarea v-model="reply" :disabled="busy" rows="4" maxlength="4000" :required="!admin||['NEEDS_INFO','RESOLVED','CLOSED'].includes(handling)"/></label><p class="helper-text">不要填写秘密或私人计划内容。若记录已在其他设备更新，会保留你的输入并要求核对，不会强行覆盖。</p><div class="button-row"><button class="btn primary" :disabled="busy||requiresConnection()">{{admin?'保存处理记录':'发送补充'}}</button><button class="btn" type="button" :disabled="busy||requiresConnection()" @click="refreshTicket()">读取最新记录（保留输入）</button></div></form>
    </article>
  </section></div>
</div></template>

<style scoped>
.feedback-tabs{margin:24px 0}.feedback-tabs .btn{min-width:max-content}.feedback-layout.feedback-layout--single{grid-template-columns:minmax(0,1fr)}
.feedback-page p{line-height:1.8}.feedback-eyebrow{color:#002fa7;font-size:13px;letter-spacing:.1em;margin:0 0 10px}.feedback-layout{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:40px;margin:28px 0}.feedback-compose,.feedback-inbox{min-width:0;border-top:2px solid #002fa7;padding-top:20px}.feedback-compose form,.feedback-detail form{display:grid;gap:18px}.feedback-page fieldset{border:0;padding:0;margin:0;display:grid;gap:18px;min-width:0}.feedback-page label{display:grid;gap:8px}.feedback-page textarea,.feedback-page input:not([type=checkbox]),.feedback-page select{width:100%;min-width:0}.feedback-page .button-row{flex-wrap:wrap}.feedback-diagnostic,.feedback-preview{border:1px solid #d8dbe2;padding:20px;background:#f7f7f8;min-width:0}.feedback-page h3{margin-top:0}.feedback-page pre{font-size:12px;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere;max-width:100%;background:#fff;border:1px solid #ddd;padding:12px}.feedback-page dl{margin:16px 0}.feedback-page dt{font-size:13px;color:#646b77;margin-top:16px}.feedback-page dd{margin:6px 0 0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.8}.feedback-page .feedback-check{display:flex;align-items:flex-start;gap:10px;line-height:1.8;margin:20px 0}.feedback-check input{flex:0 0 auto;margin-top:6px}.feedback-offline{border-left:3px solid #002fa7;background:#f7f7f8;padding:16px}.feedback-filters{display:flex;align-items:end;gap:12px;margin:24px 0}.feedback-item{display:grid;gap:12px;width:100%;background:transparent;border:0;border-bottom:1px solid #ddd;padding:22px 0;text-align:left;cursor:pointer;color:#131a26}.feedback-item strong{overflow-wrap:anywhere;font-size:18px}.feedback-item small{color:#646b77}.feedback-item[aria-current=true]{border-left:3px solid #002fa7;padding-left:14px}.feedback-status{font-size:12px;color:#002fa7;overflow-wrap:anywhere}.feedback-page .pagination-bar{gap:12px;flex-wrap:wrap;margin:20px 0}.feedback-detail{border-top:1px solid #ddd;margin-top:28px;padding-top:24px}.feedback-messages{list-style:none;margin:0 0 24px;padding:0}.feedback-messages li{padding:18px 0;border-bottom:1px solid #ddd}.feedback-messages header{display:flex;flex-wrap:wrap;gap:10px;justify-content:space-between}.feedback-messages time{font-size:12px;color:#646b77}.feedback-messages p{white-space:pre-wrap;overflow-wrap:anywhere}.feedback-page [aria-pressed=true]{border-color:#002fa7;color:#002fa7;background:#edf2ff}
@media(max-width:850px){.feedback-layout{grid-template-columns:minmax(0,1fr);gap:32px}.feedback-diagnostic,.feedback-preview{padding:16px}.feedback-page .button-row .btn{flex:1}.feedback-filters label{flex:1}.feedback-page .page-head{align-items:flex-start}.feedback-inbox{scroll-margin-top:90px}}
</style>
