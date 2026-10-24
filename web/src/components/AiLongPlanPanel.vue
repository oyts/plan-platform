<script setup lang="ts">
import {computed,ref} from 'vue';
import type {AiDraft,AiSession} from '@/aiPlanning';
const props=defineProps<{session:AiSession;draft:AiDraft;disabled:boolean;dirty:boolean;canCall:boolean}>();
const emit=defineEmits<{start:[string];send:[];manual:[];change:[]}>();
const coverage=ref(''),confirmed=ref(false);
const next=computed(()=>props.session.longPlan?.batches.find(b=>!b.complete));
const items=computed(()=>props.draft.items.filter(i=>next.value?.itemIds.includes(i.localId)));
const plannedDates=computed(()=>new Set(props.draft.items.map(i=>i.dueDate).filter(Boolean)).size);
</script>
<template>
<section class="long-plan" aria-label="长规划分批细化">
<h3>长规划 · 大纲与分批细化</h3><p>{{draft.items.length}} / 100项 · {{draft.stages.length}} / 30阶段<span v-if="draft.mode==='PLAN'"> · {{draft.startDate}} 至 {{draft.endDate}} · 有事项的日期 {{plannedDates}} 天（不是整个周期的覆盖证明）</span></p>
<template v-if="!session.longPlan"><p>先核对大纲是否涵盖全部日期、内容、阶段和依赖；没有列出的范围不会自动补齐。超过100项请自行选择拆为多份规划，不会截断。已确认大纲按每批最多10项细化，只改标题、说明和参考时长，日期及依赖保持稳定。全部完成前不能生成真实业务。</p><label>我确认的大纲覆盖范围<textarea v-model="coverage" maxlength="1000" rows="3" placeholder="例如：覆盖10月到11月的Java基础学习，周日休息；列表已包含全部计划日。"/></label><label class="consent"><input v-model="confirmed" type="checkbox"/>已逐项核对完整大纲；同意按下述总预算分批，每批仍由我单独发送。</label><p>最多10批、12次手动提交（失败也占预留预算）、24次上游调用（含修复），预留输入1536000 / 输出98304 Token；累计调用时间硬上限24分钟，7天到期。每批成功回复计1次每日额度，失败或未知不自动重试。手动细化不调用AI。</p><button class="btn" :disabled="disabled||dirty||!confirmed||!coverage.trim()" @click="emit('start',coverage)">确认大纲，开启分批（不调用 AI）</button></template>
<template v-else><p role="status">{{session.longPlan.coverage}} · 已完成 {{session.longPlan.batches.filter(b=>b.complete).length}} / {{session.longPlan.batches.length}}批 · {{session.longPlan.complete?'可核对整份草稿':'仍有未细化内容，不能应用真实业务'}}</p><p>已预留 {{session.longPlan.submissions}} / {{session.longPlan.maxSubmissions}}次提交，{{session.longPlan.reservedCalls}} / {{session.longPlan.maxCalls}}次上游；输入 {{session.longPlan.reservedInputTokens}} / {{session.longPlan.maxInputTokens}}、输出 {{session.longPlan.reservedOutputTokens}} / {{session.longPlan.maxOutputTokens}} Token。累计调用时间已预留 {{session.longPlan.reservedComputeSeconds}} / {{session.longPlan.maxComputeSeconds}}秒。预留上限不是实际花费，未知结果也不返还本流程预留。到期 {{new Date(session.longPlan.expiresAt).toLocaleString('zh-CN')}}；重开不会重置预算。</p>
<ol class="batches"><li v-for="batch in session.longPlan.batches" :key="batch.number">第{{batch.number}}批 · {{batch.itemIds.length}}项 · {{batch.complete?'已完成':batch.lastState==='UNKNOWN'?'结果未知，请先核对，不会自动重发':batch.lastState==='FAILED'?'失败，原内容保留':batch.number===next?.number?'待本次明确提交':'尚未细化'}}</li></ol>
<template v-if="next"><p>第{{next.number}}批：{{items.map(i=>i.title).join('、')}}。发送只处理本批，完成后不会自动发送下一批。修改要求可填写左侧／对话页输入框。</p><button class="btn" :disabled="disabled||dirty||!canCall||session.longPlan.submissions>=session.longPlan.maxSubmissions||new Date(session.longPlan.expiresAt).getTime()<=Date.now()" @click="emit('send')">发送第{{next.number}}批给 AI</button><details><summary>手动补齐本批（不调用 AI）</summary><fieldset :disabled="disabled"><article v-for="item in items" :key="item.localId"><label>细化标题<input v-model="item.title" maxlength="180" @input="emit('change')"/></label><label>细化说明<textarea v-model="item.note" maxlength="2000" rows="3" @input="emit('change')"/></label><label>参考分钟<input v-model.number="item.plannedMinutes" type="number" :min="draft.mode==='TODO'?0:1" max="1440" @input="emit('change')"/></label></article><p>确认本批内容已细化完成？此操作只保存对话草稿，不生成真实计划。</p><button class="btn" @click="emit('manual')">确认手动完成本批</button></fieldset></details></template>
</template>
</section>
</template>
<style scoped>
.long-plan{border:1px solid var(--line);padding:16px;min-width:0}.long-plan h3{font-size:1rem;margin-top:0}.long-plan p,.long-plan li{font-size:.8rem;line-height:1.8;overflow-wrap:anywhere}.long-plan label:not(.consent){display:grid;gap:8px;font-size:.8rem;margin:12px 0;font-weight:700}.long-plan textarea,.long-plan input:not([type=checkbox]){width:100%;box-sizing:border-box}.consent{display:flex;gap:10px;align-items:flex-start;font-size:.8rem;line-height:1.8}.consent input{flex:0 0 20px;width:20px;height:20px;margin-top:4px}.long-plan fieldset{border:0;padding:0;min-width:0}.long-plan article{border-top:1px solid var(--line);padding-top:12px}.long-plan summary{min-height:44px;line-height:1.8;padding:12px 0;cursor:pointer;font-size:.85rem}.batches{padding-left:20px}
</style>
