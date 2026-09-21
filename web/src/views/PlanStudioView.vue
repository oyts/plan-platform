<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { api, apiMessage } from '@/api/client';
import type { Milestone, Plan, RecurrenceBatch, Tag, TaskTemplate } from '@/types';

const route = useRoute();
const planId = String(route.params.id);
const plan = ref<Plan | null>(null);
const tags = ref<Tag[]>([]);
const templates = ref<TaskTemplate[]>([]);
const milestones = ref<Milestone[]>([]);
const recurrences = ref<RecurrenceBatch[]>([]);
const error = ref('');
const message = ref('');
const editingTemplate = ref<TaskTemplate | null>(null);
const editingMilestone = ref<Milestone | null>(null);
const templateDraft = reactive({ name: '', category: 'CUSTOM', title: '', description: '', plannedMinutes: 60, acceptanceText: '', resourceUrl: '', required: true });
const milestoneDraft = reactive({ name: '', date: '', description: '', acceptanceText: '' });
const instantiate = reactive({ templateId: '', tagId: '', date: '', startTime: '09:00', endTime: '10:00', sortOrder: 0, mode: 'ONCE', until: '' });
const milestoneChecks = reactive<Record<string, boolean[]>>({});
const milestoneNotes = reactive<Record<string, string>>({});
const stopFrom = reactive<Record<string, string>>({});
const selectedTemplate = computed(() => templates.value.find(item => item.id === instantiate.templateId) ?? null);

async function load() {
  const [plansResponse, tagsResponse, templatesResponse, milestonesResponse, recurrencesResponse] = await Promise.all([
    api.get<Plan[]>('/plans'), api.get<Tag[]>(`/plans/${planId}/tags`), api.get<TaskTemplate[]>('/templates'), api.get<Milestone[]>(`/plans/${planId}/milestones`), api.get<RecurrenceBatch[]>(`/plans/${planId}/recurrences`),
  ]);
  plan.value = plansResponse.data.find(item => item.id === planId) ?? null;
  tags.value = tagsResponse.data; templates.value = templatesResponse.data; milestones.value = milestonesResponse.data; recurrences.value = recurrencesResponse.data;
  if (!instantiate.tagId) instantiate.tagId = tags.value.find(tag => !tag.overall)?.id ?? tags.value[0]?.id ?? '';
  if (!instantiate.date) instantiate.date = plan.value?.startDate ?? new Date().toLocaleDateString('sv-SE');
  if (!instantiate.until) instantiate.until = plan.value?.endDate ?? instantiate.date;
  if (!instantiate.templateId) instantiate.templateId = templates.value[0]?.id ?? '';
  for (const milestone of milestones.value) {
    milestoneChecks[milestone.id] = milestone.acceptance.map((_, index) => milestone.checks[index] ?? false);
    milestoneNotes[milestone.id] = milestone.note ?? '';
  }
  for (const batch of recurrences.value) stopFrom[batch.recurrenceId] = stopFrom[batch.recurrenceId] ?? batch.firstDate;
}
function resetTemplate() { editingTemplate.value = null; Object.assign(templateDraft, { name: '', category: 'CUSTOM', title: '', description: '', plannedMinutes: 60, acceptanceText: '', resourceUrl: '', required: true }); }
function editTemplate(template: TaskTemplate) { editingTemplate.value = template; Object.assign(templateDraft, { name: template.name, category: template.category, title: template.title, description: template.description, plannedMinutes: template.plannedMinutes, acceptanceText: template.acceptance.join('\n'), resourceUrl: template.resourceUrl, required: template.required }); }
function templatePayload(version?: number) { return { ...templateDraft, acceptance: templateDraft.acceptanceText.split('\n').map(item => item.trim()).filter(Boolean), version }; }
async function saveTemplate() {
  try {
    if (editingTemplate.value) await api.put(`/templates/${editingTemplate.value.id}`, templatePayload(editingTemplate.value.version));
    else await api.post('/templates', templatePayload());
    message.value = '任务模板已保存。'; resetTemplate(); await load();
  } catch (exception) { error.value = apiMessage(exception); }
}
async function deleteTemplate(template: TaskTemplate) { if (!confirm(`删除模板“${template.name}”？已生成的任务不会删除。`)) return; try { await api.delete(`/templates/${template.id}`); if (instantiate.templateId === template.id) instantiate.templateId = ''; await load(); } catch (exception) { error.value = apiMessage(exception); } }
async function createFromTemplate() {
  if (!selectedTemplate.value) return;
  error.value = ''; message.value = '';
  const base = { tagId: instantiate.tagId || null, date: instantiate.date, startTime: instantiate.startTime || null, endTime: instantiate.endTime || null, plannedMinutes: selectedTemplate.value.plannedMinutes, category: selectedTemplate.value.category, title: selectedTemplate.value.title, description: selectedTemplate.value.description, acceptance: selectedTemplate.value.acceptance, resourceUrl: selectedTemplate.value.resourceUrl, required: selectedTemplate.value.required, sortOrder: instantiate.sortOrder };
  try {
    if (instantiate.mode === 'ONCE') await api.post(`/templates/${selectedTemplate.value.id}/instantiate`, { planId, tagId: instantiate.tagId || null, date: instantiate.date, startTime: instantiate.startTime || null, endTime: instantiate.endTime || null, sortOrder: instantiate.sortOrder });
    else await api.post(`/plans/${planId}/tasks/recurring`, { task: base, mode: instantiate.mode, until: instantiate.until });
    message.value = instantiate.mode === 'ONCE' ? '任务已从模板创建。' : '重复任务已按规则创建。';
  } catch (exception) { error.value = apiMessage(exception); }
}
function resetMilestone() { editingMilestone.value = null; Object.assign(milestoneDraft, { name: '', date: plan.value?.startDate ?? '', description: '', acceptanceText: '' }); }
function editMilestone(milestone: Milestone) { editingMilestone.value = milestone; Object.assign(milestoneDraft, { name: milestone.name, date: milestone.date, description: milestone.description, acceptanceText: milestone.acceptance.join('\n') }); }
function milestonePayload(version?: number) { return { ...milestoneDraft, acceptance: milestoneDraft.acceptanceText.split('\n').map(item => item.trim()).filter(Boolean), version }; }
async function saveMilestone() {
  try {
    if (editingMilestone.value) await api.put(`/milestones/${editingMilestone.value.id}`, milestonePayload(editingMilestone.value.version));
    else await api.post(`/plans/${planId}/milestones`, milestonePayload());
    message.value = '里程碑已保存。'; resetMilestone(); await load();
  } catch (exception) { error.value = apiMessage(exception); }
}
async function saveMilestoneProgress(milestone: Milestone) {
  try { await api.put(`/milestones/${milestone.id}/progress`, { checks: milestoneChecks[milestone.id] ?? [], note: milestoneNotes[milestone.id] ?? '', version: milestone.version }); message.value = `已记录「${milestone.name}」的完成证据。`; await load(); } catch (exception) { error.value = apiMessage(exception); }
}
async function deleteMilestone(milestone: Milestone) { if (!confirm(`删除里程碑“${milestone.name}”？`)) return; try { await api.delete(`/milestones/${milestone.id}`); await load(); } catch (exception) { error.value = apiMessage(exception); } }
async function stopBatch(batch: RecurrenceBatch) {
  const from = stopFrom[batch.recurrenceId] || batch.firstDate;
  if (!confirm(`停止“${batch.title}”从 ${from} 起尚未完成的实例？已完成和已跳过的记录会保留。`)) return;
  try { const { data } = await api.post(`/plans/${planId}/recurrences/${batch.recurrenceId}/stop`, { from, reason: '在重复任务批次管理中停止' }); message.value = `已停止 ${data.stoppedCount} 项未来实例。`; await load(); } catch (exception) { error.value = apiMessage(exception); }
}
onMounted(() => load().then(resetMilestone).catch(exception => error.value = apiMessage(exception)));
</script>

