<script setup lang="ts">
import {computed,onMounted,onUnmounted,reactive,ref,watch} from 'vue';
import {onBeforeRouteLeave,useRoute} from 'vue-router';
import {api,apiMessage,currentQueueOwner,serverScope} from '@/api/client';
import {requiresConnection} from '@/connectionState';
import {listOperations,onQueueChange} from '@/offlineQueue';
import {readPrivateDraft,writePrivateDraft,deletePrivateDraft} from '@/privateDrafts';
import {templatePending,type PersonalTemplate,type TemplatePreview,type TemplatePending,type TemplateResult} from '@/personalTemplates';
import type {PageResponse} from '@/types';
import RestDaysPicker from '@/components/RestDaysPicker.vue';
import TemplateStructurePreview from '@/components/TemplateStructurePreview.vue';
import {localDate} from '@/todos';
import {supportsPersonalTemplates} from '@/helpModel';
const supported=ref<boolean|null>(null);
const route=useRoute(),owner=currentQueueOwner()??'',scope=serverScope();let active=true,stopQueue:(()=>void)|undefined;
const inContext=()=>active&&owner===currentQueueOwner()&&scope===serverScope();
const filters=reactive({search:'',kind:'ALL',category:'',favorite:false}),page=ref(0),list=ref<PageResponse<PersonalTemplate>|null>(null),selected=ref<PersonalTemplate|null>(null);
const error=ref(''),notice=ref(''),busy=ref(false),ready=ref(false),queue=ref<number|null>(null),localError=ref('');
const form=reactive({source:'PLAN',sourceId:'',name:'',description:'',category:'',favorite:false,replace:false}),sources=ref<{id:string;name:string}[]>([]);
const sourcePage=ref(0),sourceSearch=ref(''),sourcePages=ref(0);
const edit=reactive({name:'',description:'',category:'',favorite:false}),use=reactive({name:'',startDate:localDate(),endDate:localDate(),restDays:[] as number[]});
const preview=ref<TemplatePreview|null>(null),previewMode=ref<'capture'|'use'>('capture'),confirmed=ref(false),pending=ref<TemplatePending|null>(null);
const result=ref<TemplateResult|null>(null),dirtyEdit=ref(false);
const locked=computed(()=>busy.value||!!pending.value),canWrite=computed(()=>ready.value&&!requiresConnection()&&queue.value===0&&!localError.value);
async function run(action:()=>Promise<void>){if(busy.value)return;busy.value=true;error.value='';try{await action();}catch(e){if(inContext())error.value=apiMessage(e);}finally{busy.value=false;}}
async function load(){const r=(await api.get<PageResponse<PersonalTemplate>>('/structure-templates',{params:{...filters,page:page.value}})).data;if(inContext())list.value=r;}
async function loadSources(){const source=form.source;form.sourceId='';sources.value=[];const rows=(await api.get(source==='PLAN'?'/plans':source==='ROUTE'?'/organize/routes':'/ai/saved-drafts',{params:source==='ROUTE'?{page:sourcePage.value,size:20,state:'ACTIVE',search:sourceSearch.value}:undefined})).data; if(inContext()&&form.source===source){sources.value=(source==='ROUTE'?rows.content:rows).map((x:{id:string;name:string})=>({id:x.id,name:x.name}));sourcePages.value=source==='ROUTE'?rows.totalPages:1;}}
const captureRequest=()=>({source:form.source,sourceId:form.sourceId,name:form.name,description:form.description,category:form.category,favorite:form.favorite,targetId:form.replace?selected.value?.id??null:null,version:form.replace?selected.value?.version??null:null});
const useRequest=()=>({name:use.name,startDate:selected.value?.kind==='PLAN'?use.startDate:null,endDate:selected.value?.kind==='PLAN'?use.endDate:null,restDaysMask:selected.value?.kind==='PLAN'?use.restDays.reduce((m,n)=>m|(1<<(n-1)),0):null,version:selected.value?.version});
async function choose(id:string){if(locked.value)return;if(dirtyEdit.value&&!confirm('模板资料尚未保存，放弃本次资料编辑并读取其他模板？'))return;await run(async()=>{const e=(await api.get<PersonalTemplate>('/structure-templates/'+id)).data;if(!inContext())return;selected.value=e;Object.assign(edit,{name:e.name,description:e.description,category:e.category,favorite:e.favorite});dirtyEdit.value=false;use.name=e.name+'（新副本）';use.startDate=localDate();const p=e.blueprint?.parent;const days=p&&e.kind==='PLAN'?Math.max(0,Math.round((Date.parse(String(p.end_date))-Date.parse(String(p.start_date)))/86400000)):0;use.endDate=new Date(Date.parse(use.startDate+'T12:00:00Z')+days*86400000).toISOString().slice(0,10);use.restDays=Array.from({length:7},(_,i)=>i+1).filter(day=>Number(p?.rest_days_mask??0)&(1<<(day-1)));preview.value=null;confirmed.value=false;});}
async function makePreview(mode:'capture'|'use'){if(!canWrite.value||locked.value)return;await run(async()=>{confirmed.value=false;result.value=null;preview.value=null;const value=(await api.post<TemplatePreview>(mode==='capture'?'/structure-templates/capture/preview':`/structure-templates/${selected.value!.id}/preview`,mode==='capture'?captureRequest():useRequest())).data;if(inContext()){previewMode.value=mode;preview.value=value;}});}
async function apply(){if(!canWrite.value||busy.value||!pending.value&&(!preview.value||!confirmed.value))return;await run(async()=>{
 if(!pending.value){const value:TemplatePending={schemaVersion:1,url:previewMode.value==='capture'?'/structure-templates/capture':`/structure-templates/${selected.value!.id}/apply`,body:{request:previewMode.value==='capture'?captureRequest():useRequest(),checksum:preview.value!.checksum,operationKey:crypto.randomUUID(),confirmed:true}};await writePrivateDraft(scope,owner,'template-pending',value);pending.value=value;}
 const value=(await api.post<TemplateResult>(pending.value.url,pending.value.body)).data;if(!inContext())return;result.value=value;await deletePrivateDraft(scope,owner,'template-pending');pending.value=null;preview.value=null;confirmed.value=false;notice.value='已完成；原对象、已有副本和同步队列未被修改。';await load();
 });}
