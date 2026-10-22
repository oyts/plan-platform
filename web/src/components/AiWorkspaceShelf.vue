<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { api, apiMessage, currentQueueOwner, serverScope } from '@/api/client';
import { WorkspaceStore, WorkspaceAutosave, exportWorkspace, parseWorkspace, type WorkspaceContent, type LocalWorkspace, type SavedWorkspace, type WorkspaceSummary } from '@/aiWorkbench';
import type { AiSession } from '@/aiPlanning';
const props = defineProps<{ content: WorkspaceContent; context: string; ready: boolean; pending: boolean; modified: boolean }>();
const emit = defineEmits<{ restore: [WorkspaceContent]; resumed: [AiSession, WorkspaceContent]; protected: [boolean] }>();
const locals = ref<LocalWorkspace[]>([]), cloud = ref<WorkspaceSummary[]>([]), active = ref<LocalWorkspace | null>(null), saved = ref<SavedWorkspace | null>(null);
const name = ref('我的规划草稿'), status = ref('尚未保存'), error = ref(''), busy = ref(false), recovery = ref<LocalWorkspace | null>(null), cloudConflict = ref<SavedWorkspace | null>(null), expanded = ref(false);
const skippedNames=ref<string[]>([]);
let store: WorkspaceStore | null = null, disposed = false, scope = '', owner = ''; const autosave = new WorkspaceAutosave();
const hasContent = computed(() => !!(props.content.message || props.content.importJson || props.content.draft));
const currentCopy = () => parseWorkspace(props.content);
function identityValid() { return owner === currentQueueOwner() && scope === serverScope(); }
async function reload() { if (!store || !identityValid()) return; locals.value = await store.list(); try { cloud.value = (await api.get<WorkspaceSummary[]>('/ai/saved-drafts')).data; } catch { /* Local material remains accessible without network. */ } }
function record(): LocalWorkspace { return { id: active.value?.id ?? crypto.randomUUID(), context: props.context, name: name.value.trim() || '我的规划草稿', content: currentCopy(), updatedAt: new Date().toISOString(), cloudId: active.value?.cloudId ?? saved.value?.id ?? null, cloudVersion: active.value?.cloudVersion ?? saved.value?.version ?? -1 }; }
async function localSave() {
  if(!hasContent.value&&!active.value)return;
  if (!store || !identityValid()) throw new Error('账号或服务器已变化，请重新打开草稿箱。');
  const value = record(); await store.save(value); active.value = value; status.value = '已存本机'; emit('protected', true); await reloadLocal();
}
async function reloadLocal() { if(store) locals.value = await store.list(); }
async function saveNow() { await autosave.cancel(); try { await localSave(); error.value = '';return true; } catch(e) { status.value = '保存失败'; error.value = apiMessage(e); emit('protected', false);return false; } }
watch(() => [props.content, name.value], () => {
  if (!props.ready || !store || recovery.value || disposed || (!hasContent.value&&!active.value)) return;
  status.value = '正在保存本机…'; emit('protected', false);
  autosave.schedule(localSave, e => { if(e) { status.value='保存失败';error.value=apiMessage(e);emit('protected',false); } });
}, { deep: true });
watch(() => props.context, async () => { await autosave.cancel(); if(!store)return; active.value=null;saved.value=null;cloudConflict.value=null;await reloadLocal();recovery.value=locals.value.find(v=>v.context===props.context)??null;status.value=recovery.value?'发现本机草稿':'尚未保存'; });
async function preserveBeforeSwitch(){await autosave.cancel();return !props.modified || await saveNow() || window.confirm('当前内容保存失败，切换可能丢失输入，仍要继续？');}
async function restoreLocal(value: LocalWorkspace) { if(!(await preserveBeforeSwitch()))return;const latest=locals.value.find(v=>v.id===value.id)??value;active.value=latest;saved.value=null;recovery.value=null;name.value=latest.name;emit('restore',parseWorkspace(latest.content));status.value='已恢复本机草稿；未发送 AI'; expanded.value=true; }
async function openCloud(id: string) {if(!(await preserveBeforeSwitch()))return;busy.value=true;error.value='';try {const value=(await api.get<SavedWorkspace>(`/ai/saved-drafts/${id}`)).data;if(!identityValid())return;saved.value=value;active.value=null;recovery.value=null;name.value=value.name;emit('restore',parseWorkspace(value.content));status.value='已载入云端保存副本；继续前须重新确认';expanded.value=true;}catch(e){error.value=apiMessage(e);}finally{busy.value=false;} }
async function saveCloud(asNew=false) {
  busy.value=true;error.value='';await autosave.cancel();let localProtected=false,cloudAccepted=false;
  try { await localSave();localProtected=true; const id=asNew?crypto.randomUUID():active.value!.cloudId??crypto.randomUUID();const version=asNew?-1:active.value!.cloudVersion;
    const value=(await api.put<SavedWorkspace>(`/ai/saved-drafts/${id}`,{name:name.value.trim()||'我的规划草稿',version,contentJson:JSON.stringify(currentCopy())})).data;
    cloudAccepted=true;saved.value=value;cloudConflict.value=null;active.value={...active.value!,cloudId:value.id,cloudVersion:value.version};await store!.save(active.value);status.value='已保存云端';await reload();
  }catch(e){status.value=cloudAccepted?'云端已接收，本机关联保存失败，请重新读取草稿箱':localProtected?'云端保存失败，本机副本保留':'保存失败，编辑仍在当前页面，请勿关闭';error.value=apiMessage(e);const id=active.value?.cloudId;if((e as {response?:{status:number}})?.response?.status===409&&id)try{cloudConflict.value=(await api.get<SavedWorkspace>(`/ai/saved-drafts/${id}`)).data;}catch{/* Retain local copy. */}}
  finally{busy.value=false;}
}
async function resume() { if(!saved.value || !window.confirm('新建对话并重新核对草稿？这一步不调用 AI、不扣次数；后续发送或生成仍需你确认。已有对象调整将核对原对象的最新内容与执行历史。'))return;
  busy.value=true;try { if(JSON.stringify(currentCopy())!==JSON.stringify(saved.value.content))throw new Error('当前编辑与云端保存副本不同，请先保存云端再继续，避免遗漏修改。');const next=(await api.post<AiSession>(`/ai/saved-drafts/${saved.value.id}/resume`,{version:saved.value.version,consent:true})).data;emit('resumed',next,saved.value.content);status.value='已新建对话，尚未发送'; }catch(e){error.value=apiMessage(e);}finally{busy.value=false;}
}
async function deleteLocal(ids: string[]) { if(!window.confirm(`删除 ${ids.length} 份本机草稿？不删除云端草稿、对话、已生成计划或同步队列。`))return;await autosave.cancel();try{await store!.delete(ids);if(active.value&&ids.includes(active.value.id)){active.value=null;recovery.value=null;emit('protected',false);}await reloadLocal();status.value='所选本机草稿已删除；继续编辑才会新建保存';}catch(e){error.value=apiMessage(e);} }
async function clearCloud(selected: WorkspaceSummary[]) { if(!selected.length||!window.confirm(`删除 ${selected.length} 份云端保存草稿？处理中或版本变化的条目会跳过；不会删除对话、已生成计划或额度记录。`))return;
  await autosave.cancel();busy.value=true;try{const result=(await api.post<{deleted:string[];skipped:string[]}>('/ai/saved-drafts/clear',{selected:selected.map(v=>({id:v.id,version:v.version}))})).data;skippedNames.value=result.skipped.map(id=>selected.find(v=>v.id===id)?.name??'版本变化的草稿');if(saved.value&&result.deleted.includes(saved.value.id))saved.value=null;if(active.value?.cloudId&&result.deleted.includes(active.value.cloudId)){active.value={...active.value,cloudId:null,cloudVersion:-1};await store!.save(active.value);}status.value=`已删除 ${result.deleted.length} 份；跳过 ${result.skipped.length} 份（处理或版本变化）`;await reload();}catch(e){error.value=apiMessage(e);}finally{busy.value=false;}
}
async function exportCurrent(){try{const saved=await exportWorkspace(name.value,currentCopy());status.value=saved?'已交给下载或系统分享，请确认保存位置':'已取消保存，未写入文件。';}catch(e){error.value=apiMessage(e);}}
async function importSaved(event:Event){const input=event.target as HTMLInputElement;const file=input.files?.[0];if(!file)return;try{if(file.size>300000)throw new Error('草稿文件过大，请使用本功能导出的 JSON。');const data=JSON.parse(await file.text());if(data.format!=='plan-cabin-ai-draft'||data.schemaVersion!==1)throw new Error('不是计划舱规划草稿导出文件。');const content=parseWorkspace(data.content);if(!window.confirm('载入这份私人规划草稿到编辑区？不会自动发送、生成或覆盖现有云端草稿。')||!(await preserveBeforeSwitch()))return;active.value=null;saved.value=null;recovery.value=null;name.value=typeof data.name==='string'?data.name.slice(0,120):'导入的规划草稿';emit('restore',content);status.value='已导入独立副本，未调用 AI';}catch(e){error.value=apiMessage(e);}finally{input.value='';}}
onMounted(async()=>{owner=currentQueueOwner()??'';scope=serverScope();if(!owner)return;store=new WorkspaceStore(scope,owner);try{await reload();recovery.value=locals.value.find(v=>v.context===props.context)??null;if(recovery.value)status.value='发现本机草稿，可恢复后核对';}catch(e){error.value=apiMessage(e);status.value='本机存储不可用';}});
onBeforeUnmount(()=>{disposed=true;void autosave.cancel();});
defineExpose({saveNow});
</script>
<template>
  <section class="ai-shelf" aria-label="规划草稿箱">
    <div class="shelf-bar"><button class="text-btn" type="button" :aria-expanded="expanded" @click="expanded=!expanded">规划草稿箱 · 本机 {{locals.length}} / 云端 {{cloud.length}}</button><span role="status">{{status}}</span><button class="btn" type="button" :disabled="busy||!hasContent" @click="saveNow">保存到本机</button><button class="btn" type="button" :disabled="busy||!hasContent||!ready" @click="saveCloud()">保存到云端草稿箱</button></div>
    <p v-if="error" class="error-text" role="alert">{{error}}</p>
    <p v-if="skippedNames.length" class="helper-text" role="status">未删除的草稿：{{skippedNames.join('、')}}。请等待请求结束或刷新版本后再选择；未取消任何 AI 请求。</p>
    <div v-if="recovery" class="shelf-recovery"><p>发现此工作区上次的本机内容。恢复只载入编辑区，不发送请求、不恢复生成确认；云端版本不同会保留两份供比较。</p><button class="btn" @click="restoreLocal(recovery)">恢复本机草稿</button><button class="btn" @click="recovery=null">暂不恢复</button></div>
    <div v-if="expanded" class="shelf-body"><label>保存名称<input v-model="name" maxlength="120"/></label><p class="helper-text">本机和云端分别最多 30 份，每份 240 KiB；云端保存草稿独立保留至你删除，不随 30 天对话到期消失。仅保存编辑区，不含对话记录、账号凭据或同步队列。导出文件含你的私人规划，请妥善保管。</p>
      <div class="button-row"><button class="btn" :disabled="!hasContent" @click="exportCurrent">导出当前草稿 JSON</button><label class="btn">导入已导出草稿<input type="file" accept="application/json,.json" :disabled="busy||pending" @change="importSaved"/></label><button v-if="saved" class="btn" :disabled="busy||pending||saved.processing" @click="resume">新建对话继续此草稿</button><button class="text-btn danger-text" :disabled="!locals.length||pending" @click="deleteLocal(locals.map(v=>v.id))">清空本机草稿箱</button><button class="text-btn danger-text" :disabled="!cloud.length||busy" @click="clearCloud(cloud)">清空云端保存草稿</button></div>
      <div v-if="cloudConflict" class="shelf-conflict"><h3>云端与本机版本不同</h3><p>本机编辑未被覆盖。云端版本 {{cloudConflict.version}}：{{cloudConflict.content.draft?.title||cloudConflict.name}}；本机：{{content.draft?.title||name}}。</p><details><summary>查看两份内容</summary><h4>本机</h4><pre>{{JSON.stringify(content,null,2)}}</pre><h4>云端</h4><pre>{{JSON.stringify(cloudConflict.content,null,2)}}</pre></details><button class="btn" @click="saveCloud(true)">将本机内容另存新草稿</button></div>
      <div class="shelf-columns"><section><h3>本机草稿</h3><article v-for="item in locals" :key="item.id"><div><strong>{{item.name}}</strong><small>{{new Date(item.updatedAt).toLocaleString('zh-CN')}}</small></div><button class="btn" :disabled="busy" @click="restoreLocal(item)">恢复</button><button class="text-btn danger-text" :disabled="pending" @click="deleteLocal([item.id])">删除本机副本</button></article><p v-if="!locals.length" class="helper-text">此账号在当前服务器暂无本机草稿。</p></section><section><h3>云端保存草稿</h3><article v-for="item in cloud" :key="item.id"><div><strong>{{item.name}}</strong><small>{{item.processing?'原请求处理中':item.adjustment?'已有对象调整草稿':'新建规划草稿'}}</small></div><button class="btn" :disabled="busy" @click="openCloud(item.id)">查看／继续</button><button class="text-btn danger-text" :disabled="busy||item.processing" @click="clearCloud([item])">删除云端副本</button></article><p v-if="!cloud.length" class="helper-text">尚未独立保存云端草稿，或当前无法连接服务器。</p></section></div>
    </div>
  </section>
