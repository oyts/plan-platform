<script setup lang="ts">
import ColorPicker from '@/components/ColorPicker.vue';
import { computed, onMounted, reactive, ref } from 'vue'; import { useRouter } from 'vue-router'; import { api, apiMessage, OfflineQueuedError } from '@/api/client'; import type { Plan } from '@/types';
import LocalBadge from '@/components/LocalBadge.vue';
import RestDaysPicker from '@/components/RestDaysPicker.vue';
import { restDaySummary } from '@/restDays';
import { useAuthStore } from '@/stores/auth';
const plans=ref<Plan[]>([]);const error=ref('');const busy=ref(false);const router=useRouter();const today=new Date().toLocaleDateString('sv-SE');
const draft=reactive({name:'',description:'',startDate:today,endDate:today,overallTagName:'总计',overallTagColor:'#002FA7',restDays:[] as number[]});
const auth = useAuthStore();
const supportsRestDays = computed(() => auth.planRestDaysEnabled || plans.value.some(plan => plan.restDays !== undefined));
async function load(){plans.value=(await api.get<Plan[]>('/plans')).data;}
async function create(){busy.value=true;error.value='';try{if (draft.restDays.length && !supportsRestDays.value) throw new Error('当前服务器尚未支持休息日配置，请先升级服务器。'); const {data}=await api.post<Plan>('/plans',draft);await router.push(`/plans/${data.id}`);}catch(e){if(e instanceof OfflineQueuedError){await load().catch(()=>undefined);draft.name='';draft.description='';}else error.value=apiMessage(e);}finally{busy.value=false;}}
onMounted(()=>load().catch(e=>error.value=apiMessage(e)));
</script>
<template>
  <div class="page">
    <router-link class="btn" to="/organize">整套复制与未来批量调整</router-link>
    <section class="page-head"><div><h1>周期计划</h1></div><p>在日期范围内安排日程、休息日和每日任务，与目标路线独立。</p></section>
    <p v-if="error" class="error-text">{{error}}</p>
    <div class="split-layout">
      <section class="section">
        <header class="section-title"><h2>我的计划</h2><span>{{plans.length}}</span></header>
        <article v-for="plan in plans" :key="plan.id" class="plan-card" @click="!plan.localOnly && router.push(`/plans/${plan.id}`)">
          <div><strong>{{plan.name}}</strong><p>{{plan.startDate}}—{{plan.endDate}}</p><p class="helper-text">{{ restDaySummary(plan.restDays) }}</p><LocalBadge :state="plan.localState"/><p v-if="plan.localOnly" class="helper-text">本地新计划，同步成功后可进入并添加任务。</p></div>
          <div class="plan-progress"><span>{{plan.completedCount}}/{{plan.taskCount}}</span><i><b :style="{width:plan.taskCount?`${plan.completedCount/plan.taskCount*100}%`:'0%'}"/></i></div>
        </article>
        <p v-if="!plans.length && !error" class="empty">创建第一个计划后，它会显示在这里。</p>
      </section>
      <form class="editor-card" @submit.prevent="create">
        <h2>新建计划</h2>
        <router-link class="btn" to="/ai?mode=PLAN">使用 AI 构建周期计划</router-link>
        <label>计划名称<input v-model="draft.name" required maxlength="120" /></label><label>说明<textarea v-model="draft.description" rows="3"/></label>
        <div class="form-grid"><label>开始日期<input v-model="draft.startDate" type="date" required /></label><label>结束日期<input v-model="draft.endDate" type="date" required /></label></div>
        <RestDaysPicker v-model="draft.restDays" :disabled="busy || !supportsRestDays" />
        <p v-if="!supportsRestDays" class="helper-text">当前服务器未确认支持休息日配置。请升级并恢复连接后使用，不会静默丢弃选择。</p>
        <div class="form-grid"><label>总标签<input v-model="draft.overallTagName" required /></label><ColorPicker v-model="draft.overallTagColor" label="标签颜色" /></div>
        <button class="btn primary" :disabled="busy">{{busy?'正在保存…':'保存计划'}}</button>
      </form>
    </div>
  </div>
</template>
