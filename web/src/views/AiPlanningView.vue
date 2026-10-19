<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import { api, apiMessage, currentQueueOwner } from '@/api/client';
import { listOperations, onQueueChange } from '@/offlineQueue';
import { aiModeNames, aiInitialMode, aiPending, aiState, aiAppliedPath, aiExternalPrompt, aiExternalAdjustmentPrompt, aiDifferences, type AiMode, type AiSession, type AiSummary, type AiDraft } from '@/aiPlanning';
import type { AiUsage } from '@/aiQuota';
import AiDraftEditor from '@/components/AiDraftEditor.vue';
import AiChangePreview from '@/components/AiChangePreview.vue';
import { pacingPrompts } from '@/aiPacing';
import {aiFailureGuidance,aiQuotaHint,aiSubmitUncertain} from '@/aiExperience';
import AiWorkspaceShelf from '@/components/AiWorkspaceShelf.vue';
import { modeExamples, type WorkspaceContent } from '@/aiWorkbench';
import { useAuthStore } from '@/stores/auth';
const route = useRoute(), router = useRouter();
const auth = useAuthStore();
const shelf = ref<InstanceType<typeof AiWorkspaceShelf>>();
const locallySaved = ref(false), detached = ref(false), retainedSource = ref<{id:string|null;version:number|null}>({id:null,version:null});
const mobilePanel = ref<'chat'|'draft'>('chat'), conversationFilter = ref('ALL');
const remoteSession = ref<AiSession | null>(null), chatScroll = ref<HTMLElement>();
const workspaceStyle = ref<Record<string,string>>({});
const keyboardOpen=ref(false);let viewportPeak=0;
const capability = ref({ available: false, message: '正在确认 AI 服务状态…', maxInputChars: 8000, adjustmentEnabled: false });
const quota = ref<AiUsage | null>(null), summaries = ref<AiSummary[]>([]), session = ref<AiSession | null>(null);
const mode = ref<AiMode | ''>(aiInitialMode(route.query.mode) ?? ''), consent = ref(false), message = ref(''), error = ref(''), notice = ref('');
const busy = ref(false), loading = ref(true), draft = ref<AiDraft | null>(null), dirty = ref(false), confirmed = ref(false), importJson = ref(''), uncertain = ref(false);
const pending = computed(() => aiPending(session.value));
const unsaved=computed(()=>(dirty.value||Boolean(message.value.trim())||Boolean(importJson.value.trim()))&&!locallySaved.value);
const workspaceContent = computed<WorkspaceContent>(()=>({schemaVersion:1,mode:mode.value||null,message:message.value,importJson:importJson.value,draft:draft.value,sourceSessionId:session.value?.id??retainedSource.value.id,sourceVersion:session.value?.version??retainedSource.value.version}));
const workspaceContext = computed(()=>session.value?.id??String(route.query.session??`new:${route.query.mode??''}:${route.query.target??''}`));
const filteredSummaries = computed(()=>summaries.value.filter(item=>conversationFilter.value==='ALL'||(conversationFilter.value==='APPLIED'?item.applied:conversationFilter.value==='PROCESSING'?item.processing:!item.applied&&!item.processing)));
const requestedTarget = computed(() => typeof route.query.target === 'string' && /^[a-f0-9-]{36}$/.test(route.query.target) ? route.query.target : null);
const isAdjustment = computed(() => !!session.value?.adjustment || !!requestedTarget.value);
const queued = ref(0);
const differences = computed(() => session.value?.adjustment && draft.value ? aiDifferences(session.value.adjustment.original, draft.value) : []);
const pacing = computed(() => session.value?.adjustment ? pacingPrompts(session.value.adjustment) : []);
function usePacing(text: string) { if (message.value.trim() && !window.confirm('用这个示例替换尚未发送的要求？')) return; message.value = text; }
const externalFormat = computed(() => session.value?.adjustment && draft.value ? aiExternalAdjustmentPrompt(session.value.adjustment, draft.value) : requestedTarget.value ? '请先勾选说明并载入所选内容，再复制保留原标识的调整格式。' : mode.value ? aiExternalPrompt(mode.value) : '请先选择模式。');
const editable = computed(() => !busy.value && !pending.value && !session.value?.applied);
const resultPath = computed(() => aiAppliedPath(session.value?.applied?.path));
let timer: ReturnType<typeof setTimeout> | undefined; let disposed = false; let generation = 0;let pollFailures=0;
async function checkQueue() { const owner = currentQueueOwner(); queued.value = owner ? (await listOperations(owner)).length : 0; }
async function assertCleanQueue() { await checkQueue(); if (queued.value) throw new Error('本机有待同步操作或冲突，请先到同步中心处理，再开始或应用 AI 调整。原队列不会清除。'); }
const stopQueueWatch = onQueueChange(() => { if (isAdjustment.value) void checkQueue().catch(() => { queued.value = -1; }); });
function accept(value: AiSession) { session.value = value; draft.value = value.draft ? structuredClone(value.draft) : null; dirty.value = false; confirmed.value = false; mode.value = value.mode ?? '';detached.value=false;retainedSource.value={id:value.id,version:value.version};remoteSession.value=null; }
function restoreWorkspace(value: WorkspaceContent) {
  const compatible=!!session.value&&value.sourceSessionId===session.value.id&&value.sourceVersion===session.value.version&&!session.value.applied&&!pending.value;
  remoteSession.value=session.value;retainedSource.value={id:value.sourceSessionId,version:value.sourceVersion};
  if(!compatible){session.value=null;detached.value=true;generation++;clearTimeout(timer);}
  mode.value=value.mode??'';message.value=value.message;importJson.value=value.importJson;draft.value=value.draft?structuredClone(value.draft):null;
  confirmed.value=false;consent.value=false;dirty.value=!!draft.value;locallySaved.value=true;
  notice.value=compatible?'已恢复本机内容，请核对并保存到当前对话；没有调用 AI。':'已载入独立草稿。请保存云端后通过“新建对话继续此草稿”重新核对；不会复活旧会话。';
}
async function resumedWorkspace(value: AiSession, content: WorkspaceContent){accept(value);message.value=content.message;importJson.value=content.importJson;consent.value=true;confirmed.value=false;await router.replace({path:'/ai',query:{session:value.id}});await loadSummary();notice.value='已创建新的对话；发送或生成仍需你明确操作。';}
async function changeMode(value:AiMode){
  if(isAdjustment.value||pending.value||busy.value||detached.value)return;
  if(!session.value){mode.value=value;return;}
  if(!draft.value){await choose(value);return;}
  if(!window.confirm('更换模式将开启新规划，原对话和草稿保留。新模式不会自动生成或扣次数，是否继续？'))return;
  await shelf.value?.saveNow();await fresh();mode.value=value;
}
function clearInput(){if(!window.confirm('只清空尚未发送的文字和外部 JSON 输入？不删除草稿、对话或已生成计划。'))return;message.value='';importJson.value='';}
function updateViewport(){const v=window.visualViewport;if(v){viewportPeak=Math.max(viewportPeak,v.height);keyboardOpen.value=viewportPeak-v.height>120;workspaceStyle.value={'--ai-viewport':`${v.height}px`};}}
async function loadSummary() { summaries.value = (await api.get<AiSummary[]>('/ai/sessions')).data; }
async function loadQuota() { quota.value = (await api.get<AiUsage>('/ai/quota')).data; }
function schedule() { clearTimeout(timer); if (!disposed && pending.value) timer = setTimeout(() => void refresh(false), Math.min(15000,1500*2**Math.min(pollFailures,4))); }
async function refresh(manual = true) {
  error.value = '';
  const id = session.value?.id, expected = generation;
  try { if (manual) capability.value=(await api.get('/ai/capabilities')).data; if (id) { const data = (await api.get<AiSession>(`/ai/sessions/${id}`)).data; if (expected !== generation || disposed) return;if(dirty.value){remoteSession.value=data;notice.value='已读取云端版本，本机修改未覆盖。可比较两份内容或另存独立草稿。';}else accept(data); } await Promise.all([loadQuota(), loadSummary()]); uncertain.value = false;pollFailures=0; }
  catch (reason) { pollFailures++;error.value = apiMessage(reason); }
  finally { schedule(); }
}
async function open(id: string) {
  if(shelf.value && !(await shelf.value.saveNow()) && !window.confirm('本机保存失败，切换可能丢失编辑，仍要继续？'))return;
  if (unsaved.value && !window.confirm('切换会放弃尚未保存的草稿修改或尚未发送的文字，是否继续？')) return;
  busy.value = true; error.value = ''; clearTimeout(timer); const expected = ++generation;
  try { const data = (await api.get<AiSession>(`/ai/sessions/${id}`)).data; if (expected !== generation || disposed) return; accept(data);message.value='';importJson.value='';consent.value = true; await router.replace({ path: '/ai', query: { session: id } }); }
  catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; schedule(); }
}
async function create() {
  if (!consent.value) throw new Error('请先阅读并勾选对话处理说明。');
  let value: AiSession;
  if (requestedTarget.value) {
    if (!mode.value || !capability.value.adjustmentEnabled) throw new Error('当前服务器尚不支持已有对象的 AI 调整，请等待服务器升级或继续手动编辑。');
    await assertCleanQueue();
    value = (await api.post<AiSession>('/ai/adjustments', { mode: mode.value, targetId: requestedTarget.value, consent: true })).data;
  } else value = (await api.post<AiSession>('/ai/sessions', { mode: mode.value || null, consent: true })).data;
  accept(value); await router.replace({ path: '/ai', query: { session: value.id } }); await loadSummary();
}
async function loadSelection() {
  busy.value = true; error.value = '';
  try { await create(); notice.value = '已载入所选内容。此处只修改建议，不会直接改变原对象。'; }
  catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; }
}
async function send() {
  if (busy.value || pending.value || uncertain.value || dirty.value || detached.value) return;
  busy.value = true; error.value = ''; notice.value = '';
  try { if (!session.value) await create(); await api.post(`/ai/sessions/${session.value!.id}/jobs`, { message: message.value, version: session.value!.version, operationKey: crypto.randomUUID() }); message.value = ''; await refresh(false); }
  catch (reason) { error.value = apiMessage(reason); uncertain.value = aiSubmitUncertain(reason); }
  finally { busy.value = false; schedule(); }
}
async function choose(value: AiMode) {
  busy.value = true; error.value = '';
  try { accept((await api.put<AiSession>(`/ai/sessions/${session.value!.id}/mode`, { mode: value, version: session.value!.version })).data); notice.value = `已选择${aiModeNames[value]}，请继续告诉 AI 你的要求。`; }
  catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; }
}
async function saveDraft() {
  if (!session.value || !draft.value) return;
  busy.value = true; error.value = '';
  try { accept((await api.put<AiSession>(`/ai/sessions/${session.value.id}/draft`, { version: session.value.version, draftJson: JSON.stringify(draft.value) })).data); notice.value = session.value.adjustment ? '调整草稿已保存，原对象尚未改变。' : '草稿已保存，还没有生成真实计划。'; }
  catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; }
}
async function apply() {
  if (!session.value || !confirmed.value || dirty.value || detached.value || remoteSession.value&&remoteSession.value.version!==session.value.version) return;
  busy.value = true; error.value = '';
  try { if (session.value.adjustment) await assertCleanQueue(); await api.post(`/ai/sessions/${session.value.id}/apply`, { version: session.value.version, confirmed: true }); await refresh(false); notice.value = session.value.adjustment ? '已应用修改，原执行记录和未修改事项保留。' : '已生成，可进入对应模式查看。'; }
  catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; }
}
async function importDraft() {
  if(detached.value)return;
  busy.value = true; error.value = '';
  try { const value = JSON.parse(importJson.value); if (!mode.value) throw new Error('请先选择导入草稿的模式。'); if (!session.value) await create(); accept((await api.put<AiSession>(`/ai/sessions/${session.value!.id}/draft`, { version: session.value!.version, draftJson: JSON.stringify(value) })).data); importJson.value = ''; notice.value = '草稿已导入，请核对后确认生成。'; }
  catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; }
}
async function remove() {
  if (!session.value || !window.confirm('删除此对话和草稿？已生成的计划不会删除，额度记录仍保留。')) return;
  busy.value = true;
  try { await api.delete(`/ai/sessions/${session.value.id}`, { params: { version: session.value.version } }); dirty.value = false; await fresh(); await loadSummary(); }
  catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; }
}
async function fresh() {
  await shelf.value?.saveNow();
  if (unsaved.value && !window.confirm('新建对话会放弃尚未保存的草稿修改或尚未发送的文字，是否继续？')) return;
  generation++; clearTimeout(timer); session.value = null; draft.value = null; dirty.value = false; confirmed.value = false; consent.value = false; uncertain.value = false; message.value = '';importJson.value='';detached.value=false;retainedSource.value={id:null,version:null};remoteSession.value=null;error.value = ''; notice.value = ''; mode.value = ''; await router.replace('/ai');
}
function changed() { dirty.value = true; confirmed.value = false; }
async function copyFormat() { if (!mode.value) return; try { await navigator.clipboard.writeText(externalFormat.value); notice.value = '格式说明已复制，可发给外部 AI。'; } catch { error.value = '无法自动复制，请在下方格式说明中手动选择并复制。'; } }
function beforeUnload(event:BeforeUnloadEvent){if(unsaved.value){event.preventDefault();event.returnValue='';}}
onBeforeRouteLeave(async () => {await shelf.value?.saveNow();return !unsaved.value || window.confirm('本机保存未成功，离开可能丢失输入，仍要离开吗？');});
watch(() => route.query.session, id => { if (typeof id === 'string' && id !== session.value?.id && !busy.value) void open(id); });
onMounted(async () => {
  window.addEventListener('beforeunload',beforeUnload);
  updateViewport();window.visualViewport?.addEventListener('resize',updateViewport);
  try { capability.value = (await api.get('/ai/capabilities')).data; await Promise.all([loadQuota(), loadSummary()]); if (typeof route.query.session === 'string') await open(route.query.session); if (isAdjustment.value) await checkQueue(); }
  catch (reason) { error.value = apiMessage(reason); } finally { loading.value = false; }
});
onUnmounted(() => { disposed = true; clearTimeout(timer); stopQueueWatch();window.removeEventListener('beforeunload',beforeUnload);window.visualViewport?.removeEventListener('resize',updateViewport); });
</script>
<template>
  <div class="page ai-page" :style="workspaceStyle" :class="{'ai-keyboard-open':keyboardOpen}">
    <header class="page-head"><div><h1>AI 规划助手</h1><p>{{ isAdjustment ? '讨论调整节奏，逐项查看前后差异。你确认以后，才修改所选对象。' : '先聊清目标，再预览草稿。你确认以后，才生成真实计划。' }}</p></div><div class="button-row"><button class="btn" :disabled="busy" @click="fresh">新建对话</button><button class="btn" :disabled="busy || loading" @click="refresh()">刷新对话</button></div></header>
    <p v-if="error" class="error-text" role="alert">{{ error }}</p><p v-if="notice" class="helper-text" role="status">{{ notice }}</p>
    <p v-if="!capability.available && !loading" class="ai-service-note">{{ capability.message }} <router-link to="/plans">手动周期计划</router-link> · <router-link to="/mainlines">手动目标路线</router-link> · <router-link to="/todos">手动待办</router-link></p>
    <div class="ai-toolbar"><label>对话分类<select v-model="conversationFilter"><option value="ALL">全部对话</option><option value="DRAFT">未完成规划</option><option value="APPLIED">已应用结果</option><option value="PROCESSING">处理中的任务</option></select></label><label>最近对话<select :value="session?.id ?? ''" :disabled="busy" @change="open(($event.target as HTMLSelectElement).value)"><option value="" disabled>选择已保存的对话</option><option v-for="item in filteredSummaries" :key="item.id" :value="item.id">{{ item.adjustment ? '调整 · ' : '' }}{{ item.mode ? aiModeNames[item.mode] : '模式待定' }} · {{ new Date(item.createdAt).toLocaleString('zh-CN') }}{{ item.applied ? ' · 已应用' : item.processing?' · 处理中':' · 未应用' }}</option></select></label><p v-if="quota">今日剩余 <strong>{{ quota.remaining }}</strong> / {{ quota.dailyLimit }} 次 · 已用 {{ quota.used }} · 占用 {{ quota.pending }}<span v-if="quota.paused"> · AI 已暂停</span></p><button v-if="session" class="text-btn danger-text" :disabled="busy || pending" @click="remove">删除对话</button></div>
    <AiWorkspaceShelf v-if="!loading" :key="auth.user?.id" ref="shelf" :content="workspaceContent" :context="workspaceContext" :ready="!busy" :pending="pending" :modified="dirty || !!message || !!importJson" @protected="locallySaved=$event" @restore="restoreWorkspace" @resumed="resumedWorkspace"/>
    <p v-if="detached" class="ai-service-note" role="status">当前是独立保存副本，可编辑和导出，不可直接发送或生成。请展开草稿箱，保存云端后选择“新建对话继续此草稿”；调整旧对象时需重新核对最新快照。</p>
    <details v-if="remoteSession && draft" class="ai-service-note"><summary>比较本机草稿与云端对话</summary><p>本机修改保留，云端版本 {{remoteSession.version}}；没有自动合并。若版本不同，请另存独立草稿后重新核对。</p><div class="ai-compare"><div><h3>本机内容</h3><pre>{{JSON.stringify(draft,null,2)}}</pre></div><div><h3>云端内容</h3><pre>{{JSON.stringify(remoteSession.draft,null,2)}}</pre></div></div></details>
    <p class="helper-text" aria-label="AI 额度说明">{{aiQuotaHint(quota)}}</p>
    <details class="ai-service-note ai-help"><summary>不确定下一步怎么做？</summary><ol><li>说明目标、每次可投入多久、有没有期限；不确定模式可以先让 AI 推荐。</li><li>收到草稿后可以手改，也可以继续对话；手改后先保存。</li><li>核对内容并勾选确认，再点击生成或应用。仅收到回复不会创建计划。</li></ol><p><router-link to="/learn/ai">进入 AI 四步教学</router-link> · <router-link to="/plans">手动周期计划</router-link> · <router-link to="/mainlines">手动目标路线</router-link> · <router-link to="/todos">手动待办</router-link></p><p>若请求结果不确定，先刷新最近对话核对，不要反复发送。需要反馈时只提供错误分类、发生时间和客户端版本，不要发送密码、完整邀请链接或私人对话。</p></details>
    <p v-if="isAdjustment && queued !== 0" class="error-text" role="status">{{ queued > 0 ? `本机仍有 ${queued} 项待同步操作或冲突，请先到同步中心处理，再应用 AI 调整。` : '暂时无法核对本机队列，应用前会重新检查。' }}<router-link to="/sync">进入同步中心</router-link></p>
    <details v-if="mode" class="ai-service-note"><summary>给外部 AI 的格式说明</summary><p>内置 AI 不可用时，可复制这段说明给外部 AI，再在下方导入返回的草稿。{{ isAdjustment ? '调整格式会包含所选对象的事项和说明，请确认愿意向你选用的外部 AI 提供这些内容。' : '' }}</p><label class="ai-format">外部 AI 格式说明<textarea :value="externalFormat" readonly rows="8" /></label><button class="btn" :disabled="!!requestedTarget && !session" @click="copyFormat">复制格式说明</button></details>
    <section v-if="session?.adjustment && !session.applied" class="ai-service-note ai-pacing" aria-label="调整节奏指南"><h2>想怎样调整节奏？</h2><p>所选对象共 {{ session.adjustment.original.items.length }} 项，其中 {{ session.adjustment.protectedItems.length }} 项有执行历史，受到保护。先说明新的时间安排，再核对下方差异；只有确认后才应用。</p><div class="button-row"><button v-for="item in pacing" :key="item.id" class="btn" type="button" :disabled="busy || pending || dirty || queued !== 0" @click="usePacing(item.message)">{{ item.label }}</button></div><p class="helper-text">示例只填写输入框，不自动调用 AI、不扣次数、不自动修改计划。可以在发送前继续编辑。<router-link to="/learn/ai">查看 AI 教学</router-link></p></section>
    <nav class="ai-panel-tabs" aria-label="AI 工作区"><button :aria-pressed="mobilePanel==='chat'" @click="mobilePanel='chat'">对话</button><button :aria-pressed="mobilePanel==='draft'" @click="mobilePanel='draft'">草稿{{draft?` · ${draft.items.length} 项`:''}}</button></nav>
    <div class="ai-layout" :data-panel="mobilePanel">
      <section class="section ai-chat" aria-labelledby="ai-chat-title"><header class="section-title"><h2 id="ai-chat-title"><span>01</span> 对话</h2><strong v-if="session?.mode">{{ aiModeNames[session.mode] }}</strong></header>
        <div class="ai-chat-body"><label v-if="!session">创建方式<select v-model="mode" :disabled="!!requestedTarget"><option value="">先请 AI 推荐模式</option><option v-for="(name, value) in aiModeNames" :key="value" :value="value">{{ name }}</option></select></label><p v-if="!session?.turns.length" class="ai-empty">{{ isAdjustment ? '告诉我哪里太紧、哪里需要增加练习，或希望怎样调整。已有执行历史的事项会保留，不会重新创建另一份计划。' : '告诉我你想做什么、每次能投入多久，以及是否有截止日期。没有固定时间的长期目标，也可以慢慢推进。' }}</p>
          <ol ref="chatScroll" class="ai-turns" aria-label="对话记录" tabindex="0"><li v-for="turn in session?.turns" :key="turn.id"><article class="ai-user-message"><strong>你</strong><p>{{ turn.message }}</p></article><article class="ai-assistant-message"><strong>规划助手</strong><p v-if="turn.reply">{{ turn.reply }}</p><p v-else role="status">{{ aiState(turn.state) }}</p><p v-if="aiFailureGuidance(turn.state,turn.error)" class="helper-text">{{aiFailureGuidance(turn.state,turn.error)}}</p><small v-if="turn.error">错误分类：{{turn.error}} · {{new Date(turn.createdAt).toLocaleString('zh-CN')}}</small></article></li></ol>
          <div v-if="session && !session.mode" class="ai-mode-choice"><p v-if="session.recommendedMode">AI 建议使用<strong>{{ aiModeNames[session.recommendedMode] }}</strong>，请你选择：</p><p v-else>选择规划模式后，AI 才能生成对应草稿。</p><div class="button-row"><button v-for="(name, value) in aiModeNames" :key="value" class="btn" :disabled="!editable" @click="choose(value)">选择{{ name }}</button></div></div>
          <details class="ai-mode-examples"><summary>三种模式有什么不同？（静态结构示例）</summary><article v-for="(example,key) in modeExamples" :key="key"><h3>{{aiModeNames[key]}}</h3><p>{{example.purpose}}</p><p>{{example.example}}</p><button v-if="!isAdjustment && (!session || session.mode)" class="btn" :disabled="!editable||detached||mode===key" @click="changeMode(key)">改用{{aiModeNames[key]}}</button></article><p class="helper-text">以上只是结构说明，不是 AI 生成结果，也不会产生三次调用。AI 推荐原因见上方回复；由你确认模式后再生成完整草稿。</p></details>
          <form v-if="!session?.applied" class="ai-message-form" @submit.prevent="send">
            <label>你的目标或修改要求<textarea v-model="message" rows="3" :maxlength="capability.maxInputChars" :disabled="busy || pending" :placeholder="isAdjustment ? '例如：最近比较忙，把未开始的任务分散到两周，每次缩短到15分钟。' : '例如：我想学吉他，每次有半小时，没有固定结束时间。'" required /></label>
            <label v-if="!session && !detached" class="ai-consent"><input v-model="consent" type="checkbox" />{{ isAdjustment ? '我同意将所选对象的事项、阶段及必要内容和本次对话发给 DeepSeek 处理；不发送其他计划、账号或执行备注。' : '我同意将本次对话和草稿发给 DeepSeek 处理；请勿输入密码、身份信息等敏感内容。' }}</label>
            <details class="ai-message-privacy"><summary>处理范围与保留期限</summary><p class="helper-text">对话保存 30 天，可主动删除；独立保存草稿不随对话到期删除。{{ isAdjustment ? '仅使用所选对象，执行历史受到保护。' : '只发送本次对话与草稿，不读取已有私人计划。' }}每次成功回复计 1 次。</p></details>
            <button v-if="requestedTarget && !session && !detached" class="btn" type="button" :disabled="busy || !consent || !capability.adjustmentEnabled || queued !== 0" @click="loadSelection">载入所选内容（不调用 AI）</button>
            <p v-if="dirty" class="helper-text">先保存草稿修改，再让 AI 继续调整。</p><p v-if="uncertain" class="helper-text">请先刷新确认本次请求状态，再继续发送。</p>
            <div class="button-row"><button class="btn" type="button" :disabled="busy||pending" @click="clearInput">清空输入</button><button class="btn primary" :disabled="detached || busy || pending || uncertain || dirty || !capability.available || !message.trim() || (!session && !consent) || !quota?.remaining || (isAdjustment && queued !== 0)">{{ pending ? 'AI 正在处理…' : '发送给 AI' }}</button></div>
          </form>
        </div>
      </section>
      <section class="section ai-preview" aria-labelledby="ai-preview-title"><header class="section-title"><h2 id="ai-preview-title"><span>02</span> {{ isAdjustment ? '调整预览' : '草稿预览' }}</h2><span v-if="draft">{{ draft.items.length }} 项</span></header>
        <div class="ai-preview-body"><template v-if="draft"><p class="helper-text">{{ aiModeNames[draft.mode] }} · {{ dirty ? '编辑内容待核对' : '已保存对话草稿' }}。此处编辑不会直接改变真实业务。</p>
          <AiChangePreview v-if="session?.adjustment" :adjustment="session.adjustment" :draft="draft"/>
          <AiDraftEditor :key="`${session?.id}:${session?.version}`" :draft="draft" :adjustment="session?.adjustment" :disabled="!editable" @change="changed"/>
          <button class="btn" :disabled="!session || !editable || !dirty || detached" @click="saveDraft">保存草稿修改</button>
          <template v-if="!session?.applied"><label class="ai-consent"><input v-model="confirmed" type="checkbox" :disabled="detached || !session || !editable || dirty || (isAdjustment && (!differences.length || queued !== 0))"/>{{ isAdjustment ? '我已逐项核对前后差异，确认将建议应用到原对象，保留执行历史。' : `我已核对 ${draft.items.length} 项内容，确认创建新的${aiModeNames[draft.mode]}。` }}</label><button class="btn primary" :disabled="detached || !session || !editable || dirty || !confirmed || (isAdjustment && (!differences.length || queued !== 0))" @click="apply">{{ isAdjustment ? '确认应用修改' : '确认生成' }}</button></template>
        </template><p v-else class="ai-empty">草稿会显示在这里。AI 需要先了解你的要求；也可以导入符合格式的外部 AI 草稿。</p>
        <p v-if="session?.applied" class="ai-applied" role="status">{{ session.adjustment ? `已应用 ${session.applied.count} 处修改，原对象和执行历史保留` : `已生成 ${session.applied.count} 项内容` }}。<router-link v-if="resultPath" :to="resultPath">进入查看</router-link>。重复确认不会再次创建。</p>
        <details v-if="!session?.applied" class="ai-import"><summary>导入外部 AI 草稿</summary><p class="helper-text">{{ isAdjustment ? '调整草稿须保留原事项和阶段的 localId；不得删除或改写受保护事项。请使用上方已载入的草稿结构，导入后核对差异。' : '使用计划舱 schemaVersion 1 JSON 格式，模式须与当前选择一致。导入后仍须预览确认。' }}</p><label>草稿 JSON<textarea v-model="importJson" rows="6" maxlength="128000" :disabled="!editable"/></label><label v-if="!session&&!detached" class="ai-consent"><input v-model="consent" type="checkbox"/>同意上方对话处理说明（仅导入不会调用 AI）。</label><button class="btn" :disabled="detached || !editable || !importJson.trim() || !mode || (!session && !consent)" @click="importDraft">导入并校验草稿</button></details></div>
      </section>
    </div>
  </div>
