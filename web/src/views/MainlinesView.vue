<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, apiMessage, OfflineQueuedError } from '@/api/client';
import { requiresConnection } from '@/connectionState';
import AppSheet from '@/components/AppSheet.vue';
import LocalBadge from '@/components/LocalBadge.vue';
import { routeGroups } from '@/routeStages';
import { routeStatus } from '@/routeProgress';
import type { Mainline, MainlineItem, MainlineStage } from '@/types';
const route = useRoute(); const router = useRouter();
const lines = ref<Mainline[]>([]); const selectedId = ref(typeof route.query.line === 'string' ? route.query.line : '');
const error = ref(''); const notice = ref(''); const saving = ref(false);
const creatingLine = ref(false);
const selected = computed(() => lines.value.find(line => line.id === selectedId.value) ?? lines.value[0]);
const lineDraft = reactive({ name: '', description: '', color: '#002FA7', executionMode: 'FLEXIBLE' });
const lineEdit = reactive({ name: '', description: '', color: '#002FA7', executionMode: 'FLEXIBLE', version: 0 });
const sheet = ref<'step' | 'line' | 'stages' | null>(null);
const editingItem = ref<MainlineItem | null>(null);
const itemDraft = reactive({ title: '', description: '', acceptanceText: '', plannedMinutes: 60, stageId: '', dependencyMode: 'INHERIT', prerequisiteIds: [] as string[] });
const editingStage = ref<MainlineStage | null>(null);
const stageDraft = reactive({ name: '', description: '', sortOrder: 0 });
const search = ref(''); const unfinishedOnly = ref(false);
const pages = reactive<Record<string, number>>({}); const collapsed = reactive<Record<string, boolean>>({});
const groups = computed(() => selected.value ? routeGroups(selected.value, search.value, unfinishedOnly.value) : []);
const candidates = computed(() => selected.value?.items.filter(i => i.id !== editingItem.value?.id) ?? []);
function showError(e: unknown) { error.value = apiMessage(e).replace(/主线/g, '目标路线'); }
async function selectLine(id: string) { selectedId.value = id; await router.replace({ path: '/mainlines', query: { line: id } }); }
async function load() {
  lines.value = (await api.get<Mainline[]>('/mainlines')).data;
  if (selected.value && selectedId.value !== selected.value.id) await selectLine(selected.value.id);
}
async function createLine() {
  if (creatingLine.value) return; creatingLine.value = true;
  try { error.value = ''; const { data } = await api.post<Mainline>('/mainlines', lineDraft); lineDraft.name = ''; lineDraft.description = ''; selectedId.value = data.id; await load(); await selectLine(data.id); } catch (e) { showError(e); } finally { creatingLine.value = false; }
}
function openStep(item: MainlineItem | null = null) {
  if (item?.localState || selected.value?.localState) return;
  editingItem.value = item; error.value = '';
  Object.assign(itemDraft, { title: item?.title ?? '', description: item?.description ?? '', acceptanceText: item?.acceptance.join('\n') ?? '完成本步骤', plannedMinutes: item?.plannedMinutes ?? 60, stageId: item?.stageId ?? '', dependencyMode: item?.dependencyMode ?? 'INHERIT', prerequisiteIds: [...(item?.prerequisiteIds ?? [])] });
  sheet.value = 'step';
}
async function saveStep() {
  if (!selected.value || saving.value) return;
  const item = editingItem.value;
  const routingChanged = !item || itemDraft.stageId !== (item.stageId ?? '') || itemDraft.dependencyMode !== (item.dependencyMode ?? 'INHERIT') || JSON.stringify([...itemDraft.prerequisiteIds].sort()) !== JSON.stringify([...(item.prerequisiteIds ?? [])].sort());
  const payload = { title: itemDraft.title, description: itemDraft.description, acceptance: itemDraft.acceptanceText.split('\n').map(s => s.trim()).filter(Boolean), plannedMinutes: itemDraft.plannedMinutes,
    sortOrder: item?.sortOrder ?? selected.value.nextSortOrder ?? Math.max(-1, ...selected.value.items.map(i => i.sortOrder)) + 1, version: item?.version,
    ...(!requiresConnection() && routingChanged ? { routing: { stageId: itemDraft.stageId || null, dependencyMode: itemDraft.dependencyMode, prerequisiteIds: itemDraft.dependencyMode === 'EXPLICIT' ? itemDraft.prerequisiteIds : [] } } : {}) };
  saving.value = true; error.value = '';
  try {
    if (item) await api.put('/mainlines/items/' + item.id, payload);
    else await api.post('/mainlines/' + selected.value.id + '/items', payload);
    await load(); sheet.value = null; notice.value = '步骤已保存。';
  } catch (e) {
    if (e instanceof OfflineQueuedError) { sheet.value = null; notice.value = '修改已保存在本机，联网后可同步。'; await load().catch(showError); }
    else showError(e);
  } finally { saving.value = false; }
}
function openLine() {
  if (!selected.value) return;
  Object.assign(lineEdit, { name: selected.value.name, description: selected.value.description, color: selected.value.color, executionMode: selected.value.executionMode ?? 'SEQUENTIAL', version: selected.value.version });
  error.value = ''; sheet.value = 'line';
}
async function saveLine() {
  if (!selected.value || saving.value) return; saving.value = true; error.value = '';
  try { await api.put('/mainlines/' + selected.value.id, lineEdit); await load(); sheet.value = null; }
  catch (e) { showError(e); } finally { saving.value = false; }
}
function openStages() { resetStage(); error.value = ''; sheet.value = 'stages'; }
function resetStage() { editingStage.value = null; Object.assign(stageDraft, { name: '', description: '', sortOrder: Math.max(-1, ...(selected.value?.stages ?? []).map(s => s.sortOrder)) + 1 }); }
function editStage(stage: MainlineStage) { editingStage.value = stage; Object.assign(stageDraft, stage); }
async function saveStage() {
  if (!selected.value || saving.value) return; saving.value = true; error.value = '';
  try {
    if (editingStage.value) await api.put('/mainlines/stages/' + editingStage.value.id, { ...stageDraft, version: editingStage.value.version });
    else await api.post('/mainlines/' + selected.value.id + '/stages', stageDraft);
    await load(); resetStage();
  } catch (e) { showError(e); } finally { saving.value = false; }
}
async function removeStage(stage: MainlineStage) {
  if (!confirm('删除阶段“' + stage.name + '”？步骤会保留并移至未分组。')) return;
  saving.value = true; error.value = '';
  try { await api.delete('/mainlines/stages/' + stage.id, { params: { version: stage.version } }); await load(); resetStage(); }
  catch (e) { showError(e); } finally { saving.value = false; }
}
async function action(item: MainlineItem, type: 'start' | 'pause' | 'complete') {
  try { error.value = ''; if (type === 'complete') await api.post('/mainlines/items/' + item.id + '/complete', { note: '', version: item.version }); else await api.post('/mainlines/items/' + item.id + '/' + type, null, { params: { version: item.version } }); await load(); } catch (e) { showError(e); }
}
async function removeLine() {
  if (!selected.value || !confirm('将目标路线“' + selected.value.name + '”移入回收站？')) return;
  try { await api.delete('/mainlines/' + selected.value.id); selectedId.value = ''; await load(); if (!selected.value) await router.replace('/mainlines'); } catch (e) { showError(e); }
}
async function removeItem(item: MainlineItem) {
  if (!confirm('将步骤“' + item.title + '”移入回收站？')) return;
  try { await api.delete('/mainlines/items/' + item.id); await load(); } catch (e) { showError(e); }
}
function pageNumber(id: string, count: number) { return Math.min(pages[id] ?? 1, Math.max(1, Math.ceil(count / 10))); }
function visibleItems(id: string, items: MainlineItem[]) { return items.slice((pageNumber(id, items.length) - 1) * 10, pageNumber(id, items.length) * 10); }
function blockers(item: MainlineItem) { return (item.blockers ?? []).map(id => selected.value?.items.find(i => i.id === id)?.title ?? '已移除的前置步骤').join('、') || '前面的未完成步骤'; }
watch([search, unfinishedOnly, selectedId], () => { Object.keys(pages).forEach(k => delete pages[k]); });
watch(() => route.query.line, id => { selectedId.value = typeof id === 'string' ? id : ''; });
onMounted(() => load().catch(showError));
</script>
<template><div class="page">
  <section class="page-head"><div><p class="eyebrow">ROUTE / STEPS</p><h1>目标路线</h1><p>不设结束日期，按自己的节奏逐步推进。</p></div></section>
  <p v-if="error && !sheet" class="error-text" role="alert">{{ error }}</p><p v-if="notice" class="helper-text" role="status">{{ notice }}</p>
  <div class="mainline-layout"><aside class="line-list" aria-label="路线列表">
    <button v-for="line in lines" :key="line.id" :class="{ active: selected?.id === line.id }" @click="selectLine(line.id)"><i :style="{ background: line.color }"/><strong>{{ line.name }}</strong><span>{{ line.completed }}/{{ line.total }}</span></button>
    <router-link class="btn" to="/ai?mode=ROUTE">使用 AI 构建目标路线</router-link>
    <details class="route-create" :open="!lines.length"><summary>新建目标路线</summary>
    <form @submit.prevent="createLine"><label>路线名称<input v-model="lineDraft.name" maxlength="120" placeholder="新路线名称" required/></label><label>路线说明<textarea v-model="lineDraft.description" maxlength="1000" placeholder="希望达成什么目标"/></label><div class="route-field"><label for="new-route-mode">推进方式</label><select id="new-route-mode" v-model="lineDraft.executionMode"><option value="FLEXIBLE">自由推进</option><option value="SEQUENTIAL">顺序推进</option></select></div><div class="button-row"><input v-model="lineDraft.color" type="color" aria-label="路线颜色"/><button class="btn primary" :disabled="requiresConnection() || creatingLine">{{ creatingLine ? '创建中…' : '新建路线' }}</button></div><p class="helper-text">新建路线、阶段及依赖调整需联网；已缓存步骤可离线编辑内容。</p></form>
    </details>
  </aside><section v-if="selected" class="section route-content">
    <router-link v-if="!selected.localState" class="btn" :to="{ path: '/ai', query: { mode: 'ROUTE', target: selected.id } }">使用 AI 调整此目标路线</router-link>
    <header class="section-title"><div><p class="helper-text">{{ selected.completed }} / {{ selected.total }} 步骤 · {{ selected.executionMode === 'FLEXIBLE' ? '自由推进' : '顺序推进' }}</p><h2>{{ selected.name }}</h2><p>{{ selected.description }}</p><LocalBadge :state="selected.localState"/></div><div class="button-row"><button class="btn" :disabled="requiresConnection() || !!selected.localState" @click="openLine">路线设置</button><button class="btn" :disabled="requiresConnection()" @click="openStages">管理阶段</button><button class="btn primary" :disabled="requiresConnection() || !!selected.localState" @click="openStep()">添加步骤</button><button class="text-btn danger-text" :disabled="requiresConnection()" @click="removeLine">移入回收站</button></div></header>
    <div class="route-filters"><label>搜索步骤<input v-model="search" type="search" placeholder="标题或说明"/></label><label class="check-row"><input v-model="unfinishedOnly" type="checkbox"/>只看未完成</label><button class="text-btn" @click="groups.forEach(g => collapsed[g.id] = true)">折叠全部</button><button class="text-btn" @click="groups.forEach(g => collapsed[g.id] = false)">展开全部</button></div>
    <section v-for="group in groups" :key="group.id" class="route-stage" :aria-label="group.name">
      <header class="route-stage-header"><button class="route-stage-toggle" :aria-expanded="!collapsed[group.id]" @click="collapsed[group.id] = !collapsed[group.id]"><strong>{{ group.name }}</strong><span>{{ group.completed }} / {{ group.total }} 完成 · {{ collapsed[group.id] ? '展开' : '收起' }}</span></button><p v-if="group.description" class="helper-text">{{ group.description }}</p></header>
      <div v-if="!collapsed[group.id]">
        <article v-for="item in visibleItems(group.id, group.items)" :key="item.id" class="mainline-step" :class="{ locked: !item.unlocked }">
          <strong class="step-number">{{ String(item.sortOrder + 1).padStart(2, '0') }}</strong>
          <div class="route-step-body"><span class="helper-text">{{ routeStatus(item.status) }} · {{ item.plannedMinutes }} 分钟参考</span><LocalBadge :state="item.localState"/><h3>{{ item.title }}</h3><p>{{ item.description }}</p><p v-if="!item.unlocked && item.status !== 'COMPLETED'" class="helper-text">等待：{{ blockers(item) }}</p></div>
          <div class="button-row"><button class="btn" :disabled="!!item.localState" @click="openStep(item)">编辑步骤</button><button v-if="item.status !== 'RUNNING' && item.status !== 'COMPLETED'" class="btn" :disabled="!item.unlocked || !!item.localState || requiresConnection()" @click="action(item, 'start')">开始</button><button v-if="item.status === 'RUNNING'" class="btn" :disabled="requiresConnection() || !!item.localState" @click="action(item, 'pause')">暂停</button><button v-if="item.status !== 'COMPLETED'" class="btn primary" :disabled="!item.unlocked || !!item.localState || requiresConnection()" @click="action(item, 'complete')">完成</button><button class="text-btn danger-text" :disabled="requiresConnection() || !!item.localState" @click="removeItem(item)">移入回收站</button></div>
        </article>
        <p v-if="!group.items.length" class="helper-text">没有符合筛选条件的步骤。</p>
        <nav v-if="group.items.length > 10" class="route-pagination" :aria-label="group.name + '步骤分页'"><button class="btn" :disabled="pageNumber(group.id, group.items.length) === 1" @click="pages[group.id] = pageNumber(group.id, group.items.length) - 1">上一页</button><span>{{ pageNumber(group.id, group.items.length) }} / {{ Math.ceil(group.items.length / 10) }} · 共 {{ group.items.length }} 步骤</span><button class="btn" :disabled="pageNumber(group.id, group.items.length) >= Math.ceil(group.items.length / 10)" @click="pages[group.id] = pageNumber(group.id, group.items.length) + 1">下一页</button></nav>
      </div>
    </section>
    <p class="helper-text">阶段只负责整理步骤，不设截止日期。参考分钟用于估算练习投入，不代表每日配额。</p>
  </section><section v-else class="empty section">先创建一条目标路线。</section></div>
  <AppSheet :open="sheet !== null" :title="sheet === 'step' ? (editingItem ? '编辑步骤' : '添加步骤') : sheet === 'line' ? '路线设置' : '管理阶段'" title-id="route-editor-title" :busy="saving" @close="sheet = null">
    <p v-if="error" class="error-text" role="alert">{{ error }}</p>
    <form v-if="sheet === 'step'" class="route-editor" @submit.prevent="saveStep">
      <label>步骤标题<input v-model="itemDraft.title" maxlength="180" required/></label><label>步骤说明<textarea v-model="itemDraft.description" maxlength="2000" rows="4"/></label><label>验收项（每行一项）<textarea v-model="itemDraft.acceptanceText" rows="3"/></label><label>参考分钟<input v-model.number="itemDraft.plannedMinutes" type="number" min="1" required/></label>
      <fieldset :disabled="requiresConnection()"><legend>步骤组织与前置条件</legend><div class="route-field"><label for="step-stage">所属阶段</label><select id="step-stage" v-model="itemDraft.stageId"><option value="">未分组</option><option v-for="stage in selected?.stages ?? []" :key="stage.id" :value="stage.id">{{ stage.name }}</option></select></div><div class="route-field"><label for="step-dependency">前置条件</label><select id="step-dependency" v-model="itemDraft.dependencyMode"><option value="INHERIT">跟随路线推进方式</option><option value="NONE">无前置条件，可独立推进</option><option value="EXPLICIT">指定前置步骤</option></select></div><div v-if="itemDraft.dependencyMode === 'EXPLICIT'" class="route-prerequisites"><label v-for="candidate in candidates" :key="candidate.id" class="check-row"><input v-model="itemDraft.prerequisiteIds" type="checkbox" :value="candidate.id"/>{{ candidate.title }}</label><p v-if="!candidates.length" class="helper-text">先添加其他步骤，再设置前置条件。</p></div><p class="helper-text">指定前置步骤时，只需完成选中的步骤；阶段本身不会锁定进度。</p></fieldset>
      <p v-if="requiresConnection()" class="helper-text">离线时可修改内容，阶段与前置条件保持不变。</p><button class="btn primary" :disabled="saving">{{ saving ? '保存中…' : '保存步骤' }}</button>
    </form>
    <form v-if="sheet === 'line'" class="route-editor" @submit.prevent="saveLine"><label>路线名称<input v-model="lineEdit.name" required maxlength="120"/></label><label>路线说明<textarea v-model="lineEdit.description" maxlength="1000" rows="4"/></label><label>路线颜色<input v-model="lineEdit.color" type="color"/></label><div class="route-field"><label for="edit-route-mode">推进方式</label><select id="edit-route-mode" v-model="lineEdit.executionMode"><option value="FLEXIBLE">自由推进</option><option value="SEQUENTIAL">顺序推进</option></select></div><p class="helper-text">顺序推进按步骤位置解锁；单个步骤仍可设置独立的前置条件。旧路线不会被自动改为自由推进。</p><button class="btn primary" :disabled="saving || requiresConnection()">保存路线设置</button></form>
    <div v-if="sheet === 'stages'" class="route-editor"><div v-for="stage in selected?.stages ?? []" :key="stage.id" class="route-stage-row"><div><strong>{{ stage.name }}</strong><p>{{ stage.description }}</p></div><div class="button-row"><button class="btn" :disabled="saving" @click="editStage(stage)">编辑阶段</button><button class="text-btn danger-text" :disabled="saving || requiresConnection()" @click="removeStage(stage)">删除阶段</button></div></div><form class="route-editor" @submit.prevent="saveStage"><h3>{{ editingStage ? '编辑阶段' : '添加阶段' }}</h3><label>阶段名称<input v-model="stageDraft.name" required maxlength="120"/></label><label>阶段说明<textarea v-model="stageDraft.description" maxlength="1000" rows="3"/></label><label>阶段位置<input v-model.number="stageDraft.sortOrder" type="number" min="0" required/></label><div class="button-row"><button class="btn primary" :disabled="saving || requiresConnection()">{{ editingStage ? '保存阶段' : '添加阶段' }}</button><button v-if="editingStage" class="btn" type="button" @click="resetStage">取消编辑</button></div></form></div>
  </AppSheet>
