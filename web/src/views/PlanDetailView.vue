<script setup lang="ts">
import ColorPicker from '@/components/ColorPicker.vue';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, apiMessage, OfflineQueuedError } from '@/api/client';
import LocalBadge from '@/components/LocalBadge.vue';
import RestDaysPicker from '@/components/RestDaysPicker.vue';
import AppSheet from '@/components/AppSheet.vue';
import { restDaySummary } from '@/restDays';
import type { Plan, Tag, Task } from '@/types';

const route = useRoute();
const router = useRouter();
const planId = String(route.params.id);
const plan = ref<Plan | null>(null);
const tags = ref<Tag[]>([]);
const tasks = ref<Task[]>([]);
const error = ref('');
const loading = ref(false);
const editingPlan = ref(false);
const savingPlan = ref(false);
const planDraft = reactive({ name: '', description: '', startDate: '', endDate: '', restDays: [] as number[] });
const editingTask = ref<Task | null>(null);
const taskEditorOpen = ref(false); const savingTask = ref(false);
const taskQuery = ref(''); const taskFilter = ref('ALL'); const taskPage = ref(0); const pageSize = 10;
const editingTag = ref<Tag | null>(null);
const tagDraft = reactive({ name: '', color: '#002FA7' });
const taskDraft = reactive({ tagId: '', date: new Date().toLocaleDateString('sv-SE'), startTime: '09:00', endTime: '10:00', plannedMinutes: 60, category: 'CUSTOM', title: '', description: '', acceptanceText: '完成验收', resourceUrl: '', required: true, sortOrder: 0 });
const sortedTasks = computed(() => [...tasks.value].sort((a, b) => a.date.localeCompare(b.date) || a.sortOrder - b.sortOrder));
const filteredTasks = computed(() => sortedTasks.value.filter(task =>
  (taskFilter.value === 'ALL' || (taskFilter.value === 'COMPLETED' ? task.progress.status === 'COMPLETED' : task.progress.status !== 'COMPLETED')) &&
  `${task.title} ${task.description}`.toLowerCase().includes(taskQuery.value.trim().toLowerCase())));
const taskPages = computed(() => Math.max(1, Math.ceil(filteredTasks.value.length / pageSize)));
const visibleTasks = computed(() => filteredTasks.value.slice(taskPage.value * pageSize, (taskPage.value + 1) * pageSize));
watch([taskQuery, taskFilter], () => { taskPage.value = 0; });
watch(taskPages, count => { taskPage.value = Math.min(taskPage.value, count - 1); });
function openNewTask() { resetTask(); taskEditorOpen.value = true; }
function closeTaskEditor() { if (!savingTask.value) { taskEditorOpen.value = false; resetTask(); } }

