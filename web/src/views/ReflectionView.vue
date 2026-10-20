<script setup lang="ts">
import {computed,onMounted,onUnmounted,reactive,ref,watch} from 'vue';
import {onBeforeRouteLeave,onBeforeRouteUpdate,useRoute} from 'vue-router';
import {api,apiMessage,currentQueueOwner,serverScope} from '@/api/client';
import {requiresConnection} from '@/connectionState';
import {cacheResponse} from '@/offlineCache';
import {readPrivateDraft,writePrivateDraft,deletePrivateDraft} from '@/privateDrafts';
import {localDate} from '@/todos';
import {recordedTime} from '@/dailyExperience';
import {aiPending,aiState} from '@/aiPlanning';
import type {AiSummary} from '@/aiPlanning';
import {aiFailureGuidance} from '@/aiExperience';
import {listOperations} from '@/offlineQueue';
import type {PageResponse} from '@/types';
import {emptyReflection,reflectionStart,reflectionDraft,reflectionDefaults,reflectionItemPath,energyLabels,burdenLabels,type ReviewKind,type ReflectionNotes,type ReflectionFacts,type ReflectionItem,type ReflectionPreferences,type ReflectionDraft,type ReflectionPreview,type Trend,type AnalysisSession,type AnalysisSummary} from '@/reflection';
const route=useRoute(),owner=currentQueueOwner()??'',scope=serverScope();let active=true,sequence=0;const inContext=()=>active&&owner===currentQueueOwner()&&scope===serverScope();
const kind=ref<ReviewKind>(route.query.kind==='WEEK'?'WEEK':'DAY'),selected=ref(typeof route.query.date==='string'?route.query.date:localDate()),start=ref('');
const selectedKind=ref<ReviewKind>(kind.value);
try{start.value=reflectionStart(kind.value,selected.value);}catch{selected.value=localDate();start.value=reflectionStart(kind.value,selected.value);}
const draft=reactive(emptyReflection()),baseVersion=ref<number|null>(null),baseline=ref(''),localClean=ref(''),current=ref<ReflectionNotes|null>(null);
const facts=ref<ReflectionFacts|null>(null),items=ref<PageResponse<ReflectionItem>|null>(null),itemMode=ref<'PLAN'|'TODO'|'ROUTE'>('PLAN'),bucket=ref('ARRANGED'),page=ref(0);
const prefs=reactive(reflectionDefaults()),trend=ref<Trend|null>(null),preview=ref<ReflectionPreview|null>(null),includeNotes=ref(true),consent=ref(false),session=ref<AnalysisSession|null>(null),analyses=ref<AnalysisSummary[]>([]),message=ref(''),operation=ref<string|null>(null);
const targets=ref<PageResponse<{id:string;name:string}>|null>(null),targetPage=ref(0),adjustMode=ref<'PLAN'|'ROUTE'|'TODO'>('PLAN'),target=ref(''),includeTarget=ref(false);
const loading=ref(false),saving=ref(false),busy=ref(false),error=ref(''),notice=ref(''),localError=ref('');
const requestVersion=ref<number|null>(null),analysisLocalClean=ref('');
const recentAdjustments=ref<AiSummary[]>([]);
const dirty=computed(()=>JSON.stringify(draft)!==baseline.value),needsCompare=computed(()=>current.value!==null&&baseVersion.value!==current.value.version),pending=computed(()=>aiPending(session.value)),key=()=>`reflection:${kind.value}:${start.value}`;
const content=(r:Partial<ReflectionNotes>)=>({...emptyReflection(),wins:r.wins??'',blockers:r.blockers??'',nextFocus:r.nextFocus??'',energy:r.energy??'UNSPECIFIED',burden:r.burden??'UNSPECIFIED',reviewed:r.reviewed??false,openToIncrease:r.openToIncrease??false});
const notesPath=()=>kind.value==='DAY'?`/reflection/days/${start.value}`:`/weekly/reviews/${start.value}`;
async function load(){const request=++sequence;loading.value=true;error.value='';preview.value=null;consent.value=false;try{
 let saved:ReflectionDraft|null=null;try{saved=reflectionDraft(await readPrivateDraft(scope,owner,key()),kind.value,start.value);localError.value='';}catch(e){localError.value=apiMessage(e);}
 const results=await Promise.allSettled([api.get<ReflectionFacts>('/reflection/facts',{params:{kind:kind.value,start:start.value}}),api.get<ReflectionNotes>(notesPath()),api.get<ReflectionPreferences>('/reflection/preferences')]);
 if(request!==sequence||!inContext())return;
 const keep=dirty.value;
 if(results[0].status==='fulfilled')facts.value=results[0].value.data;
 if(results[1].status==='fulfilled'){current.value=results[1].value.data;if(!keep){Object.assign(draft,content(current.value));baseVersion.value=current.value.version;baseline.value=JSON.stringify(draft);}}
 if(results[2].status==='fulfilled')Object.assign(prefs,results[2].value.data);
 if(!keep){localClean.value='';if(saved){Object.assign(draft,saved.content);baseVersion.value=saved.baseVersion;localClean.value=JSON.stringify(draft);notice.value='本机草稿已恢复，尚未提交云端。';}}else notice.value='云端版本已读取，当前编辑仍保留，请比较后再决定。';
 for(const result of results)if(result.status==='rejected')error.value=apiMessage(result.reason);
 if(!requiresConnection()){await loadItems();if(kind.value==='WEEK')trend.value=(await api.get<Trend>('/reflection/trend',{params:{start:start.value}})).data;}
 }catch(e){if(inContext())error.value=apiMessage(e);}finally{if(request===sequence)loading.value=false;}}
