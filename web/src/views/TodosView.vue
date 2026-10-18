<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { api, apiMessage, OfflineQueuedError } from '@/api/client';
import { localDate, shiftDate } from '@/todos';
import type { Todo } from '@/types';
import AppSheet from '@/components/AppSheet.vue';
import LocalBadge from '@/components/LocalBadge.vue';

const route=useRoute();
const date = ref(typeof route.query.date==='string' && /^\d{4}-\d{2}-\d{2}$/.test(route.query.date) ? route.query.date : localDate()); const range = ref('DAY'); const filter = ref('OPEN'); const query = ref('');
const rows = ref<Todo[]>([]); const loading = ref(false); const busy = ref(false); const error = ref('');
const quickTitle = ref(''); const page = ref(0); const pageSize = 10;
const editorOpen = ref(false); const editing = ref<Todo | null>(null);
const draft = reactive({ title: '', note: '', dueDate: localDate(), done: false });
let requestNumber = 0;
let pendingFocus = typeof route.query.focus==='string' ? route.query.focus : '';
const params = computed(() => ({ from: range.value === 'DAY' ? date.value : shiftDate(date.value, -30), to: date.value, deleted: range.value === 'TRASH' }));
const filtered = computed(() => rows.value.filter(row =>
  (filter.value === 'ALL' || (filter.value === 'DONE' ? row.done : !row.done)) &&
  `${row.title} ${row.note}`.toLowerCase().includes(query.value.trim().toLowerCase())));
const pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)));
const visible = computed(() => filtered.value.slice(page.value * pageSize, (page.value + 1) * pageSize));
watch([filter, query], () => { page.value = 0; });
watch(pages, count => { page.value = Math.min(page.value, count - 1); });
async function load() {
  const request = ++requestNumber; loading.value = true; error.value = '';
  try { const data = (await api.get<Todo[]>('/todos', { params: params.value })).data; if (request === requestNumber) { rows.value = data; const focus=data.find(item=>item.id===pendingFocus); if(focus && !editorOpen.value){pendingFocus='';openEditor(focus);} } }
  catch (e) { if (request === requestNumber) error.value = apiMessage(e); }
  finally { if (request === requestNumber) loading.value = false; }
}
watch([date, range], () => { page.value = 0; rows.value = []; void load(); }, { immediate: true });
async function mutate(action: () => Promise<unknown>): Promise<boolean> {
  if (busy.value) return false;
  busy.value = true; error.value = '';
  try { await action(); await load(); return true; }
  catch (e) {
    if (e instanceof OfflineQueuedError) { await load(); return true; }
    error.value = apiMessage(e); return false;
  } finally { busy.value = false; }
}
async function quickAdd() {
  if (!quickTitle.value.trim() || range.value === 'TRASH') return;
  if (await mutate(() => api.post('/todos', { title: quickTitle.value.trim(), note: '', dueDate: date.value }))) quickTitle.value = '';
}
function openEditor(row?: Todo) {
  if (row?.localState || row?.localOnly) return;
  editing.value = row ?? null;
  Object.assign(draft, row ? { title: row.title, note: row.note, dueDate: row.dueDate, done: row.done } : { title: '', note: '', dueDate: date.value, done: false });
  editorOpen.value = true;
}
async function save() {
  const row = editing.value;
  if (await mutate(() => row ? api.put(`/todos/${row.id}`, { ...draft, deleted: false, version: row.version }) : api.post('/todos', draft))) editorOpen.value = false;
}
async function update(row: Todo, change: Partial<Todo>) {
  if (row.localOnly || row.localState) return false;
  return mutate(() => api.put(`/todos/${row.id}`, { title: row.title, note: row.note, dueDate: row.dueDate, done: row.done, deleted: row.deleted, version: row.version, ...change }));
}
async function toggle(row: Todo, event: Event) {
  const checkbox = event.target as HTMLInputElement;
  const done = checkbox.checked;
  // Revert the native checkbox if neither the server nor the offline queue accepted it.
  if (!await update(row, { done })) checkbox.checked = row.done;
}
async function remove(row: Todo) { if (confirm(`将“${row.title}”移入待办回收站？`)) await update(row, { deleted: true }); }
</script>
<template><div class="page todos-page">
  <router-link class="btn" to="/organize">查询更早的待办历史</router-link>
  <router-link class="btn" to="/ai?mode=TODO">使用 AI 构建今日待办</router-link>
  <header class="page-head"><div><h1>今日待办</h1><p>临时想到的事，记下来就可以开始。不需要创建计划或路线。</p></div><button class="btn primary" :disabled="busy || range === 'TRASH'" @click="openEditor()">添加待办</button></header>
  <section class="section">
    <div class="todo-controls"><label>查看日期<input v-model="date" type="date" required/></label><label>日期范围<select v-model="range" aria-label="日期范围"><option value="DAY">当天</option><option value="RECENT">截至此日的近 31 天</option><option value="TRASH">近 31 天回收站</option></select></label><label>状态<select v-model="filter" aria-label="状态"><option value="OPEN">未完成</option><option value="DONE">已完成</option><option value="ALL">全部</option></select></label><label>搜索<input v-model="query" type="search" placeholder="标题或备注"/></label></div>
    <form v-if="range !== 'TRASH'" class="todo-quick" @submit.prevent="quickAdd"><label class="todo-quick-title">快速记录<input v-model="quickTitle" maxlength="180" placeholder="今天需要做什么？" required/></label><button class="btn primary" :disabled="busy || !quickTitle.trim()">记一件事</button></form>
    <p v-if="error" class="error-text" role="alert">{{ error }}</p><p v-if="loading" class="loading">正在读取待办…</p>
    <p class="helper-text">{{ rows.filter(row => row.done).length }} / {{ rows.length }} 项完成 · 每页 10 项。离线操作保存在当前设备，联网后到同步中心提交。</p>
    <article v-for="row in visible" :key="row.id" class="todo-row" :class="{ 'todo-row--done': row.done }">
      <input v-if="!row.deleted" type="checkbox" :checked="row.done" :aria-label="`完成 ${row.title}`" :disabled="busy || row.localOnly || !!row.localState" @change="toggle(row, $event)"/>
      <div class="todo-copy"><h2>{{ row.title }}</h2><p v-if="row.note">{{ row.note }}</p><small>{{ row.dueDate }} · {{ row.done ? '已完成' : '未完成' }}</small><LocalBadge :state="row.localState"/></div>
      <div class="button-row"><template v-if="!row.deleted"><button class="btn" :disabled="busy || row.localOnly || !!row.localState" @click="openEditor(row)">编辑</button><router-link v-if="!row.done && !row.localOnly && !row.localState" class="btn" :to="{ path: '/ai', query: { mode: 'TODO', target: row.id } }">AI 调整</router-link><button class="btn danger" :disabled="busy || row.localOnly || !!row.localState" @click="remove(row)">删除</button></template><button v-else class="btn" :disabled="busy || !!row.localState" @click="update(row, { deleted: false })">恢复待办</button></div>
    </article>
    <p v-if="!loading && !filtered.length && !error" class="empty">{{ range === 'TRASH' ? '此日期范围没有已删除的待办。' : '没有匹配的待办。可以随手记一件事，或切换状态与日期查看。' }}</p>
    <div v-if="filtered.length" class="pagination-bar" aria-label="待办分页"><span>{{ page + 1 }} / {{ pages }} 页 · {{ filtered.length }} 项</span><div class="button-row"><button class="btn" :disabled="page === 0" @click="page--">上一页</button><button class="btn" :disabled="page + 1 >= pages" @click="page++">下一页</button></div></div>
  </section>
  <AppSheet :open="editorOpen" :title="editing ? '编辑待办' : '添加待办'" title-id="todo-editor-title" :busy="busy" @close="editorOpen = false"><form class="sheet-form" @submit.prevent="save"><p v-if="error" class="error-text" role="alert">{{ error }}</p><label>待办标题<input v-model="draft.title" required maxlength="180"/></label><label>安排日期<input v-model="draft.dueDate" type="date" required/></label><label>备注<textarea v-model="draft.note" maxlength="2000" rows="4"/></label><label v-if="editing" class="check"><input v-model="draft.done" type="checkbox"/>已完成</label><div class="button-row sheet-actions"><button type="button" class="btn" :disabled="busy" @click="editorOpen = false">取消</button><button class="btn primary" :disabled="busy || !draft.title.trim()">{{ busy ? '保存中…' : '保存待办' }}</button></div></form></AppSheet>