</div></template>
<style scoped>
.route-content { min-width: 0; }
.mainline-step.locked { opacity: 1; }
.mainline-step.locked .step-number { color: var(--muted); }
.route-create summary { cursor: pointer; padding: 16px; font-weight: 700; color: var(--blue); border-top: 1px solid var(--line); }
.route-create form { border-top: 1px solid var(--line); }
.line-list input[type="color"] { width: 48px; flex: none; min-height: 44px; padding: 4px; }
.route-filters { display: flex; align-items: end; flex-wrap: wrap; gap: 16px; padding: 20px; border-bottom: 1px solid var(--line, #d8d8df); }
.route-filters > label:first-child { flex: 1; min-width: 180px; }
.check-row { display: flex; align-items: center; gap: 10px; min-height: 44px; }
.check-row input { width: auto; }
.route-stage { border-bottom: 1px solid var(--line, #d8d8df); }
.route-stage-header { padding: 20px 20px 12px; }
.route-stage-toggle { display: flex; width: 100%; justify-content: space-between; gap: 16px; background: transparent; border: 0; padding: 8px 0; text-align: left; cursor: pointer; color: inherit; }
.route-stage-toggle strong { font-size: 20px; }
.route-stage-toggle span { color: #686b76; }
.route-step-body { min-width: 0; overflow-wrap: anywhere; }
.route-pagination { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; padding: 16px 0; }
.route-editor { display: grid; gap: 20px; }
.route-field { display: grid; gap: 8px; }
.route-editor fieldset { display: grid; gap: 16px; margin: 0; padding: 20px; border: 1px solid #d8d8df; min-width: 0; }
.route-prerequisites { display: grid; gap: 12px; max-height: 260px; overflow-y: auto; }
.route-stage-row { padding-bottom: 16px; border-bottom: 1px solid #d8d8df; display: grid; gap: 12px; }
@media (max-width: 720px) { .route-stage-toggle { flex-direction: column; gap: 4px; } .route-filters > label:first-child { flex-basis: 100%; } .mainline-step > .button-row { grid-column: 1 / -1; } }
</style>