function resetAnalysisInput(){operation.value=null;requestVersion.value=null;message.value='';analysisLocalClean.value='';}
function mayLeave(){return !saving.value&&!busy.value&&((!dirty.value||JSON.stringify(draft)===localClean.value)&&(!message.value||message.value===analysisLocalClean.value)||confirm('还有未保存到本机或云端的复盘或讨论文字，确定离开？'));}
async function choose(){if(!mayLeave()){selectedKind.value=kind.value;selected.value=start.value;return;}try{const next=reflectionStart(selectedKind.value,selected.value);kind.value=selectedKind.value;start.value=next;page.value=0;current.value=null;facts.value=null;items.value=null;trend.value=null;session.value=null;resetAnalysisInput();Object.assign(draft,emptyReflection());baseline.value=JSON.stringify(draft);await load();}catch(e){error.value=apiMessage(e);}}
async function loadItems(){try{const result=await api.get<PageResponse<ReflectionItem>>('/reflection/items',{params:{kind:kind.value,start:start.value,mode:itemMode.value,bucket:itemMode.value==='ROUTE'?'ACTUAL':bucket.value,page:page.value,size:20}});if(inContext())items.value=result.data;}catch(e){error.value=apiMessage(e);}}
async function save(local=false){if(!inContext()||saving.value||localError.value||!local&&(needsCompare.value||requiresConnection()))return;saving.value=true;error.value='';try{
 if(local){const value:ReflectionDraft={schemaVersion:1,kind:kind.value,start:start.value,savedAt:new Date().toISOString(),baseVersion:baseVersion.value,content:{...draft}};await writePrivateDraft(scope,owner,key(),value);localClean.value=JSON.stringify(draft);notice.value='已存本机，关闭重开可恢复；不会自动调用AI或提交队列。';}
 else{const path=notesPath(),r=(await api.put<ReflectionNotes>(path,{...draft,version:baseVersion.value})).data;if(!inContext())return;current.value=r;baseVersion.value=r.version;Object.assign(draft,content(r));baseline.value=JSON.stringify(draft);await cacheResponse(scope,owner,path,undefined,r);await deletePrivateDraft(scope,owner,key());localClean.value='';notice.value='复盘已保存，原任务和计时记录没有改变。';preview.value=null;consent.value=false;}
 }catch(e){error.value=apiMessage(e);}finally{saving.value=false;}}