</div></template>
<style scoped>
.todos-page .section{padding:20px}.todo-controls{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.todo-controls label,.todo-quick-title{display:grid;gap:8px;font-size:14px;color:#667085}.todo-quick{display:flex;align-items:end;gap:16px;border-bottom:1px solid #ddd;padding:24px 0}.todo-quick-title{flex:1}.todo-row{display:flex;align-items:center;gap:20px;border-bottom:1px solid #ddd;padding:24px 0}.todo-row>input{width:24px;height:24px;min-height:24px;flex-shrink:0;accent-color:#002fa7}.todo-copy{min-width:0;flex:1}.todo-copy h2{font-size:19px;overflow-wrap:anywhere;margin:0 0 8px}.todo-copy p{white-space:pre-wrap;overflow-wrap:anywhere}.todo-row--done h2{text-decoration:line-through;color:#666}.todo-copy small{color:#666}.todo-copy .local-badge{margin-left:12px}
@media(max-width:700px){.todo-controls{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}.todo-controls>label{min-width:0}.todo-controls>label:first-child,.todo-controls>label:last-child{grid-column:1/-1}.todo-controls input,.todo-controls select{min-width:0;width:100%;max-width:100%}.todo-controls input[type=date]{min-height:48px;font-variant-numeric:tabular-nums}.todo-quick{gap:10px}.todo-row{display:grid;grid-template-columns:24px minmax(0,1fr);gap:14px}.todo-row>input{align-self:start;margin-top:3px}.todo-copy{grid-column:2}.todo-row>.button-row{grid-column:2;flex-wrap:wrap}.todo-copy h2{font-size:17px}}
</style>