</template>
<style scoped>
.ai-turns{max-height:440px;overflow-y:auto;overscroll-behavior:contain;scrollbar-gutter:stable}.ai-message-form{position:sticky;bottom:0;background:#fff;z-index:2;padding-bottom:env(safe-area-inset-bottom)}.ai-preview-body{max-height:850px;overflow-y:auto;overscroll-behavior:contain}.ai-panel-tabs{display:none}.ai-mode-examples{border-top:1px solid var(--line);font-size:.85rem}.ai-mode-examples summary,.ai-message-privacy summary{cursor:pointer;min-height:44px;line-height:1.6;padding:12px 0;font-size:.8rem}.ai-mode-examples article{border-top:1px solid var(--line);padding:12px 0}.ai-mode-examples h3{font-size:1rem;margin:0}.ai-mode-examples p{line-height:1.7}.ai-compare{display:grid;grid-template-columns:1fr 1fr;gap:16px}.ai-compare>div{min-width:0}.ai-compare pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:240px;overflow:auto;font-size:.75rem}
@media(max-width:900px){.ai-panel-tabs{display:flex;position:sticky;top:68px;z-index:3;border:1px solid var(--line);background:#fff;margin:16px 0}.ai-panel-tabs button{flex:1;min-height:48px;border:0;background:#fff;font-weight:700;color:var(--muted);border-bottom:3px solid transparent}.ai-panel-tabs button[aria-pressed=true]{color:#002FA7;border-bottom-color:#002FA7}.ai-layout[data-panel=chat]>.ai-preview,.ai-layout[data-panel=draft]>.ai-chat{display:none}.ai-turns{max-height:clamp(180px,38dvh,360px)}.ai-preview-body{max-height:none;overflow:visible}.ai-message-form{bottom:calc(66px + env(safe-area-inset-bottom));padding-bottom:12px}.ai-keyboard-open .ai-message-form{bottom:0}.ai-message-form textarea{max-height:160px;min-height:86px}.ai-compare{grid-template-columns:1fr}.ai-message-form .button-row{display:flex;gap:8px}.ai-message-form .button-row>.primary{flex:1}}
.ai-pacing h2{font-size:1rem;margin:0}.ai-pacing .button-row{flex-wrap:wrap;gap:10px}.ai-pacing p{overflow-wrap:anywhere}
.ai-page{max-width:1440px}.ai-page .page-head p{max-width:680px}.ai-page .btn{min-height:44px}.ai-service-note{padding:16px;border:1px solid var(--line);background:#fff;font-size:.85rem;line-height:1.7}.ai-toolbar{display:flex;gap:18px;align-items:end;margin:24px 0;flex-wrap:wrap}.ai-toolbar label{display:grid;gap:8px;max-width:420px;flex:1;min-width:220px;font-size:.8rem;font-weight:700}.ai-toolbar p{font-size:.8rem;line-height:1.8;margin:0;flex:1}.ai-toolbar p strong{color:#002fa7;font-size:1.5rem;font-variant-numeric:tabular-nums}.ai-toolbar>.text-btn{min-height:44px}.ai-layout{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:24px;align-items:start}.ai-layout .section{margin:0;min-width:0;background:#fff}.ai-layout .section-title{gap:12px;align-items:center;flex-wrap:wrap}.ai-layout h2{font-size:1.2rem;margin:0}.ai-layout h2 span{font-size:1.9rem;color:#002fa7;margin-right:12px;font-weight:400}.ai-layout .section-title>strong{font-size:.8rem;color:#002fa7}.ai-chat-body,.ai-preview-body{padding:24px;display:grid;gap:20px;min-width:0}.ai-layout label:not(.ai-consent){display:grid;gap:8px;font-size:.8rem;font-weight:700}.ai-empty{color:var(--muted);font-size:.85rem;line-height:1.8;margin:0}.ai-turns{padding:0;margin:0;list-style:none;display:grid;gap:24px}.ai-turns article{padding:16px 0;border-top:1px solid var(--line)}.ai-turns strong{font-size:.8rem;color:#002fa7}.ai-turns p{font-size:.9rem;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere;margin:8px 0 0}.ai-user-message{padding-left:16px!important;border-left:2px solid #002fa7}.ai-message-form{display:grid;gap:16px;border-top:1px solid var(--line);padding-top:20px}.ai-message-form .helper-text{margin:0}.ai-consent{display:flex;align-items:flex-start;gap:10px;font-size:.8rem;line-height:1.7}.ai-consent input{width:20px;height:20px;margin:3px 0 0;flex:0 0 20px}.ai-mode-choice{padding:16px;background:#f7f7f8}.ai-mode-choice p{margin:0 0 12px;font-size:.85rem;line-height:1.7}.ai-mode-choice .button-row{gap:8px;flex-wrap:wrap}.ai-import{border-top:1px solid var(--line);padding-top:16px}.ai-import summary{cursor:pointer;line-height:44px;font-size:.85rem;font-weight:700}.ai-import label,.ai-import button{margin-top:14px}.ai-applied{line-height:1.8;padding:16px;border:1px solid #002fa7;font-size:.85rem}.ai-layout textarea{resize:vertical;max-width:100%;min-width:0}@media(max-width:900px){.ai-layout{grid-template-columns:1fr;gap:20px}.ai-chat-body,.ai-preview-body{padding:16px}.ai-toolbar{gap:12px;margin:20px 0}.ai-toolbar label{max-width:none;flex-basis:100%;min-width:0}.ai-toolbar p{flex-basis:70%}.ai-page .page-head>.button-row{flex-wrap:wrap}.ai-page .page-head .btn{flex:1}.ai-layout .section-title{padding:16px}.ai-message-form>.btn,.ai-preview-body>.btn{width:100%}}
</style>