function adopt(){if(current.value&&confirm('已比较当前草稿和下方云端文字？仅采用最新版本号，不自动合并；保存仍需再次确认。')){baseVersion.value=current.value.version;notice.value='已采用最新版本号，请核对草稿后保存。';}}
async function savePrefs(){busy.value=true;try{Object.assign(prefs,(await api.put('/reflection/preferences',{...prefs})).data);notice.value='复盘引导设置已保存；系统通知仍须在本设备单独开启。';}catch(e){error.value=apiMessage(e);}finally{busy.value=false;}}
const related=()=>includeTarget.value?{relatedMode:adjustMode.value,relatedId:target.value}:{};
async function prepare(){if(dirty.value&&includeNotes.value){error.value='请先保存要交给AI的复盘文字，或取消包含文字。仅保存到本机不会发送。';return;}if(includeTarget.value&&!target.value){error.value='请先选择一份本人规划，或取消附带规划。';return;}busy.value=true;error.value='';try{preview.value=(await api.post<ReflectionPreview>('/reflection/analysis-preview',{kind:kind.value,start:start.value,includeNotes:includeNotes.value,...related()})).data;consent.value=false;}catch(e){error.value=apiMessage(e);}finally{busy.value=false;}}
async function createAnalysis(){if(!preview.value||!consent.value||requiresConnection()||!mayLeave())return;busy.value=true;error.value='';try{const created=(await api.post<AnalysisSession>('/reflection/analyses',{kind:preview.value.kind,start:preview.value.start,includeNotes:includeNotes.value,fingerprint:preview.value.fingerprint,consent:true,...related()})).data;session.value=created;resetAnalysisInput();preview.value=null;consent.value=false;message.value=kind.value==='DAY'?'请基于已同意的事实和文字，帮我做简短日复盘。只分析，不调整计划。':'请先总结本周事实和阻塞，不要直接调整或加量。';await history();notice.value='分析会话已建立，尚未调用AI。点击发送才使用每日次数。';}catch(e){error.value=apiMessage(e);}finally{busy.value=false;}}
async function send(){if(!session.value||pending.value||!message.value.trim()||requiresConnection())return;busy.value=true;try{operation.value??=crypto.randomUUID();requestVersion.value??=session.value.version;await saveAnalysisLocal();await api.post(`/ai/sessions/${session.value.id}/jobs`,{version:requestVersion.value,message:message.value,operationKey:operation.value});operation.value=null;requestVersion.value=null;message.value='';analysisLocalClean.value='';await deletePrivateDraft(scope,owner,'reflection-analysis:'+session.value.id);await refreshAnalysis();}catch(e){error.value=apiMessage(e)+'；若结果不确定，请先刷新核对，不要换请求编号重复发送。';}finally{busy.value=false;}}
async function saveAnalysisLocal(){if(!session.value||!inContext())return;await writePrivateDraft(scope,owner,'reflection-analysis:'+session.value.id,{schemaVersion:1,message:message.value,operationKey:operation.value,version:requestVersion.value});analysisLocalClean.value=message.value;notice.value='讨论输入已存本机，不会自动发送或调用AI。';}
async function refreshAnalysis(){if(!session.value)return;const id=session.value.id;try{const r=(await api.get<AnalysisSession>(`/ai/sessions/${id}`)).data;if(inContext()&&session.value?.id===id)session.value=r;}catch(e){error.value=apiMessage(e);}}
async function history(){try{analyses.value=(await api.get<AnalysisSummary[]>('/reflection/analyses')).data;recentAdjustments.value=(await api.get<AiSummary[]>('/ai/sessions')).data.filter(s=>s.adjustment&&s.applied);}catch(e){error.value=apiMessage(e);}}
async function openAnalysis(id:string){if(busy.value||!mayLeave())return;try{const value=(await api.get<AnalysisSession>(`/ai/sessions/${id}`)).data;if(!value.review)throw Error('这不是复盘分析会话。');session.value=value;operation.value=null;requestVersion.value=null;message.value='';analysisLocalClean.value='';const local=await readPrivateDraft(scope,owner,'reflection-analysis:'+id) as {schemaVersion:number;message:string;operationKey:string|null;version:number|null}|null;if(local){if(local.schemaVersion!==1||typeof local.message!=='string'||local.message.length>8000||!(local.operationKey===null||typeof local.operationKey==='string'&&/^[a-zA-Z0-9._:-]{8,100}$/.test(local.operationKey))||!(local.version===null||Number.isSafeInteger(local.version)&&local.version>=0))throw Error('讨论本机草稿格式无效，未发送任何内容。');message.value=local.message;operation.value=local.operationKey;requestVersion.value=local.version;analysisLocalClean.value=local.message;}}catch(e){error.value=apiMessage(e);}}
async function deleteAnalysis(){if(!session.value||pending.value||!confirm('删除这份AI分析会话？不会删除你的日周复盘和计划；历史数据库备份不因此清除。'))return;try{await api.delete(`/ai/sessions/${session.value.id}`,{params:{version:session.value.version}});session.value=null;await history();}catch(e){error.value=apiMessage(e);}}
async function decision(value:'OPENED'|'DECLINED'|'LATER'){try{trend.value=(await api.post<Trend>(`/reflection/trend/${start.value}/decision`,{decision:value})).data;notice.value='已记录选择，主动趋势提示冷却14天；手动分析仍可用。';}catch(e){error.value=apiMessage(e);}}
async function cleanQueue(){if((await listOperations(owner)).length)throw Error('请先处理同步队列和冲突；不会自动清理队列。');}
async function loadTargets(){try{targets.value=(await api.get<PageResponse<{id:string;name:string}>>('/reflection/targets',{params:{mode:adjustMode.value,page:targetPage.value}})).data;}catch(e){error.value=apiMessage(e);}}
const adjustmentLink=computed(()=>({path:'/ai',query:{mode:adjustMode.value,target:target.value}}));
async function checkTarget(event:MouseEvent){try{await cleanQueue();if(!target.value)throw Error('请填入你本人要调整的对象编号。');}catch(e){event.preventDefault();error.value=apiMessage(e);}}
watch([includeNotes,includeTarget,target,adjustMode,()=>JSON.stringify(draft)],()=>{preview.value=null;consent.value=false;});
watch(()=>route.fullPath,async()=>{if(route.path==='/reflection'){selectedKind.value=route.query.kind==='WEEK'?'WEEK':'DAY';selected.value=typeof route.query.date==='string'?route.query.date:localDate();await choose();}});
function beforeUnload(e:BeforeUnloadEvent){if(dirty.value&&JSON.stringify(draft)!==localClean.value||message.value&&message.value!==analysisLocalClean.value||saving.value||busy.value){e.preventDefault();e.returnValue='';}}
let timer:ReturnType<typeof setInterval>|undefined;
onBeforeRouteLeave(mayLeave);onBeforeRouteUpdate(mayLeave);
onMounted(()=>{baseline.value=JSON.stringify(draft);void load();if(!requiresConnection())void history();window.addEventListener('beforeunload',beforeUnload);timer=setInterval(()=>{if(pending.value&&!busy.value&&!requiresConnection())void refreshAnalysis();},2000);});
onUnmounted(()=>{active=false;sequence++;clearInterval(timer);window.removeEventListener('beforeunload',beforeUnload);});
</script>
<template>
<div class="page reflection-page">
 <header class="page-head">
