<script setup lang="ts">
import {computed,ref,watch} from 'vue';
import {blueprintItems,templateDependencies,type Blueprint} from '@/personalTemplates';
const props=defineProps<{blueprint:Blueprint}>(),page=ref(0);
const items=computed(()=>[...blueprintItems(props.blueprint)].sort((a,b)=>Number(a.sort_order)-Number(b.sort_order)||String(a.task_date??'').localeCompare(String(b.task_date??''))));
const visible=computed(()=>items.value.slice(page.value*20,page.value*20+20));
const stages=computed(()=>props.blueprint.rows.mainline_stages??[]),tags=computed(()=>props.blueprint.rows.plan_tags??[]);
const name=(id:unknown,rows:Record<string,unknown>[])=>rows.find(x=>x.id===id)?.name??'未分组';
const acceptance=(value:unknown)=>{try{return JSON.parse(String(value)) as string[];}catch{return [];}};
watch(()=>props.blueprint,()=>page.value=0);
</script>
<template><section class="structure" aria-label="完整模板结构"><header><h3>{{blueprint.parent.name}}</h3><p>{{blueprint.parent.description}}</p><p v-if="blueprint.kind==='PLAN'">结构周期：{{blueprint.parent.start_date}} 至 {{blueprint.parent.end_date}}。提交前请核对新计划周期与休息日。</p><p v-else>{{blueprint.parent.execution_mode==='SEQUENTIAL'?'顺序推进':'灵活推进'}}，不设置结束期限。</p><p>共 {{items.length}} 项 · 参考总投入 {{items.reduce((sum,x)=>sum+Number(x.planned_minutes),0)}} 分钟；不等于日历工期。</p></header>
<details v-if="stages.length"><summary>全部阶段（{{stages.length}}）</summary><p v-for="s in stages" :key="String(s.id)">{{s.name}}：{{s.description}}</p></details>
<details v-if="tags.length"><summary>全部标签（{{tags.length}}）</summary><p v-for="t in tags" :key="String(t.id)">{{t.name}} · {{t.color}}</p></details>
<article v-for="row in visible" :key="String(row.id)"><h4>{{row.title}}</h4><p>{{row.description}}</p><small>{{row.task_date??name(row.stage_id,stages)}} · {{row.planned_minutes}} 分钟<template v-if="row.start_time"> · {{row.start_time}}—{{row.end_time??'未设结束时间'}}</template></small><p v-if="blueprint.kind==='ROUTE'">前置：{{templateDependencies(row,blueprint)}}</p><p v-else>标签：{{name(row.tag_id,tags)}} · {{row.required_task?'必做':'选做'}}</p><ul v-if="acceptance(row.acceptance_json).length"><li v-for="(text,i) in acceptance(row.acceptance_json)" :key="i">{{text}}</li></ul><p v-if="row.resource_url" class="resource">资源：{{row.resource_url}}</p></article>
<div class="button-row"><span>第 {{page+1}} 页 · 每页20项</span><button class="btn" type="button" :disabled="page===0" @click="page--">上一页结构</button><button class="btn" type="button" :disabled="(page+1)*20>=items.length" @click="page++">下一页结构</button></div>
<details v-if="blueprint.rows.plan_milestones?.length"><summary>全部里程碑（{{blueprint.rows.plan_milestones.length}}）</summary><article v-for="m in blueprint.rows.plan_milestones" :key="String(m.id)"><h4>{{m.name}} · {{m.milestone_date}}</h4><p>{{m.description}}</p><ul><li v-for="(text,i) in acceptance(m.acceptance_json)" :key="i">{{text}}</li></ul></article></details></section></template>
<style scoped>.structure header{border-bottom:1px solid var(--line);padding-bottom:16px}.structure article{border-bottom:1px solid var(--line);padding:16px 0}.structure h3,.structure h4,.structure p,.structure small,.structure li{overflow-wrap:anywhere}.structure p{white-space:pre-wrap}.structure details{padding:16px 0}.structure summary{cursor:pointer;font-weight:700}.structure .button-row{flex-wrap:wrap;margin-top:18px}.resource{font-size:.875rem}.structure small{color:var(--muted)}</style>
