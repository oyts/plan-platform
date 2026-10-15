<script setup lang="ts">
import { computed, ref } from 'vue';
import type { AiDraft } from '@/aiPlanning';
import RestDaysPicker from './RestDaysPicker.vue';
const props = defineProps<{ draft: AiDraft; disabled: boolean }>();
const emit = defineEmits<{ change: [] }>();
const page = ref(0);
const pages = computed(() => Math.max(1, Math.ceil(props.draft.items.length / 10)));
const visible = computed(() => props.draft.items.slice(page.value * 10, page.value * 10 + 10));
function change() { emit('change'); }
function add() {
  props.draft.items.push({ localId: crypto.randomUUID(), title: '', note: '', dueDate: props.draft.mode === 'ROUTE' ? null : props.draft.startDate ?? new Date().toLocaleDateString('sv-SE'), plannedMinutes: props.draft.mode === 'TODO' ? 0 : 30, stageId: null, prerequisites: [] });
  page.value = pages.value - 1; change();
}
function remove(id: string) { props.draft.items = props.draft.items.filter(i => i.localId !== id); for (const item of props.draft.items) item.prerequisites = item.prerequisites.filter(p => p !== id); page.value = Math.min(page.value, pages.value - 1); change(); }
</script>
<template>
  <fieldset class="ai-draft-fields" :disabled="disabled" @input="change" @change="change">
    <label>规划名称<input v-model="draft.title" maxlength="120" required /></label>
    <template v-if="draft.mode === 'PLAN'"><div class="ai-dates"><label>开始日期<input v-model="draft.startDate" type="date" required /></label><label>结束日期<input v-model="draft.endDate" type="date" required /></label></div><RestDaysPicker v-model="draft.restDays" :disabled="disabled" @update:model-value="change" /></template>
    <details v-if="draft.mode === 'ROUTE' && draft.stages.length"><summary>路线阶段（{{ draft.stages.length }}）</summary><label v-for="stage in draft.stages" :key="stage.localId">阶段名称<input v-model="stage.title" maxlength="120" required /></label></details>
    <article v-for="(item, index) in visible" :key="item.localId" class="ai-draft-item"><header><strong>步骤 {{ page * 10 + index + 1 }}</strong><button class="text-btn danger-text" type="button" @click="remove(item.localId)">移除</button></header><label>任务标题<input v-model="item.title" maxlength="120" required /></label><label>任务说明<textarea v-model="item.note" maxlength="2000" rows="2" /></label><div class="ai-dates"><label v-if="draft.mode !== 'ROUTE'">执行日期<input v-model="item.dueDate" type="date" required /></label><label v-if="draft.mode !== 'TODO'">预计分钟<input v-model.number="item.plannedMinutes" type="number" min="1" max="1440" required /></label></div><template v-if="draft.mode === 'ROUTE'"><label>所属阶段<select v-model="item.stageId"><option :value="null">不分阶段</option><option v-for="stage in draft.stages" :key="stage.localId" :value="stage.localId">{{ stage.title }}</option></select></label><details><summary>前置步骤（{{ item.prerequisites.length }}）</summary><label>完成所选步骤后解锁<select v-model="item.prerequisites" multiple size="3"><option v-for="candidate in draft.items.filter(i => i.localId !== item.localId)" :key="candidate.localId" :value="candidate.localId">{{ candidate.title || '未命名步骤' }}</option></select></label></details></template></article>
    <div class="button-row"><button class="btn" type="button" :disabled="page === 0" @click="page--">上一组</button><span>{{ page + 1 }} / {{ pages }}</span><button class="btn" type="button" :disabled="page + 1 >= pages" @click="page++">下一组</button><button class="btn" type="button" :disabled="draft.items.length >= 100" @click="add">添加步骤</button></div>
  </fieldset>
</template>
<style scoped>
.ai-draft-fields{border:0;padding:0;margin:0;min-width:0;display:grid;gap:18px}.ai-draft-fields label{display:grid;gap:8px;font-size:.8rem;font-weight:700}.ai-dates{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.ai-draft-item{border-top:1px solid var(--line);padding-top:18px;display:grid;gap:14px}.ai-draft-item header{display:flex;justify-content:space-between;align-items:center}.ai-draft-item header button{min-height:44px}.ai-draft-fields summary{cursor:pointer;min-height:44px;line-height:44px;font-size:.8rem}.ai-draft-fields details>label{margin-top:12px}.ai-draft-fields .button-row{align-items:center;gap:8px;flex-wrap:wrap}.ai-draft-fields .button-row span{font-size:.75rem}.ai-draft-fields .btn{min-height:44px}@media(max-width:420px){.ai-dates{grid-template-columns:1fr}}
</style>