<div>
<h1>日周复盘</h1>
<p>先看发生了什么，再决定下一步。不用每日完成率给自己打分。</p>
</div>
<router-link class="btn" to="/reviews">原周期计划日复盘</router-link>
</header>
 <section class="review-select">
<label>复盘范围<select v-model="selectedKind">
<option value="DAY">日复盘</option>
<option value="WEEK">周复盘</option>
</select>
</label>
<label>选择日期<input v-model="selected" type="date"/>
</label>
<button class="btn primary" :disabled="loading||saving||busy" @click="choose">查看所选范围</button>
<router-link class="btn" to="/weekly-review">原周复盘入口</router-link>
</section>
 <p v-if="error||localError" class="error-text" role="alert">{{error||localError}}</p>
<p v-if="notice" class="success-text" role="status">{{notice}}</p>
<p v-if="loading" role="status">正在读取已同步复盘…</p>
 <p v-if="requiresConnection()" class="helper-text">当前为离线快照。本机草稿可保存重开；保存云端与AI分析需要联网，不自动加入任务队列。</p>
 <section v-if="facts" class="section">
<h2>{{facts.start}}<template v-if="kind==='WEEK'"> 至 {{facts.end}}</template>
</h2>
<div class="reflection-metrics">
<article>
<h3>周期计划</h3>
<strong>{{facts.plan.actuallyCompleted}}</strong>
<p>按实际完成时间归属</p>
<small>安排 {{facts.plan.arranged}} · 当前完成 {{facts.plan.arrangedCompleted}} · 当前未完成 {{facts.plan.open}} · 跳过 {{facts.plan.skipped}}</small>
</article>
<article>
<h3>今日待办</h3>
<strong>{{facts.todo.actuallyCompleted}}</strong>
<p>按实际完成时间归属</p>
<small>安排 {{facts.todo.arranged}} · 当前完成 {{facts.todo.arrangedCompleted}} · 当前未完成 {{facts.todo.open}}</small>
</article>
<article>
<h3>目标路线</h3>
<strong>{{facts.routeCompleted}}</strong>
<p>实际完成的步骤</p>
<small>不设每日配额，不计作未完成或逾期</small>
</article>
</div>
 <p>已结算周期计划投入：{{recordedTime(facts.recordedPlanSeconds)}}。流水按开始时间归属，手工修正按记录时间；跨日不按分钟拆分，不包含尚未结算计时，不估算路线或待办实际用时。</p>
