<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import { api, apiMessage } from '@/api/client';
import { aiModeNames, aiInitialMode, aiPending, aiState, aiAppliedPath, aiExternalPrompt, type AiMode, type AiSession, type AiSummary, type AiDraft } from '@/aiPlanning';
import type { AiUsage } from '@/aiQuota';
import AiDraftEditor from '@/components/AiDraftEditor.vue';
const route = useRoute(), router = useRouter();
const capability = ref({ available: false, message: '正在确认 AI 服务状态…', maxInputChars: 8000 });
const quota = ref<AiUsage | null>(null), summaries = ref<AiSummary[]>([]), session = ref<AiSession | null>(null);
const mode = ref<AiMode | ''>(aiInitialMode(route.query.mode) ?? ''), consent = ref(false), message = ref(''), error = ref(''), notice = ref('');
const busy = ref(false), loading = ref(true), draft = ref<AiDraft | null>(null), dirty = ref(false), confirmed = ref(false), importJson = ref(''), uncertain = ref(false);
const pending = computed(() => aiPending(session.value));
const editable = computed(() => !busy.value && !pending.value && !session.value?.applied);
const resultPath = computed(() => aiAppliedPath(session.value?.applied?.path));
let timer: ReturnType<typeof setTimeout> | undefined; let disposed = false; let generation = 0;
function accept(value: AiSession) { session.value = value; draft.value = value.draft ? structuredClone(value.draft) : null; dirty.value = false; confirmed.value = false; mode.value = value.mode ?? ''; }
async function loadSummary() { summaries.value = (await api.get<AiSummary[]>('/ai/sessions')).data; }
async function loadQuota() { quota.value = (await api.get<AiUsage>('/ai/quota')).data; }
function schedule() { clearTimeout(timer); if (!disposed && pending.value) timer = setTimeout(() => void refresh(false), 1500); }
async function refresh(manual = true) {
  if (manual && dirty.value && !window.confirm('刷新会放弃尚未保存的草稿修改，是否继续？')) return;
  error.value = '';
  const id = session.value?.id, expected = generation;
  try { if (id) { const data = (await api.get<AiSession>(`/ai/sessions/${id}`)).data; if (expected !== generation || disposed) return; accept(data); } await Promise.all([loadQuota(), loadSummary()]); uncertain.value = false; }
  catch (reason) { error.value = apiMessage(reason); }
  finally { schedule(); }
}
async function open(id: string) {
  if (dirty.value && !window.confirm('切换会放弃尚未保存的草稿修改，是否继续？')) return;
  busy.value = true; error.value = ''; clearTimeout(timer); const expected = ++generation;
  try { const data = (await api.get<AiSession>(`/ai/sessions/${id}`)).data; if (expected !== generation || disposed) return; accept(data); consent.value = true; await router.replace({ path: '/ai', query: { session: id } }); }
  catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; schedule(); }
}
async function create() {
  if (!consent.value) throw new Error('请先阅读并勾选对话处理说明。');
  const value = (await api.post<AiSession>('/ai/sessions', { mode: mode.value || null, consent: true })).data;
  accept(value); await router.replace({ path: '/ai', query: { session: value.id } }); await loadSummary();
}
async function send() {
  if (busy.value || pending.value || uncertain.value || dirty.value) return;
  busy.value = true; error.value = ''; notice.value = '';
  try { if (!session.value) await create(); await api.post(`/ai/sessions/${session.value!.id}/jobs`, { message: message.value, version: session.value!.version, operationKey: crypto.randomUUID() }); message.value = ''; await refresh(false); }
  catch (reason) { error.value = apiMessage(reason); uncertain.value = true; }
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
  try { accept((await api.put<AiSession>(`/ai/sessions/${session.value.id}/draft`, { version: session.value.version, draftJson: JSON.stringify(draft.value) })).data); notice.value = '草稿已保存，还没有生成真实计划。'; }
  catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; }
}
async function apply() {
  if (!session.value || !confirmed.value || dirty.value) return;
  busy.value = true; error.value = '';
  try { await api.post(`/ai/sessions/${session.value.id}/apply`, { version: session.value.version, confirmed: true }); await refresh(false); notice.value = '已生成，可进入对应模式查看。'; }
  catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; }
}
async function importDraft() {
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
  if (dirty.value && !window.confirm('新建对话会放弃尚未保存的草稿修改，是否继续？')) return;
  generation++; clearTimeout(timer); session.value = null; draft.value = null; dirty.value = false; confirmed.value = false; consent.value = false; uncertain.value = false; message.value = ''; error.value = ''; notice.value = ''; mode.value = aiInitialMode(route.query.mode) ?? ''; await router.replace('/ai');
}
function changed() { dirty.value = true; confirmed.value = false; }
async function copyFormat() { if (!mode.value) return; try { await navigator.clipboard.writeText(aiExternalPrompt(mode.value)); notice.value = '格式说明已复制，可发给外部 AI。'; } catch { error.value = '无法自动复制，请在下方格式说明中手动选择并复制。'; } }
onBeforeRouteLeave(() => !dirty.value || window.confirm('草稿有尚未保存的修改，仍要离开吗？'));
watch(() => route.query.session, id => { if (typeof id === 'string' && id !== session.value?.id && !busy.value) void open(id); });
onMounted(async () => {
  try { capability.value = (await api.get('/ai/capabilities')).data; await Promise.all([loadQuota(), loadSummary()]); if (typeof route.query.session === 'string') await open(route.query.session); }
  catch (reason) { error.value = apiMessage(reason); } finally { loading.value = false; }
});
onUnmounted(() => { disposed = true; clearTimeout(timer); });
</script>
<template>
  <div class="page ai-page">
    <header class="page-head"><div><h1>AI 规划助手</h1><p>先聊清目标，再预览草稿。你确认以后，才生成真实计划。</p></div><div class="button-row"><button class="btn" :disabled="busy" @click="fresh">新建对话</button><button class="btn" :disabled="busy || loading" @click="refresh()">刷新对话</button></div></header>
    <p v-if="error" class="error-text" role="alert">{{ error }}</p><p v-if="notice" class="helper-text" role="status">{{ notice }}</p>
    <p v-if="!capability.available && !loading" class="ai-service-note">{{ capability.message }} <router-link to="/plans">手动周期计划</router-link> · <router-link to="/mainlines">手动目标路线</router-link> · <router-link to="/todos">手动待办</router-link></p>
    <div class="ai-toolbar"><label>最近对话<select :value="session?.id ?? ''" :disabled="busy" @change="open(($event.target as HTMLSelectElement).value)"><option value="" disabled>选择已保存的对话</option><option v-for="item in summaries" :key="item.id" :value="item.id">{{ item.mode ? aiModeNames[item.mode] : '模式待定' }} · {{ new Date(item.createdAt).toLocaleString('zh-CN') }}{{ item.applied ? ' · 已生成' : '' }}</option></select></label><p v-if="quota">今日剩余 <strong>{{ quota.remaining }}</strong> / {{ quota.dailyLimit }} 次 · 已用 {{ quota.used }} · 占用 {{ quota.pending }}<span v-if="quota.paused"> · AI 已暂停</span></p><button v-if="session" class="text-btn danger-text" :disabled="busy || pending" @click="remove">删除对话</button></div>
    <details v-if="mode" class="ai-service-note"><summary>给外部 AI 的格式说明</summary><p>内置 AI 不可用时，可复制这段说明给外部 AI，再在下方导入返回的草稿。</p><label class="ai-format">外部 AI 格式说明<textarea :value="aiExternalPrompt(mode)" readonly rows="8" /></label><button class="btn" @click="copyFormat">复制格式说明</button></details>
    <div class="ai-layout">
      <section class="section ai-chat" aria-labelledby="ai-chat-title"><header class="section-title"><h2 id="ai-chat-title"><span>01</span> 对话</h2><strong v-if="session?.mode">{{ aiModeNames[session.mode] }}</strong></header>
        <div class="ai-chat-body"><label v-if="!session">创建方式<select v-model="mode"><option value="">先请 AI 推荐模式</option><option v-for="(name, value) in aiModeNames" :key="value" :value="value">{{ name }}</option></select></label><p v-if="!session?.turns.length" class="ai-empty">告诉我你想做什么、每次能投入多久，以及是否有截止日期。没有固定时间的长期目标，也可以慢慢推进。</p>
          <ol class="ai-turns" aria-label="对话记录"><li v-for="turn in session?.turns" :key="turn.id"><article class="ai-user-message"><strong>你</strong><p>{{ turn.message }}</p></article><article class="ai-assistant-message"><strong>规划助手</strong><p v-if="turn.reply">{{ turn.reply }}</p><p v-else role="status">{{ aiState(turn.state) }}</p><p v-if="turn.state === 'FAILED' || turn.state === 'UNKNOWN'" class="helper-text">已有草稿仍保留，可手工编辑或稍后再试。不会自动重复调用。{{ turn.error ? `（${turn.error}）` : '' }}</p></article></li></ol>
          <div v-if="session && !session.mode" class="ai-mode-choice"><p v-if="session.recommendedMode">AI 建议使用<strong>{{ aiModeNames[session.recommendedMode] }}</strong>，请你选择：</p><p v-else>选择规划模式后，AI 才能生成对应草稿。</p><div class="button-row"><button v-for="(name, value) in aiModeNames" :key="value" class="btn" :disabled="!editable" @click="choose(value)">选择{{ name }}</button></div></div>
          <form v-if="!session?.applied" class="ai-message-form" @submit.prevent="send"><label>你的目标或修改要求<textarea v-model="message" rows="4" :maxlength="capability.maxInputChars" :disabled="busy || pending" placeholder="例如：我想学吉他，每次有半小时，没有固定结束时间。" required /></label><label v-if="!session" class="ai-consent"><input v-model="consent" type="checkbox" />我同意将本次对话和草稿发给 DeepSeek 处理；请勿输入密码、身份信息等敏感内容。</label><p class="helper-text">对话保存 30 天，可主动删除。只发送本次对话与草稿，不读取你已有的私人计划。每次成功回复计 1 次。</p><p v-if="dirty" class="helper-text">先保存草稿修改，再让 AI 继续调整。</p><p v-if="uncertain" class="helper-text">请先刷新确认本次请求状态，再继续发送。</p><button class="btn primary" :disabled="busy || pending || uncertain || dirty || !capability.available || !message.trim() || (!session && !consent) || !quota?.remaining">{{ pending ? 'AI 正在处理…' : '发送给 AI' }}</button></form>
        </div>
      </section>
      <section class="section ai-preview" aria-labelledby="ai-preview-title"><header class="section-title"><h2 id="ai-preview-title"><span>02</span> 草稿预览</h2><span v-if="draft">{{ draft.items.length }} 项</span></header><div class="ai-preview-body"><template v-if="draft"><p class="helper-text">{{ aiModeNames[draft.mode] }} · {{ dirty ? '有未保存修改' : '已保存草稿' }}。此处编辑不会直接改变真实业务。</p><AiDraftEditor :key="`${session?.id}:${session?.version}`" :draft="draft" :disabled="!editable" @change="changed" /><button class="btn" :disabled="!editable || !dirty" @click="saveDraft">保存草稿修改</button><template v-if="!session?.applied"><label class="ai-consent"><input v-model="confirmed" type="checkbox" :disabled="!editable || dirty" />我已核对 {{ draft.items.length }} 项内容，确认创建新的{{ aiModeNames[draft.mode] }}。</label><button class="btn primary" :disabled="!editable || dirty || !confirmed" @click="apply">确认生成</button></template></template><p v-else class="ai-empty">草稿会显示在这里。AI 需要先了解你的要求；也可以导入符合格式的外部 AI 草稿。</p><p v-if="session?.applied" class="ai-applied" role="status">已生成 {{ session.applied.count }} 项内容。<router-link v-if="resultPath" :to="resultPath">进入查看</router-link>。重复确认不会再次创建。</p><details v-if="!session?.applied" class="ai-import"><summary>导入外部 AI 草稿</summary><p class="helper-text">使用计划舱 schemaVersion 1 JSON 格式，模式须与当前选择一致。导入后仍须预览确认。</p><label>草稿 JSON<textarea v-model="importJson" rows="6" maxlength="128000" :disabled="!editable" /></label><label v-if="!session" class="ai-consent"><input v-model="consent" type="checkbox" />同意上方对话处理说明（仅导入不会调用 AI）。</label><button class="btn" :disabled="!editable || !importJson.trim() || !mode || (!session && !consent)" @click="importDraft">导入并校验草稿</button></details></div></section>
    </div>
  </div>