function resetTask() {
  editingTask.value = null;
  Object.assign(taskDraft, { tagId: tags.value.find(tag => !tag.overall)?.id ?? tags.value[0]?.id ?? '', date: plan.value?.startDate ?? new Date().toLocaleDateString('sv-SE'), startTime: '09:00', endTime: '10:00', plannedMinutes: 60, category: 'CUSTOM', title: '', description: '', acceptanceText: '完成验收', resourceUrl: '', required: true, sortOrder: 0 });
}
function resetTag() { editingTag.value = null; Object.assign(tagDraft, { name: '', color: '#002FA7' }); }
async function load() {
  loading.value = true;
  try {
  const [plansResponse, tagsResponse, tasksResponse] = await Promise.all([api.get<Plan[]>('/plans'), api.get<Tag[]>(`/plans/${planId}/tags`), api.get<Task[]>(`/plans/${planId}/tasks`)]);
  plan.value = plansResponse.data.find(item => item.id === planId) ?? null;
  tags.value = tagsResponse.data; tasks.value = tasksResponse.data;
  if (!editingTask.value && !taskDraft.tagId) taskDraft.tagId = tags.value.find(tag => !tag.overall)?.id ?? tags.value[0]?.id ?? '';
  } finally { loading.value = false; }
}
function editPlan() {
  if (!plan.value || plan.value.localState) return;
  Object.assign(planDraft, { name: plan.value.name, description: plan.value.description, startDate: plan.value.startDate, endDate: plan.value.endDate, restDays: [...(plan.value.restDays ?? [])] });
  editingPlan.value = true;
}
async function savePlan() {
  if (!plan.value || savingPlan.value) return;
  if (plan.value.restDays === undefined) { error.value = '当前服务器尚未支持休息日配置，请先升级服务器。'; return; }
  savingPlan.value = true; error.value = '';
  try {
    await api.put(`/plans/${planId}`, { ...planDraft, version: plan.value.version });
    editingPlan.value = false; await load();
  } catch (exception) {
    if (exception instanceof OfflineQueuedError) { editingPlan.value = false; await load().catch(failure => { error.value = apiMessage(failure); }); }
    else error.value = apiMessage(exception);
  } finally { savingPlan.value = false; }
}
async function saveTag() {
  error.value = '';
  try {
    if (editingTag.value) await api.put(`/tags/${editingTag.value.id}`, { ...tagDraft, version: editingTag.value.version });
    else await api.post(`/plans/${planId}/tags`, tagDraft);
    resetTag(); await load();
  } catch (exception) { if (exception instanceof OfflineQueuedError) { resetTag(); await load(); } else error.value = apiMessage(exception); }
}
function editTag(tag: Tag) { if (tag.localState) { error.value = '请先同步此标签，再继续编辑。'; return; } editingTag.value = tag; Object.assign(tagDraft, { name: tag.name, color: tag.color }); }
async function removeTag(tag: Tag) {
  if (tag.overall || !confirm(`删除标签“${tag.name}”？`)) return;
  try { await api.delete(`/tags/${tag.id}`); await load(); } catch (exception) { error.value = apiMessage(exception); }
}
function taskPayload(version?: number) { return { ...taskDraft, tagId: taskDraft.tagId || null, acceptance: taskDraft.acceptanceText.split('\n').map(value => value.trim()).filter(Boolean), version }; }
async function saveTask() {
  if (savingTask.value) return;
  savingTask.value = true;
  error.value = '';
  try {
    if (editingTask.value) await api.put(`/tasks/${editingTask.value.id}`, taskPayload(editingTask.value.version));
    else await api.post(`/plans/${planId}/tasks`, taskPayload());
    await load(); resetTask(); taskEditorOpen.value = false;
  } catch (exception) { if (exception instanceof OfflineQueuedError) { await load(); resetTask(); taskEditorOpen.value = false; } else error.value = apiMessage(exception); }
  finally { savingTask.value = false; }
}
function editTask(task: Task) {
  if (task.localOnly || task.localState) { error.value = '此任务已有本地操作，请先同步或处理冲突，再继续编辑。'; return; }
  editingTask.value = task;
  taskEditorOpen.value = true;
  Object.assign(taskDraft, { tagId: task.tagId ?? '', date: task.date, startTime: task.startTime?.slice(0, 5) ?? '', endTime: task.endTime?.slice(0, 5) ?? '', plannedMinutes: task.plannedMinutes, category: task.category, title: task.title, description: task.description, acceptanceText: task.acceptance.join('\n'), resourceUrl: task.resourceUrl, required: task.required, sortOrder: task.sortOrder });
}
async function removeTask(task: Task) {
  if (!confirm(`删除任务“${task.title}”？`)) return;
  try { await api.delete(`/tasks/${task.id}`); if (editingTask.value?.id === task.id) resetTask(); await load(); } catch (exception) { error.value = apiMessage(exception); }
}
async function removePlan() {
  if (!plan.value || !confirm(`将计划“${plan.value.name}”移入回收站？任务、标签和里程碑会随计划保留，可在“记录”中恢复。`)) return;
  try { await api.delete(`/plans/${planId}`); await router.push('/plans'); } catch (exception) { error.value = apiMessage(exception); }
}
onMounted(() => load().then(resetTask).catch(exception => error.value = apiMessage(exception)));
</script>