<p class="helper-text">北京时间统计，仅含已同步数据；当前记录可因后续修改变化，并非历史冻结快照。安排日期与完成日期口径不同，不能直接相除。保留的归档任务计入事实，删除的事项不列入完成清单。</p>
<p v-if="facts.plan.completedWithoutTime||facts.todo.completedWithoutTime" class="helper-text">旧记录中周期任务 {{facts.plan.completedWithoutTime}} 项、待办 {{facts.todo.completedWithoutTime}} 项没有完成时间，无法归属具体日期；这里是全部旧记录的未知数量，不计入本期实际完成。</p>
<p class="helper-text">汇总于 {{new Date(facts.calculatedAt).toLocaleString('zh-CN')}}。</p>
 <div class="item-filters">
<label>事项类型<select v-model="itemMode" @change="page=0;loadItems()">
<option value="PLAN">周期计划</option>
<option value="TODO">今日待办</option>
<option value="ROUTE">目标路线</option>
</select>
</label>
<label v-if="itemMode!=='ROUTE'">列表口径<select v-model="bucket" @change="page=0;loadItems()">
<option value="ARRANGED">本期安排，显示当前状态</option>
<option value="OPEN">本期安排且当前未完成</option>
<option value="ACTUAL">本期实际完成</option>
</select>
</label>
</div>
<p v-if="items&&!items.content.length">所选口径暂无事项。</p>
<router-link v-for="item in items?.content" :key="item.id" :to="reflectionItemPath(item)" class="reflection-item">
<span>{{item.title}}</span>
<small>{{item.completedAt?new Date(item.completedAt).toLocaleString('zh-CN'):item.status==='COMPLETED'?'完成时间未知':'当前未完成'}} · {{item.arrangedDate??'不按日期安排'}}</small>
</router-link>
<div v-if="items" class="button-row">
<span>共 {{items.totalElements}} 项</span>
<button class="btn" :disabled="page===0" @click="page--;loadItems()">上一页事项</button>
<button class="btn" :disabled="page>=items.totalPages-1" @click="page++;loadItems()">下一页事项</button>
</div>
 </section>
 <section class="section">