</template>
<style scoped>
.ai-page{max-width:1440px}.ai-page .page-head p{max-width:680px}.ai-page .btn{min-height:44px}.ai-service-note{padding:16px;border:1px solid var(--line);background:#fff;font-size:.85rem;line-height:1.7}.ai-toolbar{display:flex;gap:18px;align-items:end;margin:24px 0;flex-wrap:wrap}.ai-toolbar label{display:grid;gap:8px;max-width:420px;flex:1;min-width:220px;font-size:.8rem;font-weight:700}.ai-toolbar p{font-size:.8rem;line-height:1.8;margin:0;flex:1}.ai-toolbar p strong{color:#002fa7;font-size:1.5rem;font-variant-numeric:tabular-nums}.ai-toolbar>.text-btn{min-height:44px}.ai-layout{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:24px;align-items:start}.ai-layout .section{margin:0;min-width:0;background:#fff}.ai-layout .section-title{gap:12px;align-items:center;flex-wrap:wrap}.ai-layout h2{font-size:1.2rem;margin:0}.ai-layout h2 span{font-size:1.9rem;color:#002fa7;margin-right:12px;font-weight:400}.ai-layout .section-title>strong{font-size:.8rem;color:#002fa7}.ai-chat-body,.ai-preview-body{padding:24px;display:grid;gap:20px;min-width:0}.ai-layout label:not(.ai-consent){display:grid;gap:8px;font-size:.8rem;font-weight:700}.ai-empty{color:var(--muted);font-size:.85rem;line-height:1.8;margin:0}.ai-turns{padding:0;margin:0;list-style:none;display:grid;gap:24px}.ai-turns article{padding:16px 0;border-top:1px solid var(--line)}.ai-turns strong{font-size:.8rem;color:#002fa7}.ai-turns p{font-size:.9rem;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere;margin:8px 0 0}.ai-user-message{padding-left:16px!important;border-left:2px solid #002fa7}.ai-message-form{display:grid;gap:16px;border-top:1px solid var(--line);padding-top:20px}.ai-message-form .helper-text{margin:0}.ai-consent{display:flex;align-items:flex-start;gap:10px;font-size:.8rem;line-height:1.7}.ai-consent input{width:20px;height:20px;margin:3px 0 0;flex:0 0 20px}.ai-mode-choice{padding:16px;background:#f7f7f8}.ai-mode-choice p{margin:0 0 12px;font-size:.85rem;line-height:1.7}.ai-mode-choice .button-row{gap:8px;flex-wrap:wrap}.ai-import{border-top:1px solid var(--line);padding-top:16px}.ai-import summary{cursor:pointer;line-height:44px;font-size:.85rem;font-weight:700}.ai-import label,.ai-import button{margin-top:14px}.ai-applied{line-height:1.8;padding:16px;border:1px solid #002fa7;font-size:.85rem}.ai-layout textarea{resize:vertical;max-width:100%;min-width:0}@media(max-width:900px){.ai-layout{grid-template-columns:1fr;gap:20px}.ai-chat-body,.ai-preview-body{padding:16px}.ai-toolbar{gap:12px;margin:20px 0}.ai-toolbar label{max-width:none;flex-basis:100%;min-width:0}.ai-toolbar p{flex-basis:70%}.ai-page .page-head>.button-row{flex-wrap:wrap}.ai-page .page-head .btn{flex:1}.ai-layout .section-title{padding:16px}.ai-message-form>.btn,.ai-preview-body>.btn{width:100%}}
</style>
