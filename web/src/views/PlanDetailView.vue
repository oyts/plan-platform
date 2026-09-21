<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, apiMessage } from '@/api/client';
import type { Plan, Tag, Task } from '@/types';

const route = useRoute();
const router = useRouter();
const planId = String(route.params.id);
const plan = ref<Plan | null>(null);
const tags = ref<Tag[]>([]);
const tasks = ref<Task[]>([]);
const error = ref('');
const editingTask = ref<Task | null>(null);
const editingTag = ref<Tag | null>(null);
const tagDraft = reactive({ name: '', color: '#002FA7' });
const taskDraft = reactive({ tagId: '', date: new Date().toLocaleDateString('sv-SE'), startTime: '09:00', endTime: '10:00', plannedMinutes: 60, category: 'CUSTOM', title: '', description: '', acceptanceText: '完成验收', resourceUrl: '', required: true, sortOrder: 0 });
const sortedTasks = computed(() => [...tasks.value].sort((a, b) => a.date.localeCompare(b.date) || a.sortOrder - b.sortOrder));

function resetTask() {
  editingTask.value = null;
  Object.assign(taskDraft, { tagId: tags.value.find(tag => !tag.overall)?.id ?? tags.value[0]?.id ?? '', date: plan.value?.startDate ?? new Date().toLocaleDateString('sv-SE'), startTime: '09:00', endTime: '10:00', plannedMinutes: 60, category: 'CUSTOM', title: '', description: '', acceptanceText: '完成验收', resourceUrl: '', required: true, sortOrder: 0 });
}
function resetTag() { editingTag.value = null; Object.assign(tagDraft, { name: '', color: '#002FA7' }); }
async function load() {
  const [plansResponse, tagsResponse, tasksResponse] = await Promise.all([api.get<Plan[]>('/plans'), api.get<Tag[]>(`/plans/${planId}/tags`), api.get<Task[]>(`/plans/${planId}/tasks`)]);
  plan.value = plansResponse.data.find(item => item.id === planId) ?? null;
  tags.value = tagsResponse.data; tasks.value = tasksResponse.data;
  if (!editingTask.value && !taskDraft.tagId) taskDraft.tagId = tags.value.find(tag => !tag.overall)?.id ?? tags.value[0]?.id ?? '';
}
async function saveTag() {
  try {
    if (editingTag.value) await api.put(`/tags/${editingTag.value.id}`, { ...tagDraft, version: editingTag.value.version });
    else await api.post(`/plans/${planId}/tags`, tagDraft);
    resetTag(); await load();
  } catch (exception) { error.value = apiMessage(exception); }
}
function editTag(tag: Tag) { editingTag.value = tag; Object.assign(tagDraft, { name: tag.name, color: tag.color }); }
async function removeTag(tag: Tag) {
  if (tag.overall || !confirm(`删除标签“${tag.name}”？`)) return;
  try { await api.delete(`/tags/${tag.id}`); await load(); } catch (exception) { error.value = apiMessage(exception); }
}
function taskPayload(version?: number) { return { ...taskDraft, tagId: taskDraft.tagId || null, acceptance: taskDraft.acceptanceText.split('\n').map(value => value.trim()).filter(Boolean), version }; }
async function saveTask() {
  try {
    if (editingTask.value) await api.put(`/tasks/${editingTask.value.id}`, taskPayload(editingTask.value.version));
    else await api.post(`/plans/${planId}/tasks`, taskPayload());
    await load(); resetTask();
  } catch (exception) { error.value = apiMessage(exception); }
}
function editTask(task: Task) {
  editingTask.value = task;
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
  <div class="page">
    <section class="page-head"><div><p class="eyebrow">PLAN / DETAIL</p><h1>{{ plan?.name ?? '读取计划中…' }}</h1></div><div><p>{{ plan?.startDate }}—{{ plan?.endDate }} · {{ tasks.length }} 项任务</p><div class="button-row"><router-link class="btn" :to="`/plans/${planId}/studio`">阶段与模板</router-link><button class="btn danger" type="button" @click="removePlan">移入回收站</button></div></div></section>
    <p v-if="error" class="error-text">{{ error }}</p>
    <section class="tag-strip" aria-label="计划标签">
      <span v-for="tag in tags" :key="tag.id" class="tag-tool"><span class="tag" :style="{ color: tag.color, borderColor: tag.color }">{{ tag.name }}{{ tag.overall ? ' · 总计' : '' }}</span><button class="text-btn" type="button" @click="editTag(tag)">编辑</button><button v-if="!tag.overall" class="text-btn danger-text" type="button" @click="removeTag(tag)">删除</button></span>
      <form class="inline-form tag-editor" @submit.prevent="saveTag"><input v-model="tagDraft.name" :placeholder="editingTag ? '修改标签名称' : '新标签名称'" required /><input v-model="tagDraft.color" type="color" /><button class="btn">{{ editingTag ? '保存标签' : '添加标签' }}</button><button v-if="editingTag" class="btn" type="button" @click="resetTag">取消</button></form>
    </section>
    <div class="split-layout split-layout--wide">
      <section class="section">
        <header class="section-title"><div><p class="eyebrow">TASK LEDGER</p><h2>计划任务</h2></div><span>{{ tasks.length }}</span></header>
        <article v-for="task in sortedTasks" :key="task.id" class="task-row compact" :class="{ 'task-row--editing': editingTask?.id === task.id }">
          <div class="date-mini"><strong>{{ task.date.slice(5) }}</strong><span>{{ task.startTime?.slice(0, 5) }}</span></div>
          <div class="task-copy"><span class="tag" :style="{ color: task.tagColor ?? '#002FA7', borderColor: task.tagColor ?? '#002FA7' }">{{ task.tagName ?? '未分类' }}</span><h3>{{ task.title }}</h3><p>{{ task.description || '暂无任务说明' }}</p></div>
          <div class="task-state"><strong>{{ task.progress.status }}</strong><span>{{ task.plannedMinutes }} 分钟</span></div>
          <div class="button-row"><button class="btn" @click="editTask(task)">编辑</button><button class="btn danger" @click="removeTask(task)">删除</button></div>
        </article>
        <p v-if="!tasks.length" class="empty">使用右侧表单添加第一项任务。</p>
      </section>
      <form class="editor-card sticky" @submit.prevent="saveTask">
        <p class="eyebrow">{{ editingTask ? 'EDIT TASK' : 'NEW TASK' }}</p><h2>{{ editingTask ? '编辑任务' : '添加任务' }}</h2><p v-if="editingTask" class="helper-text">编辑保存到服务器后，任务状态与已记录用时会被保留。</p>
        <label>任务标题<input v-model="taskDraft.title" required /></label><label>任务内容<textarea v-model="taskDraft.description" rows="3" /></label>
        <div class="form-grid"><label>日期<input v-model="taskDraft.date" type="date" :min="plan?.startDate" :max="plan?.endDate" required /></label><label>标签<select v-model="taskDraft.tagId"><option value="">未分类</option><option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option></select></label></div>
        <div class="form-grid"><label>开始<input v-model="taskDraft.startTime" type="time" /></label><label>结束<input v-model="taskDraft.endTime" type="time" /></label></div>
        <div class="form-grid"><label>分类<input v-model="taskDraft.category" maxlength="40" required /></label><label>排序<input v-model.number="taskDraft.sortOrder" type="number" min="0" required /></label></div>
        <label>计划分钟<input v-model.number="taskDraft.plannedMinutes" type="number" min="1" max="1440" required /></label><label>验收标准（每行一项）<textarea v-model="taskDraft.acceptanceText" rows="3" /></label><label>资料链接<input v-model="taskDraft.resourceUrl" type="url" /></label><label class="check"><input v-model="taskDraft.required" type="checkbox" />计入核心完成率</label>
        <div class="button-row"><button class="btn primary">{{ editingTask ? '保存修改' : '保存任务' }}</button><button v-if="editingTask" class="btn" type="button" @click="resetTask">取消编辑</button></div>
      </form>
    </div>
  </div>
</template>