<h2>自己的记录</h2>
<form class="reflection-editor" @submit.prevent="save()">
<fieldset :disabled="loading||saving" class="review-fields">
<label>收获<textarea v-model="draft.wins" maxlength="4000" rows="4"/>
</label>
<label>阻塞与感受<textarea v-model="draft.blockers" maxlength="4000" rows="4"/>
</label>
<label>下一步<textarea v-model="draft.nextFocus" maxlength="4000" rows="4"/>
</label>
<div class="self-report">
<label>精力自述（可选）<select v-model="draft.energy">
<option v-for="(label,value) in energyLabels" :key="value" :value="value">{{label}}</option>
</select>
</label>
<label>负担自述（可选）<select v-model="draft.burden">
<option v-for="(label,value) in burdenLabels" :key="value" :value="value">{{label}}</option>
</select>
</label>
</div>
<label v-if="kind==='WEEK'" class="check">
<input v-model="draft.openToIncrease" type="checkbox"/>我愿意讨论少量增加投入（不代表同意修改计划）</label>
<label class="check">
<input v-model="draft.reviewed" type="checkbox"/>我已完成这次复盘，不再提醒本期复盘</label>
<p class="helper-text">自述不是健康评估，不根据完成率推断你的精力。保存文字不会改写任务或调用AI。</p>
<div v-if="needsCompare&&current" class="review-conflict">
<h3>云端版本不同，草稿仍保留</h3>
<p>云端收获：{{current.wins||'未填写'}}</p>
<p>云端阻塞：{{current.blockers||'未填写'}}</p>
<p>云端下一步：{{current.nextFocus||'未填写'}}</p>
<p>云端自述：{{energyLabels[current.energy]}}／{{burdenLabels[current.burden]}}</p>
<button class="btn" type="button" @click="adopt">已比较，采用最新版本号</button>
</div>
<div class="button-row">
<button class="btn" type="button" :disabled="saving||!!localError" @click="save(true)">保存本机草稿</button>
<button class="btn primary" :disabled="saving||requiresConnection()||needsCompare||!current||!!localError">保存云端复盘</button>
<button class="btn" type="button" :disabled="saving||requiresConnection()" @click="load">读取最新云端版本</button>
</div>
</fieldset>
</form>
</section>
 <section v-if="kind==='WEEK'&&trend" class="section">
<h2>节奏回顾</h2>
<p>{{trend.reason}}</p>
<p v-if="trend.nextEligibleAt" class="helper-text">主动提示最早恢复于 {{new Date(trend.nextEligibleAt).toLocaleString('zh-CN')}}。上次选择：{{({OPENED:'讨论',DECLINED:'不调整',LATER:'稍后再看'} as Record<string,string>)[trend.lastDecision??'']??'未记录'}}。</p>
<div v-if="trend.eligible" class="button-row">
<button class="btn" @click="decision('OPENED')">我想讨论节奏</button>
<button class="btn" @click="decision('DECLINED')">保持现状，不调整</button>
<button class="btn" @click="decision('LATER')">14天后再提醒</button>
</div>
<p class="helper-text">至少两周已完成且含实际事项的记录才主动提示，一次讨论或拒绝后冷却14天。手动分析不受该提示限制；完成率好也不自动加量。</p>
</section>
 <section class="section">
<h2>AI 复盘分析</h2>
<p>主动发送才扣每日次数。先预览范围；不扫描其他私人计划，不自动发送，不在离线队列中重放。</p>
 <details>
<summary>选择一份相关规划（可选）</summary>
<p>只读取你选择的对象；预览并同意后才发送其结构和说明，不包含执行备注。最多100项，超过时可只分析汇总。</p>
<div class="self-report">
<label>相关对象类型<select v-model="adjustMode" @change="target='';targetPage=0;loadTargets()">
<option value="PLAN">周期计划</option>
<option value="ROUTE">目标路线</option>
<option value="TODO">今日待办</option>
</select>
</label>
<label>本人相关规划<select v-model="target">
<option value="">不选择</option>
<option v-for="t in targets?.content" :key="t.id" :value="t.id">{{t.name}}</option>
</select>
</label>
</div>
<div class="button-row">
<button class="btn" :disabled="requiresConnection()||busy" @click="loadTargets">读取本人对象</button>
<button v-if="targets" class="btn" :disabled="targetPage===0" @click="targetPage--;loadTargets()">上一页对象</button>
<button v-if="targets" class="btn" :disabled="targetPage>=targets.totalPages-1" @click="targetPage++;loadTargets()">下一页对象</button>
</div>
<label class="check">
<input v-model="includeTarget" type="checkbox" :disabled="!target||busy"/>在本次分析中附带这份规划的必要结构与说明</label>
</details>
 <label class="check">