<template>
  <div class="page studio-page">
    <header class="page-head"><div><p class="eyebrow">PLAN / MILESTONES / TEMPLATES</p><h1>{{ plan?.name ?? '计划工作台' }}</h1><p>把阶段目标、可重复动作和日程实例放到同一个计划工作台。</p></div><router-link class="btn" :to="`/plans/${planId}`">返回计划任务</router-link></header>
    <p v-if="error" class="error-text">{{ error }}</p><p v-if="message" class="success-text">{{ message }}</p>
    <section class="studio-section">
      <header class="section-title"><div><p class="eyebrow">MILESTONE SCALE</p><h2>阶段里程碑</h2></div><span>{{ milestones.length }}</span></header>
      <div v-if="milestones.length" class="milestone-scale"><article v-for="(milestone, index) in milestones" :key="milestone.id" class="milestone-card"><div class="milestone-index">{{ String(index + 1).padStart(2, '0') }}</div><div><p class="eyebrow">{{ milestone.date }}</p><h3>{{ milestone.name }}</h3><p>{{ milestone.description || '暂无说明' }}</p><label v-for="(item, checkIndex) in milestone.acceptance" :key="item" class="check acceptance-check"><input v-model="milestoneChecks[milestone.id][checkIndex]" type="checkbox" />{{ item }}</label><label>复盘备注<textarea v-model="milestoneNotes[milestone.id]" rows="2" /></label><div class="button-row"><button class="btn primary" @click="saveMilestoneProgress(milestone)">保存证据</button><button class="btn" @click="editMilestone(milestone)">编辑</button><button class="btn danger" @click="deleteMilestone(milestone)">删除</button></div></div></article></div>
      <p v-else class="empty">还没有里程碑。可以把原来的 G1—G5 或任何阶段验收目标加到这里。</p>
      <form class="studio-form" @submit.prevent="saveMilestone"><p class="eyebrow">{{ editingMilestone ? 'EDIT MILESTONE' : 'NEW MILESTONE' }}</p><div class="form-grid"><label>名称<input v-model="milestoneDraft.name" required /></label><label>目标日期<input v-model="milestoneDraft.date" type="date" :min="plan?.startDate" :max="plan?.endDate" required /></label></div><label>说明<textarea v-model="milestoneDraft.description" rows="2" /></label><label>验收标准（每行一项）<textarea v-model="milestoneDraft.acceptanceText" rows="3" /></label><div class="button-row"><button class="btn primary">{{ editingMilestone ? '保存里程碑' : '新增里程碑' }}</button><button v-if="editingMilestone" class="btn" type="button" @click="resetMilestone">取消</button></div></form>
    </section>
    <div class="studio-grid">
      <section class="studio-section"><header class="section-title"><div><p class="eyebrow">TEMPLATE LIBRARY</p><h2>任务模板</h2></div><span>{{ templates.length }}</span></header><div v-if="templates.length" class="template-list"><article v-for="template in templates" :key="template.id"><strong>{{ template.name }}</strong><span>{{ template.title }} · {{ template.plannedMinutes }} 分钟</span><small>{{ template.category }}</small><div class="button-row"><button class="text-btn" @click="editTemplate(template)">编辑</button><button class="text-btn danger-text" @click="deleteTemplate(template)">删除</button></div></article></div><p v-else class="empty">保存常用动作后，可以快速创建单次或重复任务。</p><form class="studio-form" @submit.prevent="saveTemplate"><p class="eyebrow">{{ editingTemplate ? 'EDIT TEMPLATE' : 'NEW TEMPLATE' }}</p><label>模板名称<input v-model="templateDraft.name" maxlength="80" required /></label><label>任务标题<input v-model="templateDraft.title" required /></label><div class="form-grid"><label>分类<input v-model="templateDraft.category" maxlength="40" required /></label><label>计划分钟<input v-model.number="templateDraft.plannedMinutes" type="number" min="1" max="1440" required /></label></div><label>任务内容<textarea v-model="templateDraft.description" rows="2" /></label><label>验收标准（每行一项）<textarea v-model="templateDraft.acceptanceText" rows="3" /></label><label>资料链接<input v-model="templateDraft.resourceUrl" type="url" /></label><label class="check"><input v-model="templateDraft.required" type="checkbox" />计入核心完成率</label><div class="button-row"><button class="btn primary">{{ editingTemplate ? '保存模板' : '新增模板' }}</button><button v-if="editingTemplate" class="btn" type="button" @click="resetTemplate">取消</button></div></form></section>
      <section class="studio-section instance-panel"><header class="section-title"><div><p class="eyebrow">CREATE FROM TEMPLATE</p><h2>创建任务实例</h2></div></header><p>模板只保存规则；创建后每项任务都可以独立编辑、延期或完成。</p><label>选择模板<select v-model="instantiate.templateId"><option value="">请选择模板</option><option v-for="template in templates" :key="template.id" :value="template.id">{{ template.name }}</option></select></label><div class="form-grid"><label>日期<input v-model="instantiate.date" type="date" :min="plan?.startDate" :max="plan?.endDate" /></label><label>标签<select v-model="instantiate.tagId"><option value="">未分类</option><option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option></select></label></div><div class="form-grid"><label>开始时间<input v-model="instantiate.startTime" type="time" /></label><label>结束时间<input v-model="instantiate.endTime" type="time" /></label></div><label>重复规则<select v-model="instantiate.mode"><option value="ONCE">仅此一次</option><option value="DAILY">每天</option><option value="WEEKDAYS">每个工作日</option><option value="WEEKLY">每周同一天</option></select></label><label v-if="instantiate.mode !== 'ONCE'">重复至<input v-model="instantiate.until" type="date" :min="instantiate.date" :max="plan?.endDate" /></label><button class="btn primary" :disabled="!selectedTemplate" @click="createFromTemplate">{{ instantiate.mode === 'ONCE' ? '创建任务' : '创建重复任务' }}</button></section>
    </div>
    <section class="studio-section"><header class="section-title"><div><p class="eyebrow">RECURRENCE BATCHES</p><h2>重复任务批次</h2></div><span>{{ recurrences.length }}</span></header><p>重复任务在创建时已经生成具体实例。停止批次只会跳过指定日期起尚未完成的实例，不会删改历史。</p><div v-if="recurrences.length" class="template-list"><article v-for="batch in recurrences" :key="batch.recurrenceId"><strong>{{ batch.title }}</strong><span>{{ batch.firstDate }} — {{ batch.lastDate }} · {{ batch.completedCount }}/{{ batch.taskCount }} 已完成 · {{ batch.openCount }} 未完成</span><small>{{ batch.category }}</small><div class="button-row"><label>停止自<input v-model="stopFrom[batch.recurrenceId]" type="date" :min="batch.firstDate" :max="batch.lastDate" /></label><button class="btn danger" @click="stopBatch(batch)">停止未来任务</button></div></article></div><p v-else class="empty">还没有重复任务批次。</p></section>
  </div>
</template>