async function clearPending(){if(!pending.value||busy.value||!confirm('请先核对模板列表或新对象，确认上次结果。放弃本机重试记录后，重新提交可能建立另一份副本；是否继续重新预览？'))return;await deletePrivateDraft(scope,owner,'template-pending');pending.value=null;preview.value=null;confirmed.value=false;}
async function saveEdit(){if(!selected.value||!canWrite.value||locked.value)return;await run(async()=>{const e=(await api.put<PersonalTemplate>('/structure-templates/'+selected.value!.id,{...edit,version:selected.value!.version})).data;if(!inContext())return;selected.value=e;dirtyEdit.value=false;notice.value='模板资料已保存，已建立的计划和路线不受影响。';await load();});}
async function remove(){if(!selected.value||!canWrite.value||locked.value||!confirm('仅删除这份个人模板？不会删除来源、已建立的计划／路线或历史备份。'))return;await run(async()=>{await api.delete('/structure-templates/'+selected.value!.id,{params:{version:selected.value!.version}});if(inContext()){selected.value=null;dirtyEdit.value=false;preview.value=null;await load();notice.value='模板已删除，来源与已有副本不变。';}});}
async function counts(){try{queue.value=(await listOperations(owner)).length;}catch{queue.value=null;}}
let saveChain=Promise.resolve(),pendingSaves=0;const inputSaving=ref(false);
watch(form,()=>{preview.value=null;confirmed.value=false;if(!ready.value||pending.value)return;const value={...form};pendingSaves++;inputSaving.value=true;saveChain=saveChain.then(async()=>{await writePrivateDraft(scope,owner,'template-input',value);}).catch(()=>{localError.value='本机输入保存失败，请保留当前内容；提交功能暂未开启。';}).finally(()=>{pendingSaves--;inputSaving.value=pendingSaves>0;});},{deep:true});
watch(use,()=>{preview.value=null;confirmed.value=false;},{deep:true});
function beforeUnload(e:BeforeUnloadEvent){if(busy.value||dirtyEdit.value||inputSaving.value){e.preventDefault();e.returnValue='';}}
onBeforeRouteLeave(async()=>{await saveChain;return !busy.value&&(!dirtyEdit.value||confirm('模板资料尚未保存，确定离开？来源保存表单仍有本机输入，预览确认不会自动恢复。'));});
onMounted(()=>void run(async()=>{try{const saved=await readPrivateDraft<Record<string,unknown>>(scope,owner,'template-input');if(saved&&['PLAN','ROUTE','AI_DRAFT'].includes(String(saved.source))&&['sourceId','name','description','category'].every(f=>typeof saved[f]==='string')&&typeof saved.favorite==='boolean'&&typeof saved.replace==='boolean')Object.assign(form,saved,{replace:false});pending.value=templatePending(await readPrivateDraft(scope,owner,'template-pending'));}catch(e){localError.value=apiMessage(e);}await counts();stopQueue=onQueueChange(()=>void counts());const health=(await api.get('/health',{timeout:5000})).data;if(!inContext())return;supported.value=supportsPersonalTemplates(health);if(!supported.value){error.value='当前服务器尚不支持整套模板，请联系管理员升级后重开此页。本机输入、原模板和同步队列均未更改。';return;}await load();const savedId=form.sourceId;await loadSources();form.sourceId=savedId;ready.value=true;window.addEventListener('beforeunload',beforeUnload);}).then(()=>{if(ready.value&&typeof route.query.selected==='string'&&/^[a-f0-9-]{36}$/.test(route.query.selected))void choose(route.query.selected);}));
onUnmounted(()=>{active=false;stopQueue?.();window.removeEventListener('beforeunload',beforeUnload);});
</script>
<template>
<div class="page template-page">
<header class="page-head">
<div>
<h1>个人整套模板库</h1>
<p>把一套有效的方法留下，下一次从完整结构开始。</p>
</div>
<router-link class="btn" to="/exports">备份与恢复</router-link>
</header>
<p v-if="error||localError" role="alert" class="error-text">{{error||localError}}</p>
<p v-if="notice" role="status" class="success-text">{{notice}}</p>
<p v-if="requiresConnection()">模板管理需要联网；不会自动提交本机输入或调用AI。</p>
<p v-if="queue!==0" class="helper-text">{{queue===null?'同步队列状态未知，请先检查同步。':`本设备有 ${queue} 项本地操作，请先处理同步再保存或使用模板。`}}不会清空或代为提交队列。</p>
<section v-if="pending" class="section pending" aria-label="待核对的模板提交">
<h2>上次提交结果待核对</h2>
<p>原请求保存在本机，重开后不会自动提交。原样重试复用编号，不会重复创建。</p>
<button class="btn primary" :disabled="busy||!canWrite" @click="apply">原样重试确认结果</button>
<button class="btn" :disabled="busy" @click="clearPending">已核对结果，重新预览</button>
</section>
<p v-if="result">
<button v-if="result.path.startsWith('/templates')" class="btn" :disabled="locked" @click="choose(result.id)">查看保存的模板</button>
<router-link v-else :to="result.path">打开本次结果</router-link>
</p>
<template v-if="supported">
<div class="template-layout">
<section class="section library" aria-label="我的模板">
<h2>我的模板</h2>
<form @submit.prevent="page=0;run(load)">
<label>搜索模板<input v-model="filters.search" maxlength="120"/>
</label>
<div class="form-grid">
<label>模板模式<select v-model="filters.kind">
<option value="ALL">全部</option>
<option value="PLAN">周期计划</option>
<option value="ROUTE">目标路线</option>
</select>
</label>
<label>分类筛选<input v-model="filters.category" maxlength="60" placeholder="完整分类名"/>
</label>
</div>
<label class="check">
<input v-model="filters.favorite" type="checkbox"/>只看收藏</label>
<button class="btn" :disabled="busy">查询模板</button>
</form>
<p v-if="list&&!list.totalElements">还没有符合条件的模板。可以先从本人计划、路线或完整AI草稿保存。</p>
<article v-for="item in list?.content" :key="item.id" :class="{chosen:selected?.id===item.id}">
<div>
<strong>{{item.name}}</strong>
<p>{{item.kind==='PLAN'?'周期计划':'目标路线'}} · {{item.category||'未分类'}} · {{item.favorite?'已收藏':'未收藏'}} · 版本 {{item.version+1}}</p>
<p>{{item.counts.items}} 项 · {{item.counts.stages}} 个阶段</p>
</div>
<button class="btn" :disabled="locked" @click="choose(item.id)">查看模板</button>
</article>
<div v-if="list" class="button-row">
<span>共 {{list.totalElements}} 份 · 第 {{page+1}} 页</span>
<button class="btn" :disabled="busy||page===0" @click="page--;run(load)">上一页模板</button>
<button class="btn" :disabled="busy||page+1>=list.totalPages" @click="page++;run(load)">下一页模板</button>
</div>
</section>
<section class="section" aria-label="保存整套结构">
<h2>保存一套结构</h2>
<p>首版支持周期计划和目标路线。AI来源须先在工作台独立保存完整新建草稿；不读取整段对话，不支持临时待办或旧对象调整草稿。</p>
<form @submit.prevent="makePreview('capture')">
<fieldset :disabled="locked">
<label>结构来源<select v-model="form.source" @change="sourcePage=0;run(loadSources)">
<option value="PLAN">本人周期计划</option>
<option value="ROUTE">本人目标路线</option>
<option value="AI_DRAFT">本人已保存AI草稿</option>
</select>
</label>
<label>来源对象<select v-model="form.sourceId" required>
<option value="">请选择</option>
<option v-for="item in sources" :key="item.id" :value="item.id">{{item.name}}</option>
</select>
</label>
<div v-if="form.source==='ROUTE'" class="source-query"><label>搜索来源路线<input v-model="sourceSearch" maxlength="120"/></label><div class="button-row"><button class="btn" type="button" @click="sourcePage=0;run(loadSources)">查询来源路线</button><button class="btn" type="button" :disabled="sourcePage===0" @click="sourcePage--;run(loadSources)">上一页来源</button><button class="btn" type="button" :disabled="sourcePage+1>=sourcePages" @click="sourcePage++;run(loadSources)">下一页来源</button></div><p>第 {{sourcePage+1}} 页，服务器每页读取20条未归档路线。</p></div>
<label>模板名称<input v-model="form.name" maxlength="120" required/>
</label>
<label>模板说明<textarea v-model="form.description" maxlength="1000" rows="3"/>
</label>
<label>模板分类<input v-model="form.category" maxlength="60"/>
</label>
<label class="check">
<input v-model="form.favorite" type="checkbox"/>收藏这份模板</label>
<label v-if="selected" class="check">
<input v-model="form.replace" type="checkbox"/>用这份来源更新当前选中模板结构（不更新已有副本）</label>
</fieldset>
<button class="btn primary" :disabled="locked||!canWrite">预览保存结构</button>
</form>
</section>
</div>
<section v-if="selected" class="section selected" aria-label="所选模板">
<header>
<h2>{{selected.name}}</h2>
<p>模板版本 {{selected.version+1}} · {{selected.description}}。编辑资料或重新保存结构只影响这份模板，不改变已有对象。</p>
</header>
<form @submit.prevent="saveEdit">
<fieldset :disabled="locked" @input="dirtyEdit=true">
<div class="form-grid">
<label>修改模板名称<input v-model="edit.name" required maxlength="120"/>
</label>
<label>修改模板分类<input v-model="edit.category" maxlength="60"/>
</label>
</div>
<label>修改模板说明<textarea v-model="edit.description" maxlength="1000" rows="2"/>
</label>
<label class="check">
<input v-model="edit.favorite" type="checkbox"/>收藏</label>
</fieldset>
<div class="button-row">
<button class="btn" :disabled="locked||!canWrite">保存模板资料</button>
<button type="button" class="btn" :disabled="locked" @click="choose(selected.id)">重新读取模板</button>
<button type="button" class="btn danger" :disabled="locked||!canWrite" @click="remove">删除此模板</button>
</div>
</form>
<TemplateStructurePreview v-if="selected.blueprint" :blueprint="selected.blueprint"/>
<form class="use-template" @submit.prevent="makePreview('use')">
<h3>用模板建立新{{selected.kind==='PLAN'?'计划':'路线'}}</h3>
<fieldset :disabled="locked">
<label>新对象名称<input v-model="use.name" maxlength="120" required/>
</label>
<template v-if="selected.kind==='PLAN'">
<div class="form-grid">
<label>新计划开始日期<input v-model="use.startDate" type="date" required/>
</label>
<label>新计划结束日期<input v-model="use.endDate" type="date" required/>
</label>
</div>
<RestDaysPicker v-model="use.restDays"/>
</template>
</fieldset>
<button class="btn primary" :disabled="locked||!canWrite">预览建立新副本</button>
</form>
</section>
<section v-if="preview" class="section template-confirm" aria-label="模板提交预览">
<h2>{{previewMode==='capture'?'确认保存模板':'确认建立新副本'}}</h2>
<p v-if="previewMode==='capture'">模板名称：{{form.name}} · 分类：{{form.category||'未分类'}} · {{form.favorite?'收藏':'不收藏'}} · {{form.replace?'更新当前所选模板':'保存为新模板'}}</p>
<p>{{preview.counts.items}} 项 · {{preview.counts.stages}} 个阶段 · {{preview.counts.tags}} 个标签 · {{preview.counts.milestones}} 个里程碑</p>
<p v-if="previewMode==='use'&&preview.kind==='PLAN'">新周期：{{use.startDate}} 至 {{use.endDate}}。任务和里程碑按起点等距平移，不因休息日自动调整。</p>
<p v-for="warning in preview.warnings" :key="warning">{{warning}}</p>
<TemplateStructurePreview :blueprint="preview.blueprint"/>
<label class="check">
<input v-model="confirmed" type="checkbox" :disabled="locked"/>我已核对完整结构、保存范围及新对象设置，确认继续。</label>
<button class="btn primary" :disabled="locked||!canWrite||!confirmed" @click="apply">{{previewMode==='capture'?'确认保存模板':'确认建立新副本'}}</button>
</section>
</template>
</div>
</template>
<style scoped>
.template-page .section{padding:24px;margin-bottom:24px}.template-layout{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:24px;align-items:start}.template-page form,.template-page fieldset{display:grid;gap:16px}.template-page fieldset{border:0;padding:0;min-width:0}.template-page label{display:grid;gap:8px}.template-page .check{display:flex;align-items:center;gap:10px}.template-page input[type=checkbox]{width:20px;min-height:20px;height:20px;flex:0 0 20px;accent-color:#002FA7}.library article{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:20px 8px;border-bottom:1px solid var(--line)}.library article.chosen{border-left:4px solid #002FA7;padding-left:12px}.template-page p,.template-page h2,.template-page strong{overflow-wrap:anywhere}.template-page p{line-height:1.7}.template-page .button-row{flex-wrap:wrap;gap:12px}.template-page .form-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.selected .structure,.use-template{margin-top:24px;border-top:1px solid var(--line);padding-top:24px}.template-confirm,.pending{border:1px solid #002FA7;background:#F7F7F8}.template-page .template-confirm>.check{margin:24px 0}.template-page .btn{white-space:normal}
@media(max-width:700px){.template-layout{grid-template-columns:minmax(0,1fr);gap:0}.template-page .section{padding:16px}.template-page .form-grid{grid-template-columns:minmax(0,1fr)}.library article{align-items:stretch;flex-direction:column}.template-page input,.template-page select{max-width:100%;min-width:0}.template-page .button-row>.btn{flex:1 1 40%}}
</style>