<input v-model="includeNotes" type="checkbox" :disabled="busy"/>包含本次已保存的复盘文字和精力／负担自述</label>
<button class="btn" :disabled="busy||requiresConnection()" @click="prepare">预览将发送的内容</button>
<div v-if="preview" class="analysis-preview">
<h3>只发送以下内容</h3>
<pre>{{preview.contentJson}}</pre>
<label class="check">
<input v-model="consent" type="checkbox"/>我已核对本次范围，同意发送给DeepSeek处理；不包含账号凭据</label>
<button class="btn primary" :disabled="busy||!consent||requiresConnection()" @click="createAnalysis">建立分析会话，尚不调用AI</button>
</div>
 <label>已保存的分析<select :value="session?.id??''" :disabled="busy" @change="openAnalysis(($event.target as HTMLSelectElement).value)">
<option value="" disabled>选择分析记录</option>
<option v-for="s in analyses" :key="s.id" :value="s.id">{{s.kind==='DAY'?'日':'周'}}复盘 · {{s.start}}</option>
</select>
</label>
 <div v-if="session" class="analysis-conversation">
<p>会话保留至 {{new Date(session.expiresAt).toLocaleString('zh-CN')}}；只分析，不生成或修改计划。使用的是建立会话时已同意的快照，事实变化后请重新预览。</p>
<article v-for="turn in session.turns" :key="turn.id">
<h3>{{aiState(turn.state)}}</h3>
<p class="review-prose">{{turn.message}}</p>
<p v-if="turn.reply" class="review-prose">{{turn.reply}}</p>
<p v-if="turn.error" class="helper-text">{{aiFailureGuidance(turn.state,turn.error)}}</p>
</article>
<label>与AI讨论<textarea v-model="message" rows="4" maxlength="8000" :disabled="pending||busy||!!operation"/>
</label>
<div class="button-row">
<button class="btn primary" :disabled="busy||pending||!message.trim()||requiresConnection()" @click="send">发送分析请求</button>
<button class="btn" :disabled="busy" @click="saveAnalysisLocal().catch(e=>error=apiMessage(e))">保存讨论本机草稿</button>
<button class="btn" :disabled="busy||requiresConnection()" @click="refreshAnalysis">刷新核对结果</button>
<button class="btn" :disabled="busy||pending||requiresConnection()" @click="deleteAnalysis">删除分析会话</button>
</div>
<p class="helper-text">超时或结果未知时不自动重试、不自动退回占用，先刷新核对；手动业务和已保存文字仍可用。</p>
</div>
 </section>
 <section v-if="kind==='WEEK'&&current?.reviewed" class="section">
<h2>从建议到调整</h2>
<details><summary>回顾最近已确认的调整</summary><p>只列当前会话保留期内的已确认调整；这是应用记录，不代表已获得效果，下一次周复盘可写下实际感受。</p><p v-if="!recentAdjustments.length">暂没有仍在保留期内的已确认调整。</p><router-link v-for="s in recentAdjustments" :key="s.id" class="reflection-item" :to="{path:'/ai',query:{session:s.id}}">{{new Date(s.createdAt).toLocaleString('zh-CN')}} · {{s.mode==='PLAN'?'周期计划':s.mode==='ROUTE'?'目标路线':'今日待办'}}调整</router-link></details>
<p>AI分析不会修改规划。如你主动决定调整，请在上方选择本人相关规划，再进入既有调整流程：重新同意载入、逐项比较、版本校验并确认。历史与已完成事项继续受保护。</p>
<router-link v-if="/^[a-f0-9-]{36}$/.test(target)" class="btn" :to="adjustmentLink" @click="checkTarget">预览所选对象的AI调整</router-link>
<p v-else>先在“选择一份相关规划”中读取并选择对象。</p>
<p class="helper-text">也可在计划、路线或待办详情中使用“AI调整”。目标路线仍是自由推进，不强加日期；这里不会删掉重建。</p>
</section>
 <details class="section">
