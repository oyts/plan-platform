<script setup lang="ts">
import {computed,watch} from 'vue';
import {templateCopyValidation,type Practice} from '@/learningModel';
import RestDaysPicker from './RestDaysPicker.vue';
const p=defineModel<Practice>({required:true});const props=defineProps<{step:number}>();
const validation=computed(()=>templateCopyValidation(p.value));
watch(()=>[p.value.name,p.value.templateKind],()=>{if(props.step===0){p.value.templateReviewed=false;p.value.templateSaved=false;p.value.templateCopied=false;p.value.templateSourceChanged=false;p.value.done=false;}});
watch(()=>[p.value.templateCopyName,p.value.start,p.value.end,JSON.stringify(p.value.rest)],()=>{if(props.step===2){p.value.templateCopied=false;p.value.templateCopyConfirmed=false;}});
</script>
<template>
<div class="template-learning">
 <template v-if="step===0">
  <label>练习结构来源<select v-model="p.templateKind"><option value="">请选择</option><option value="PLAN">一周阅读周期</option><option value="ROUTE">吉他入门路线</option></select></label>
  <label>练习模板名称<input v-model="p.name" maxlength="120" placeholder="我的阅读方法"/></label>
  <p>本例只使用合成内容。真实保存来源必须是本人记录；AI草稿需要先独立保存完整的新建规划。</p>
 </template>
 <template v-else-if="step===1">
  <section class="practice-record" aria-label="练习模板结构">
   <h4>{{p.name}}</h4>
   <template v-if="p.templateKind==='PLAN'"><p>七天周期 · 星期日休息</p><ol><li>第1天：阅读第一章 · 20分钟</li><li>第5天：整理提纲 · 15分钟</li><li>第7天：检查阅读笔记（里程碑）</li></ol></template>
   <template v-else><p>目标路线 · 没有结束期限</p><ol><li>认识吉他：认识琴弦 · 15分钟</li><li>单音练习：练习空弦 · 20分钟，前置为认识琴弦</li></ol></template>
   <p>只保存结构，不带完成状态、实际用时、私人复盘或AI对话。</p>
  </section>
  <label class="check"><input v-model="p.templateReviewed" type="checkbox"/>我已核对练习结构和保存范围</label>
  <button type="button" class="btn" :disabled="!p.templateReviewed" @click="p.templateSaved=true">保存为练习模板</button>
  <p v-if="p.templateSaved" role="status">练习模板已保存，没有写入真实模板库。</p>
 </template>
 <template v-else-if="step===2">
  <label>练习副本名称<input v-model="p.templateCopyName" maxlength="120"/></label>
  <template v-if="p.templateKind==='PLAN'"><label>练习新周期开始<input v-model="p.start" type="date"/></label><label>练习新周期结束<input v-model="p.end" type="date"/></label><RestDaysPicker v-model="p.rest"/></template>
  <p v-if="!validation.ready" role="status">{{validation.reason}}</p>
  <div v-else class="practice-record"><strong>{{p.templateCopyName}}</strong><p>{{p.templateKind==='PLAN'?`新周期：${p.start} 至 ${p.end}，安排按起点等距平移。`:'保留阶段及前置关系，不设置截止日期。'}}</p><p>新副本从未完成、实际用时0开始；来源和模板不被覆盖。</p></div>
  <label class="check"><input v-model="p.templateCopyConfirmed" type="checkbox"/>确认建立练习副本，不修改来源</label>
  <button type="button" class="btn" :disabled="!validation.ready||!p.templateCopyConfirmed" @click="p.templateCopied=true">建立练习副本</button>
  <p v-if="p.templateCopied" role="status">已建立练习副本，没有创建真实计划或路线。</p>
 </template>
 <template v-else>
  <button type="button" class="btn" @click="p.templateSourceChanged=true">修改来源标题（练习）</button>
  <p>来源：{{p.templateSourceChanged?'来源已改名，副本未跟随改名':'尚未修改'}}；原事项仍未完成。</p>
  <section class="practice-record"><h4>副本：{{p.templateCopyName}}</h4><p>已完成 {{p.done?1:0}} 项 · 原始执行记录不复制</p><button type="button" class="btn" @click="p.done=true">完成副本第一项（练习）</button></section>
  <p>模板：{{p.name}}。修改来源和推进副本互不覆盖；真实编辑模板也不会同步修改已建立的副本。</p>
 </template>
</div>
</template>
<style scoped>.template-learning{display:grid;gap:18px;min-width:0}.template-learning label:not(.check){display:grid;gap:8px}.template-learning p,.template-learning li{line-height:1.7;overflow-wrap:anywhere}.template-learning .practice-record{border:1px solid var(--line);padding:18px}.template-learning .check{display:flex;gap:10px;align-items:center}.template-learning .check input{width:20px;min-height:20px;flex:0 0 20px}.template-learning input,.template-learning select{max-width:100%;min-width:0}</style>
