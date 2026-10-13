<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router';
import { advanceLesson, bindLearning, dismissLesson, learning, practiceLesson, previousLesson, restartLesson } from '@/learning';
import { freshPractice, isLesson, lessons, stepReady, type LessonId, type Practice } from '@/learningModel';
import RestDaysPicker from '@/components/RestDaysPicker.vue';
const route = useRoute(); const busy = ref(false); const title = ref<HTMLElement>(); const draft = ref<Practice>(freshPractice());
const lesson = computed(() => lessons.find(item => item.id === route.params.lesson));
const progress = computed(() => lesson.value ? learning.progress[lesson.value.id] : null);
const step = computed(() => progress.value?.step ?? 0);
const ready = computed(() => lesson.value && stepReady(lesson.value.id, step.value, draft.value));
const saved = ref('');
// Prevent internal navigation from tearing down the editor while IndexedDB commits.
onBeforeRouteLeave(() => !busy.value);
onBeforeRouteUpdate(() => !busy.value);
function pendingSave(event: BeforeUnloadEvent) { if (busy.value) { event.preventDefault(); event.returnValue = ''; } }
onMounted(() => window.addEventListener('beforeunload', pendingSave));
onUnmounted(() => window.removeEventListener('beforeunload', pendingSave));
watch([() => route.params.lesson, () => learning.ready], async () => {
  await bindLearning(); draft.value = JSON.parse(JSON.stringify(progress.value?.practice ?? freshPractice())); saved.value = '';
}, { immediate: true });
async function saveDraft() { if (!lesson.value || busy.value) return; busy.value = true; try { await practiceLesson(lesson.value.id, JSON.parse(JSON.stringify(draft.value))); saved.value = learning.storageError ? '练习暂存在内存中，请保持页面打开。' : '练习已保存在当前设备。'; } finally { busy.value = false; } }
async function next() { if (!lesson.value || busy.value || !ready.value) return; busy.value = true; try { await practiceLesson(lesson.value.id, JSON.parse(JSON.stringify(draft.value))); await advanceLesson(lesson.value.id); saved.value = ''; await nextTick(); title.value?.focus(); } finally { busy.value = false; } }
async function back() { if (!lesson.value || busy.value) return; busy.value = true; try { await previousLesson(lesson.value.id); saved.value = ''; await nextTick(); title.value?.focus(); } finally { busy.value = false; } }
async function restart(id: LessonId) { busy.value = true; try { await restartLesson(id); draft.value = freshPractice(); saved.value = ''; await nextTick(); title.value?.focus(); } finally { busy.value = false; } }
function scheduleExample() { draft.value.rescheduled = true; }
const hints = computed(() => {
  if (lesson.value?.id === 'plan') return ['给练习起个名称，选一个不超过 31 天的日期范围。真实计划可以更长。', '为这次练习至少选一天休息、留一天计划日。真实计划可以不设休息日，星期六和星期日不会默认休息。', '在周期内选一个非休息日，安排一条阅读任务。真实任务在计划的任务页添加；日程会按日期显示。', '勾选完成，看看进度如何改变。真实使用时也可以记录实际用时，再在统计与复盘中回顾。'];
  if (lesson.value?.id === 'route') return ['写下长期目标，例如“学会弹奏一首歌”。路线不要求每天执行，也不用设置结束日期。', '给一组步骤起阶段名称，例如“认识吉他”。阶段把相关步骤归在一起，不等同于某一天。', '这次选择明确依赖：先认识琴弦，再练习单音。真实路线支持灵活推进；只有设了前置依赖的步骤才要等待。', '完成前置步骤后，后一个步骤就可以开始。有空做一两步，忙的时候暂停，不会变成逾期的每日任务。'];
  if (lesson.value?.id === 'todo') return ['输入一件小事，例如“寄出资料”。待办不属于周期计划或目标路线。', '加一句提示，例如“确认收件地址”。真实使用也可以留空备注。', '勾选表示已经完成。真实待办默认只显示未完成，切换“状态”到“已完成”或“全部”可找回。', '临时改期很常见：在练习中取消完成并改到明天。真实页面中点击“编辑”，调整“安排日期”；随后切换查看日期。'];
  return ['真实离线前，先联网打开需要的列表、任务页或日期范围。没有缓存的内容不能凭空离线读取。', '练习模拟离线修改：内容先保存在这台设备，队列增加一项。本地保存不代表其他设备已经收到。', '模拟另一台设备也修改了同一项。联网后的版本冲突不会自动覆盖，需要在同步中心查看两份内容。', '比较两份标题，明确选择保留哪份。这个练习不会提交真实队列。真实使用请先核对内容，再同步等待项。'];
});
</script>
<template>
  <div class="page learning-page">
    <header class="page-head"><div><p class="eyebrow">LEARN / PRACTICE</p><h1>使用教学</h1></div><p>先做一个小案例，再开始自己的计划。可以暂停，也可以随时重学。</p></header>
    <p v-if="learning.storageError" class="error-text" role="alert">此设备无法保存教学进度，本次仍可练习；关闭页面后可能需要重来。真实业务数据不受此提示影响。</p>
    <p v-if="!learning.ready" role="status">正在读取教学进度…</p>
    <template v-else>
      <nav class="lesson-tabs" aria-label="教学课程"><router-link to="/learn" :aria-current="!route.params.lesson ? 'page' : undefined">全部课程</router-link><router-link v-for="item in lessons" :key="item.id" :to="`/learn/${item.id}`" :aria-current="lesson?.id === item.id ? 'page' : undefined">{{ item.title }}</router-link></nav>
      <section v-if="!route.params.lesson" class="lesson-grid" aria-label="选择教学">
        <article v-for="(item,index) in lessons" :key="item.id" class="lesson-card"><span class="panel-number">{{ String(index + 1).padStart(2,'0') }}</span><h2>{{ item.title }}</h2><p>{{ item.summary }}</p><p class="helper-text">练习：{{ item.example }} · 约 {{ item.minutes }} 分钟</p><strong>{{ learning.progress[item.id].completed ? '已完成，可随时重学' : learning.progress[item.id].step ? `进度 ${learning.progress[item.id].step + 1} / 4` : '尚未完成' }}</strong><router-link class="btn primary" :to="`/learn/${item.id}`">{{ learning.progress[item.id].completed ? '回顾教学' : learning.progress[item.id].step ? '继续练习' : '开始练习' }}</router-link></article>
      </section>
      <p v-else-if="!isLesson(route.params.lesson)" class="empty">没有这节教学，请从上方课程重新选择。</p>
      <template v-else-if="lesson && progress">
        <div class="lesson-context"><p><strong>独立练习区</strong> · 内容仅用于教学，不会写入真实计划、同步队列或云端。</p><router-link :to="lesson.destination" @click="dismissLesson(lesson.id)">暂停，返回功能页</router-link></div>
        <section v-if="progress.completed" class="lesson-finished"><span class="panel-number">04 / 04</span><h2 ref="title" tabindex="-1">{{ lesson.title }}教学已完成</h2><p>你已完成“{{ lesson.example }}”。练习数据与真实数据互相独立。</p><p>{{ lesson.summary }}</p><h3>在真实页面怎么做</h3><ol><li v-for="instruction in lesson.usage" :key="instruction">{{ instruction }}</li></ol><div class="button-row"><router-link class="btn primary" :to="lesson.destination">去使用{{ lesson.title }}</router-link><button class="btn" :disabled="busy" @click="restart(lesson.id)">重新练习</button><router-link class="btn" to="/learn">返回课程列表</router-link></div></section>
        <div v-else class="lesson-layout">
          <aside class="lesson-instructions"><p class="eyebrow">{{ lesson.example }}</p><span class="panel-number">{{ String(step + 1).padStart(2,'0') }} / 04</span><h2 ref="title" tabindex="-1">{{ lesson.steps[step] }}</h2><p>{{ hints[step] }}</p><ol class="lesson-steps"><li v-for="(label,index) in lesson.steps" :key="label" :aria-current="index === step ? 'step' : undefined">{{ label }}<span>{{ index < step ? '已练习' : index === step ? '当前步骤' : '下一步' }}</span></li></ol></aside>
          <form class="lesson-practice" @submit.prevent="next">
            <h3>动手试一试</h3>
            <template v-if="lesson.id === 'plan'">
              <template v-if="step === 0"><label>练习计划名称<input v-model="draft.name" maxlength="120" placeholder="七天阅读练习" required/></label><div class="form-grid"><label>练习开始日期<input v-model="draft.start" type="date" required/></label><label>练习结束日期<input v-model="draft.end" type="date" :min="draft.start" required/></label></div><p class="helper-text">请选不超过 31 天的练习周期。</p></template>
              <template v-else-if="step === 1"><RestDaysPicker v-model="draft.rest"/><p class="helper-text">练习需要至少一天休息和一天计划日；正式计划没有此限制。</p></template>
              <template v-else-if="step === 2"><label>练习任务标题<input v-model="draft.task" maxlength="120" placeholder="读书 20 分钟" required/></label><label>练习任务日期<input v-model="draft.taskDate" type="date" :min="draft.start" :max="draft.end" required/></label><p class="helper-text">任务日期必须在周期内，而且不是刚才选的休息日。</p></template>
              <template v-else><div class="practice-record"><strong>{{ draft.name }}</strong><p>{{ draft.taskDate }} · {{ draft.task }}</p><label class="check"><input v-model="draft.done" type="checkbox"/>完成练习任务</label><p aria-live="polite">进度：{{ draft.done ? '1 / 1 已完成' : '0 / 1 已完成' }}</p></div></template>
            </template>
            <template v-else-if="lesson.id === 'route'">
              <label v-if="step === 0">练习目标名称<input v-model="draft.name" maxlength="120" placeholder="学会弹奏一首歌" required/></label>
              <label v-else-if="step === 1">练习阶段名称<input v-model="draft.stage" maxlength="120" placeholder="认识吉他" required/></label>
              <template v-else-if="step === 2"><div class="practice-record"><strong>{{ draft.stage }}</strong><p>步骤 1：认识六根琴弦</p><p>步骤 2：练习单音</p><label class="check"><input v-model="draft.prerequisite" type="checkbox"/>步骤 2 需要先完成步骤 1</label></div></template>
              <template v-else><div class="practice-record"><strong>{{ draft.name }} / {{ draft.stage }}</strong><label class="check"><input v-model="draft.done" type="checkbox"/>完成步骤 1：认识六根琴弦</label><p aria-live="polite">步骤 2：练习单音 · {{ draft.done ? '现在可以开始' : '等待步骤 1 完成' }}</p></div></template>
            </template>
            <template v-else-if="lesson.id === 'todo'">
              <label v-if="step === 0">练习待办标题<input v-model="draft.task" maxlength="120" placeholder="寄出资料" required/></label>
              <label v-else-if="step === 1">练习待办备注<textarea v-model="draft.note" maxlength="180" placeholder="确认收件地址" rows="3" required/></label>
              <div v-else-if="step === 2" class="practice-record"><strong>{{ draft.task }}</strong><p>{{ draft.note }}</p><label class="check"><input v-model="draft.done" type="checkbox"/>完成练习待办</label><p aria-live="polite">{{ draft.done ? '已完成，可在已完成列表查看' : '未完成' }}</p></div>
              <div v-else class="practice-record"><strong>{{ draft.task }}</strong><p>{{ draft.rescheduled ? '安排：明天 · 未完成' : '安排：今天 · 已完成' }}</p><button type="button" class="btn" @click="scheduleExample">取消完成并改到明天（练习）</button></div>
            </template>
            <template v-else>
              <label v-if="step === 0" class="check"><input v-model="draft.cached" type="checkbox"/>已在练习中联网打开这条任务</label>
              <template v-else-if="step === 1"><p>模拟离线：把“阅读”改为“阅读 20 分钟”。</p><button type="button" class="btn" @click="draft.queued = true">模拟离线保存</button><p aria-live="polite">{{ draft.queued ? '1 项本地操作 · 0 项冲突，其他设备尚未收到' : '0 项本地操作' }}</p></template>
              <template v-else-if="step === 2"><p>模拟恢复网络，发现另一台设备修改了同一条任务。</p><p>1 项本地操作 · 1 项待处理冲突</p><button type="button" class="btn" @click="draft.resolution = 'review'">查看两份内容</button><div v-if="draft.resolution === 'review'" class="practice-record"><p>本地：阅读 20 分钟</p><p>云端：阅读 30 分钟</p></div></template>
              <template v-else><div class="practice-record"><p>本地：阅读 20 分钟</p><p>云端：阅读 30 分钟</p><label class="check"><input v-model="draft.resolution" type="radio" value="cloud" name="resolution"/>保留云端版本（练习）</label><label class="check"><input v-model="draft.resolution" type="radio" value="local" name="resolution"/>保留本地版本（练习）</label><p v-if="['cloud','local'].includes(draft.resolution)" aria-live="polite">练习结果：{{ draft.resolution === 'cloud' ? '阅读 30 分钟' : '阅读 20 分钟' }} · 0 项本地操作、0 项冲突</p></div></template>
            </template>
            <p v-if="!ready" class="helper-text">完成上面的操作后，才能继续下一步。</p><p v-if="busy" role="status">正在保存练习，请稍候…</p><p v-else-if="saved" role="status">{{ saved }}</p>
            <div class="lesson-actions"><button type="button" class="btn" :disabled="busy || step === 0" @click="back">上一步</button><button type="button" class="btn" :disabled="busy" @click="saveDraft">保存练习</button><button class="btn primary" :disabled="busy || !ready">{{ step === 3 ? '完成教学' : '下一步' }}</button></div>
          </form>
        </div>
      </template>
      <footer class="learning-footer">教学进度只保存在当前设备，并按服务器和账号隔离，不会跨设备同步。离开前点击“保存练习”可保留当前输入；卸载应用或清除浏览器数据可能会重置进度。</footer>
    </template>
  </div>