</template>
<style scoped>
.ai-shelf{border:1px solid var(--line);background:#fff;margin:16px 0}.shelf-bar{padding:12px 16px;display:flex;align-items:center;gap:12px;flex-wrap:wrap}.shelf-bar>span{font-size:.8rem;margin-right:auto}.shelf-body,.shelf-recovery{border-top:1px solid var(--line);padding:16px}.shelf-body>label{display:grid;gap:8px;max-width:420px;font-size:.8rem;font-weight:700}.shelf-columns{display:grid;grid-template-columns:1fr 1fr;gap:24px}.shelf-columns section{min-width:0}.shelf-columns article{border-top:1px solid var(--line);display:flex;gap:10px;align-items:center;flex-wrap:wrap;padding:14px 0}.shelf-columns article>div{flex:1;min-width:120px;overflow-wrap:anywhere}.shelf-columns small{display:block;color:var(--muted);font-size:.75rem;margin-top:6px}.shelf-columns h3,.shelf-conflict h3{font-size:1rem}.shelf-conflict{border:1px solid var(--blue);padding:16px;margin-top:16px}.shelf-conflict pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:240px;overflow:auto;font-size:.75rem}.shelf-body .button-row{flex-wrap:wrap;gap:8px}.ai-shelf>p{padding:0 16px}.shelf-recovery p{font-size:.85rem;line-height:1.7}@media(max-width:900px){.shelf-columns{grid-template-columns:1fr}.shelf-bar>.btn{flex:1;font-size:.8rem}.shelf-bar>span{flex-basis:100%}.shelf-body .button-row>.btn{flex-basis:100%}}
</style>
