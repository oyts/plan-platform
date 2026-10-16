<script setup lang="ts">
import type { Practice } from '@/learningModel';
defineProps<{ step: number }>();
const draft = defineModel<Practice>({ required: true });
</script>
<template>
  <section class="ai-practice" aria-label="AI 教学模拟">
    <p class="helper-text">本节回复与安排均为教学示例，不连接 DeepSeek、不扣次数、不创建真实数据。</p>
    <template v-if="step === 0">
      <label>练习目标<textarea v-model="draft.name" maxlength="180" rows="3" placeholder="我想用两周复习英语，每个计划日有20分钟。" /></label>
      <label class="check"><input v-model="draft.aiConsent" type="checkbox" />已阅读对话处理说明（练习）</label>
      <div class="practice-record"><strong>示例建议：周期计划</strong><p>这个案例有两周期限和规律安排，适合周期计划。无固定期限选目标路线，临时小事选今日待办。</p></div>
      <label>选择练习模式<select v-model="draft.aiMode"><option value="">由我确认模式</option><option value="PLAN">周期计划</option><option value="ROUTE">目标路线</option><option value="TODO">今日待办</option></select></label>
      <p v-if="draft.aiMode && draft.aiMode !== 'PLAN'" class="helper-text">本案例练习有期限的规律安排，请选择周期计划；真实使用由你自己决定模式。</p>
    </template>
    <template v-else-if="step === 1">
      <button class="btn" type="button" @click="draft.aiPreviewed = true; draft.task ||= '复习10个英语单词'">生成示例草稿（不调用 AI）</button>
      <template v-if="draft.aiPreviewed"><div class="practice-record"><strong>示例草稿 · 两周英语复习</strong><p>每个计划日 20 分钟，星期日休息。草稿尚未生成真实任务。</p></div><label>修改练习任务标题<input v-model="draft.task" maxlength="180" /></label><p>可以先把“10个”改成“5个”。真实页面需点击“保存草稿修改”，然后再确认；也可以继续对话修改。</p></template>
    </template>
    <template v-else-if="step === 2">
      <div class="practice-record"><strong>{{ draft.task }}</strong><p>示例安排：计划日练习，20 分钟；星期日休息。</p><p>目前仍是草稿，关闭对话不等于已经创建计划。</p></div>
      <label class="check"><input v-model="draft.aiConfirmed" type="checkbox" @change="draft.aiCreated = false" />我已核对内容，确认生成（练习）</label>
      <button type="button" class="btn" :disabled="!draft.aiConfirmed || draft.aiCreated" @click="draft.aiCreated = true">确认生成（练习）</button>
      <p role="status">{{ draft.aiCreated ? '模拟结果：已生成练习计划。没有创建真实任务。' : '尚未确认生成，不创建任务。' }}</p>
    </template>
    <template v-else>
      <div class="practice-record"><strong>示例原计划</strong><p>第 1 项：认识单词 · 已完成，执行记录保留</p><p>第 2 项：{{ draft.task }} · 未开始，20 分钟</p></div>
      <p>最近比较忙，希望未开始的练习缩短到 10 分钟。真实使用先从原计划进入“AI 调整”，同意载入后再发送要求。</p>
      <button class="btn" type="button" @click="draft.aiShifted = true; draft.resolution = ''">预览放慢后的安排（练习）</button>
      <div v-if="draft.aiShifted" class="practice-record" aria-label="练习调整对照"><p>保留：第 1 项已完成内容和执行记录不变。</p><p>修改：第 2 项预计时间 20 分钟 → 10 分钟。</p><p>不删除任务，不改变原休息日。</p><button class="btn" type="button" :disabled="draft.resolution === 'apply'" @click="draft.resolution = 'apply'">确认应用调整（练习）</button><p v-if="draft.resolution === 'apply'" role="status">模拟结果：仅未开始的事项放慢，已完成记录保留。</p></div>
    </template>
  </section>
</template>
<style scoped>
.ai-practice{display:grid;gap:20px;min-width:0}.ai-practice>label:not(.check){display:grid;gap:10px}.ai-practice .check{display:flex;align-items:flex-start;gap:10px;line-height:1.7}.ai-practice .check input{width:20px;height:20px;flex-shrink:0}.practice-record{border:1px solid var(--line);padding:20px;overflow-wrap:anywhere}.practice-record p{line-height:1.8}.ai-practice textarea,.ai-practice input,.ai-practice select{max-width:100%;min-width:0}.ai-practice .btn{min-height:44px}@media(max-width:700px){.practice-record{padding:16px}.ai-practice>.btn{width:100%}}
</style>