<summary>复盘引导与时间设置</summary>
<form class="reflection-preferences" @submit.prevent="savePrefs">
<label class="check">
<input v-model="prefs.completionGuide" type="checkbox"/>当天适用事项首次全部完成时，显示一次轻量引导</label>
<label class="check">
<input v-model="prefs.dailyEnabled" type="checkbox"/>日复盘定时提醒</label>
<label>日复盘时间（北京时间）<input v-model="prefs.dailyTime" type="time" required/>
</label>
<label class="check">
<input v-model="prefs.weeklyEnabled" type="checkbox"/>周复盘定时提醒</label>
<label>周复盘提醒日<select v-model.number="prefs.weekDay">
<option v-for="(day,i) in ['一','二','三','四','五','六','日']" :key="i" :value="i+1">星期{{day}}</option>
</select>
</label>
<label>周复盘时间（北京时间）<input v-model="prefs.weeklyTime" type="time" required/>
</label>
<label class="check">
<input v-model="prefs.trendEnabled" type="checkbox"/>符合两周记录和冷却条件时显示节奏讨论入口</label>
<button class="btn" :disabled="busy||requiresConnection()">保存复盘引导设置</button>
<p>日、周、待办收尾开关独立；相同时刻合并成一次系统通知。无适用事项、已完成对应复盘或免打扰时不发。系统通知仍须在“系统提醒”页单独开启，关闭浏览器不承诺投递。</p>
</form>
<router-link class="btn" to="/reminders">查看设备系统提醒</router-link>
</details>
</div>
</template>
<style scoped>
.review-fields{border:0;padding:0;margin:0;min-width:0}
.review-select,.item-filters,.self-report{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin:24px 0}.review-select{grid-template-columns:1fr 1fr auto auto;align-items:end}.reflection-page label{display:grid;gap:8px;margin:12px 0}.reflection-page .check{display:flex;align-items:flex-start;gap:10px;line-height:1.8}.check input{width:18px;min-height:18px;flex:0 0 18px;margin-top:5px}.reflection-metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));border-left:1px solid var(--line);border-top:1px solid var(--line);margin:24px 0}.reflection-metrics article{padding:24px;border-bottom:1px solid var(--line);border-right:1px solid var(--line)}.reflection-metrics strong{font-size:48px;color:#002fa7;font-weight:500}.reflection-page p,.reflection-page small{line-height:1.8}.reflection-item{display:flex;justify-content:space-between;gap:20px;padding:16px 0;border-bottom:1px solid var(--line)}.reflection-item small{color:var(--muted)}.reflection-editor,.reflection-preferences{max-width:860px}.reflection-page textarea{width:100%;min-width:0}.button-row{flex-wrap:wrap}.review-conflict,.analysis-preview{padding:20px;background:#f7f7f8;border:1px solid var(--line);margin:20px 0}.analysis-preview pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:320px;overflow:auto}.analysis-conversation{max-width:860px;max-height:720px;overflow:auto;margin-top:24px}.analysis-conversation article{border-top:1px solid var(--line);padding:20px 0}.review-prose{white-space:pre-wrap;overflow-wrap:anywhere}.reflection-page details>summary{cursor:pointer;color:#002fa7}.reflection-page input,.reflection-page select{min-width:0;max-width:100%}@media(max-width:800px){.review-select{grid-template-columns:repeat(2,minmax(0,1fr))}.reflection-metrics{grid-template-columns:minmax(0,1fr)}.reflection-metrics article{padding:20px}.reflection-item{flex-direction:column;gap:8px}.self-report,.item-filters{grid-template-columns:minmax(0,1fr)}.reflection-page .button-row .btn{flex:1;min-width:140px}.analysis-conversation{max-height:70dvh}}
</style>