</template>
<style scoped>
.lesson-tabs{display:flex;gap:0;border-bottom:1px solid #ddd;flex-wrap:wrap;margin-bottom:28px}.lesson-tabs a{padding:14px 18px;text-decoration:none;color:#555;border-bottom:3px solid transparent}.lesson-tabs a[aria-current=page]{color:#002fa7;border-color:#002fa7;font-weight:700}.lesson-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));border-top:1px solid #ddd;border-left:1px solid #ddd}.lesson-card{padding:28px;border-right:1px solid #ddd;border-bottom:1px solid #ddd;display:flex;align-items:start;flex-direction:column;gap:12px}.lesson-card p,.lesson-card h2{margin:0}.lesson-card .btn{margin-top:auto}.lesson-context{display:flex;justify-content:space-between;align-items:center;gap:20px;background:#f1f4fb;padding:16px 20px;margin-bottom:24px}.lesson-context p{margin:0}.lesson-context a{flex-shrink:0}.lesson-layout{display:grid;grid-template-columns:minmax(0,2fr) minmax(0,3fr);border:1px solid #ddd}.lesson-instructions{padding:28px;background:#f7f7f8;border-right:1px solid #ddd}.lesson-instructions>p{line-height:1.8}.lesson-steps{padding:0;list-style:none;margin:28px 0 0}.lesson-steps li{display:flex;justify-content:space-between;gap:12px;border-top:1px solid #ddd;padding:12px 0;color:#666}.lesson-steps span{font-size:12px;flex-shrink:0}.lesson-steps li[aria-current=step]{color:#002fa7;font-weight:700}.lesson-practice{display:flex;flex-direction:column;gap:20px;padding:28px;min-width:0}.lesson-practice h3{margin:0}.lesson-practice>label:not(.check),.lesson-practice>.form-grid>label{display:grid;gap:10px}.lesson-practice input:not([type=checkbox]):not([type=radio]),.lesson-practice textarea{min-width:0;width:100%}.lesson-practice .check{align-items:center}.lesson-practice .check input{width:20px;min-height:20px;flex-shrink:0}.practice-record{border:1px solid #ddd;padding:20px;overflow-wrap:anywhere}.lesson-actions{display:flex;gap:10px;flex-wrap:wrap;border-top:1px solid #ddd;padding-top:20px;margin-top:auto}.lesson-finished{padding:32px;border:1px solid #ddd}.lesson-finished .button-row{flex-wrap:wrap}.learning-footer{margin-top:28px;color:#666;font-size:13px;line-height:1.8}.learning-page h2:focus{outline:2px solid #002fa7;outline-offset:6px}
@media(max-width:700px){.lesson-tabs a{padding:12px 10px;font-size:14px}.lesson-grid,.lesson-layout{grid-template-columns:minmax(0,1fr)}.lesson-card{padding:20px}.lesson-context{align-items:start;flex-direction:column;gap:12px}.lesson-instructions{padding:20px;border-right:0;border-bottom:1px solid #ddd}.lesson-instructions .panel-number{font-size:36px}.lesson-steps{margin:16px 0 0}.lesson-practice{padding:20px}.lesson-actions .btn{flex:1;padding-left:10px;padding-right:10px;white-space:nowrap}.lesson-finished{padding:20px}.lesson-finished .btn{width:100%}.learning-page .form-grid{grid-template-columns:minmax(0,1fr)}}
</style>