<template>
  <div class="page plan-detail-page">
    <router-link v-if="plan && !plan.localState" class="btn" :to="{ path: '/ai', query: { mode: 'PLAN', target: planId } }">使用 AI 调整此周期计划</router-link>
    <section class="page-head"><div><p class="eyebrow">PLAN / DETAIL</p><h1>{{ plan?.name ?? (loading ? '读取计划中…' : '计划暂不可用') }}</h1><p v-if="plan" class="helper-text">{{ restDaySummary(plan.restDays) }}</p><LocalBadge :state="plan?.localState"/></div><div><p>{{ plan?.startDate }}—{{ plan?.endDate }} · {{ tasks.length }} 项任务</p><div class="button-row"><button class="btn" type="button" :disabled="!plan || !!plan.localState || loading || savingPlan" @click="editPlan">编辑计划设置</button><router-link class="btn" :to="`/plans/${planId}/studio`">阶段与模板</router-link><button class="btn danger" type="button" @click="removePlan">移入回收站</button></div></div></section>
    <p v-if="error" class="error-text">{{ error }}</p>
    <form v-if="editingPlan" class="editor-card" aria-label="编辑计划设置" @submit.prevent="savePlan">
      <h2>编辑计划设置</h2>
      <label>计划名称<input v-model="planDraft.name" required maxlength="120" /></label><label>说明<textarea v-model="planDraft.description" maxlength="1000" rows="2" /></label>
      <div class="form-grid"><label>开始日期<input v-model="planDraft.startDate" type="date" required /></label><label>结束日期<input v-model="planDraft.endDate" type="date" required /></label></div>
      <RestDaysPicker v-model="planDraft.restDays" :disabled="savingPlan || plan?.restDays === undefined" />
      <p class="helper-text">修改休息日只调整日程标记，不移动或删除已有任务；离线保存需到同步中心提交。</p>
      <div class="button-row"><button class="btn primary" :disabled="savingPlan || plan?.restDays === undefined">{{ savingPlan ? '保存中…' : '保存计划设置' }}</button><button class="btn" type="button" :disabled="savingPlan" @click="editingPlan = false">取消</button></div>
    </form>
    <section class="tag-strip" aria-label="计划标签">
      <span v-for="tag in tags" :key="tag.id" class="tag-tool"><span class="tag" :style="{ color: tag.color, borderColor: tag.color }">{{ tag.name }}{{ tag.overall ? ' · 总计' : '' }}</span><LocalBadge :state="tag.localState"/><button class="text-btn" type="button" :disabled="!!tag.localState" @click="editTag(tag)">编辑</button><button v-if="!tag.overall" class="text-btn danger-text" type="button" :disabled="!!tag.localState" @click="removeTag(tag)">删除</button></span>
      <form class="inline-form tag-editor" @submit.prevent="saveTag"><input v-model="tagDraft.name" :placeholder="editingTag ? '修改标签名称' : '新标签名称'" required /><ColorPicker v-model="tagDraft.color" label="标签颜色" /><button class="btn">{{ editingTag ? '保存标签' : '添加标签' }}</button><button v-if="editingTag" class="btn" type="button" @click="resetTag">取消</button></form>
    </section>
    <div class="plan-task-layout">
      <section class="section">
        <header class="section-title"><div><h2>计划任务</h2><p class="helper-text">{{ tasks.length }} 项任务</p></div><button class="btn primary" type="button" @click="openNewTask">添加任务</button></header>
        <div class="task-list-controls"><label>搜索任务<input v-model="taskQuery" type="search" placeholder="标题或任务内容"/></label><label>任务状态<select v-model="taskFilter"><option value="ALL">全部任务</option><option value="ACTIVE">未完成</option><option value="COMPLETED">已完成</option></select></label></div>
        <article v-for="task in visibleTasks" :key="task.id" class="task-row compact" :class="{ 'task-row--editing': editingTask?.id === task.id }">
          <div class="date-mini"><strong>{{ task.date.slice(5) }}</strong><span>{{ task.startTime?.slice(0, 5) }}</span></div>
          <div class="task-copy"><span class="tag" :style="{ color: task.tagColor ?? '#002FA7', borderColor: task.tagColor ?? '#002FA7' }">{{ task.tagName ?? '未分类' }}</span><h3>{{ task.title }}</h3><p>{{ task.description || '暂无任务说明' }}</p></div>
          <div class="task-state"><strong>{{ task.progress.status }}</strong><span>{{ task.plannedMinutes }} 分钟</span><LocalBadge :state="task.localState"/></div>
          <div class="button-row"><button class="btn" :disabled="!!task.localState" @click="editTask(task)">编辑</button><button class="btn danger" :disabled="task.localOnly || !!task.localState" @click="removeTask(task)">删除</button></div>
        </article>
        <p v-if="!tasks.length" class="empty">点击“添加任务”，安排第一项任务。</p>
        <p v-else-if="!filteredTasks.length" class="empty">没有匹配的任务，试试其他关键词或状态。</p>
        <div v-if="filteredTasks.length" class="pagination-bar" aria-label="任务分页"><span>{{ taskPage + 1 }} / {{ taskPages }} 页 · {{ filteredTasks.length }} 项</span><div class="button-row"><button type="button" class="btn" :disabled="taskPage === 0" @click="taskPage--">上一页</button><button type="button" class="btn" :disabled="taskPage + 1 >= taskPages" @click="taskPage++">下一页</button></div></div>
      </section>
    </div>
    <AppSheet :open="taskEditorOpen" :title="editingTask ? '编辑任务' : '添加任务'" title-id="task-editor-title" :busy="savingTask" @close="closeTaskEditor">
      <form class="sheet-form" @submit.prevent="saveTask">
        <p v-if="editingTask" class="helper-text">任务状态与已记录用时会被保留。</p>
        <p v-if="error" class="error-text" role="alert">{{ error }}</p>
        <label>任务标题<input v-model="taskDraft.title" required /></label><label>任务内容<textarea v-model="taskDraft.description" rows="3" /></label>
        <div class="form-grid"><label>日期<input v-model="taskDraft.date" type="date" :min="plan?.startDate" :max="plan?.endDate" required /></label><label>标签<select v-model="taskDraft.tagId"><option value="">未分类</option><option v-for="tag in tags.filter(tag => !tag.localOnly)" :key="tag.id" :value="tag.id">{{ tag.name }}</option></select></label></div>
        <div class="form-grid"><label>开始<input v-model="taskDraft.startTime" type="time" /></label><label>结束<input v-model="taskDraft.endTime" type="time" /></label></div>
        <div class="form-grid"><label>分类<input v-model="taskDraft.category" maxlength="40" required /></label><label>排序<input v-model.number="taskDraft.sortOrder" type="number" min="0" required /></label></div>
        <label>计划分钟<input v-model.number="taskDraft.plannedMinutes" type="number" min="1" max="1440" required /></label><label>验收标准（每行一项）<textarea v-model="taskDraft.acceptanceText" rows="3" /></label><label>资料链接<input v-model="taskDraft.resourceUrl" type="url" /></label><label class="check"><input v-model="taskDraft.required" type="checkbox" />计入核心完成率</label>
        <div class="button-row sheet-actions"><button class="btn" type="button" :disabled="savingTask" @click="closeTaskEditor">取消编辑</button><button class="btn primary" :disabled="savingTask">{{ savingTask ? '保存中…' : editingTask ? '保存修改' : '保存任务' }}</button></div>
      </form>
    </AppSheet>
  </div>
</template>
<style scoped>
.tag-editor{display:grid;grid-template-columns:minmax(140px,1fr) minmax(260px,340px) auto auto;gap:14px;width:100%;align-items:start}.tag-editor>.btn{grid-column:auto}.tag-editor>input{width:100%;min-width:0}@media(max-width:900px){.tag-editor{grid-template-columns:minmax(0,1fr);width:100%}.tag-editor :deep(.color-picker){grid-column:1/-1;width:100%;max-width:360px}.tag-editor>.btn{grid-column:1/-1}}
</style>
