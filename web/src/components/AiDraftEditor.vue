<script setup lang="ts">
import { computed, reactive } from 'vue';
import type { AiDraft, AiAdjustment } from '@/aiPlanning';
import RestDaysPicker from './RestDaysPicker.vue';
const props = defineProps<{ draft: AiDraft; disabled: boolean; adjustment?: AiAdjustment | null }>();
const emit = defineEmits<{ change: [] }>();
const pages = reactive<Record<string,number>>({});
const groups = computed(() => props.draft.mode === 'ROUTE' ? [...props.draft.stages.map(stage => ({id:stage.localId,title:stage.title,items:props.draft.items.filter(i=>i.stageId===stage.localId)})),{id:'unassigned',title:'未分组',items:props.draft.items.filter(i=>!i.stageId)}].filter(g=>g.items.length) : [{id:'all',title:'规划事项',items:props.draft.items}]);
const totalMinutes = computed(()=>props.draft.items.reduce((sum,item)=>sum+(Number.isFinite(item.plannedMinutes)?Math.max(0,item.plannedMinutes):0),0));
function groupPage(id:string,count:number){return Math.min(pages[id]??0,Math.max(0,Math.ceil(count/10)-1));}
function change() { emit('change'); }
function add() {
  props.draft.items.push({ localId: crypto.randomUUID(), title: '', note: '', dueDate: props.draft.mode === 'ROUTE' ? null : props.draft.startDate ?? new Date().toLocaleDateString('sv-SE'), plannedMinutes: props.draft.mode === 'TODO' ? 0 : 30, stageId: null, prerequisites: [] });
  pages[props.draft.mode==='ROUTE'?'unassigned':'all']=Math.floor((props.draft.items.filter(i=>props.draft.mode!=='ROUTE'||!i.stageId).length-1)/10);change();
}
function remove(id: string) { props.draft.items = props.draft.items.filter(i => i.localId !== id); for (const item of props.draft.items) item.prerequisites = item.prerequisites.filter(p => p !== id); change(); }
</script>
<template>
  <fieldset class="ai-draft-fields" :disabled="disabled" @input="change" @change="change">
    <label>规划名称<input v-model="draft.title" maxlength="120" :disabled="!!adjustment && draft.mode === 'TODO'" required /></label>
    <template v-if="draft.mode === 'PLAN'"><div class="ai-dates"><label>开始日期<input v-model="draft.startDate" type="date" :disabled="!!adjustment" required /></label><label>结束日期<input v-model="draft.endDate" type="date" :min="adjustment?.original.endDate ?? undefined" required /></label></div><p v-if="adjustment" class="helper-text">保留原起点，结束日期可以延长，不缩短历史范围。</p><RestDaysPicker v-model="draft.restDays" :disabled="disabled" @update:model-value="change" /></template>
    <details v-if="draft.mode === 'ROUTE' && draft.stages.length"><summary>路线阶段（{{ draft.stages.length }}）</summary><label v-for="stage in draft.stages" :key="stage.localId">阶段名称<input v-model="stage.title" maxlength="120" required /></label></details>
    <p v-if="draft.mode==='ROUTE'" class="helper-text" aria-label="草稿路线参考投入">全路线参考投入 {{totalMinutes}} 分钟；阶段合计使用全部步骤，不受分页影响。不是截止日期或完成保证。</p>
    <details v-for="group in groups" :key="group.id" class="ai-draft-group" open><summary>{{group.title}} · {{group.items.length}} 项<span v-if="draft.mode==='ROUTE'"> · 参考 {{group.items.reduce((sum,i)=>sum+(Number(i.plannedMinutes)||0),0)}} 分钟</span></summary>
      <article v-for="item in group.items.slice(groupPage(group.id,group.items.length)*10,groupPage(group.id,group.items.length)*10+10)" :key="item.localId" class="ai-draft-item"><header><strong>步骤 {{draft.items.findIndex(i=>i.localId===item.localId)+1}}</strong><span v-if="adjustment?.original.items.some(i => i.localId === item.localId)" class="helper-text">{{ adjustment.protectedItems.includes(item.localId) ? '执行历史保护' : '原事项保留' }}</span><button v-else class="text-btn danger-text" type="button" @click="remove(item.localId)">移除</button></header><fieldset class="ai-draft-fields" :disabled="disabled || adjustment?.protectedItems.includes(item.localId)"><label>任务标题<input v-model="item.title" maxlength="180" required /></label><label>任务说明<textarea v-model="item.note" maxlength="2000" rows="2" /></label><div class="ai-dates"><label v-if="draft.mode !== 'ROUTE'">执行日期<input v-model="item.dueDate" type="date" required /></label><label v-if="draft.mode !== 'TODO'">预计分钟<input v-model.number="item.plannedMinutes" type="number" min="1" max="1440" required /></label></div><template v-if="draft.mode === 'ROUTE'"><label>所属阶段<select v-model="item.stageId"><option :value="null">不分阶段</option><option v-for="stage in draft.stages" :key="stage.localId" :value="stage.localId">{{ stage.title }}</option></select></label><details><summary>前置步骤（{{ item.prerequisites.length }}）</summary><label>完成所选步骤后解锁<select v-model="item.prerequisites" multiple size="3"><option v-for="candidate in draft.items.filter(i => i.localId !== item.localId)" :key="candidate.localId" :value="candidate.localId">{{ candidate.title || '未命名步骤' }}</option></select></label></details></template></fieldset></article>
      <div v-if="group.items.length>10" class="button-row"><button class="btn" type="button" :disabled="groupPage(group.id,group.items.length)===0" @click="pages[group.id]=groupPage(group.id,group.items.length)-1">上一组</button><span>{{groupPage(group.id,group.items.length)+1}} / {{Math.max(1,Math.ceil(group.items.length/10))}}</span><button class="btn" type="button" :disabled="(groupPage(group.id,group.items.length)+1)*10>=group.items.length" @click="pages[group.id]=groupPage(group.id,group.items.length)+1">下一组</button></div>
    </details>
    <button class="btn" type="button" :disabled="draft.items.length >= 100" @click="add">添加步骤</button>
  </fieldset>
</template>
<style scoped>
.ai-draft-fields{border:0;padding:0;margin:0;min-width:0;display:grid;gap:18px}.ai-draft-fields label{display:grid;gap:8px;font-size:.8rem;font-weight:700}.ai-dates{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.ai-draft-item{border-top:1px solid var(--line);padding-top:18px;display:grid;gap:14px}.ai-draft-item header{display:flex;justify-content:space-between;align-items:center}.ai-draft-item header button{min-height:44px}.ai-draft-fields summary{cursor:pointer;min-height:44px;line-height:44px;font-size:.8rem}.ai-draft-fields details>label{margin-top:12px}.ai-draft-fields .button-row{align-items:center;gap:8px;flex-wrap:wrap}.ai-draft-fields .button-row span{font-size:.75rem}.ai-draft-fields .btn{min-height:44px}@media(max-width:420px){.ai-dates{grid-template-columns:1fr}}
</style>
