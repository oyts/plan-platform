<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { api } from '@/api/client';
import { currentQueueOwner, serverScope } from '@/api/client';
import { requiresConnection } from '@/connectionState';
import { requiredClientUpdate } from '@/clientUpdates';
import { localDate } from '@/todos';
import { reminderCount, reminderDefaults, reminderDismissKey, type DailySummary } from '@/dailyExperience';
const settings = ref(reminderDefaults()); const summary = ref<DailySummary|null>(null); const dismissed = ref('');
const owner = currentQueueOwner(), scope = serverScope();
const key = reminderDismissKey(scope,owner ?? '');
let active = true, busy = false, timer: number | undefined;
try { dismissed.value = localStorage.getItem(key) ?? ''; } catch { /* Hiding a notice remains optional. */ }
const count = computed(() => summary.value ? reminderCount(summary.value,settings.value) : 0);
const visible = computed(() => !requiresConnection() && !requiredClientUpdate() && count.value>0 && summary.value?.date===localDate() && dismissed.value!==localDate());
async function refresh() {
  if (busy || requiresConnection() || requiredClientUpdate() || document.visibilityState==='hidden') return;
  busy = true;
  try {
    const response = (await api.get('/daily/reminders')).data;
    if (!active || owner!==currentQueueOwner() || scope!==serverScope()) return;
    settings.value = response;
    if (!settings.value.enabled) { summary.value=null; return; }
    const value = (await api.get<DailySummary>('/daily/summary',{params:{date:localDate(),days:settings.value.lookAheadDays}})).data;
    if (active && owner===currentQueueOwner() && scope===serverScope()) summary.value=value;
  } catch { summary.value=null; } finally { busy=false; }
}
function dismiss() { dismissed.value=localDate(); try { localStorage.setItem(key,dismissed.value); } catch { /* In-memory dismissal still works. */ } }
function storage(event: StorageEvent) { if (event.key===key) dismissed.value=event.newValue ?? ''; }
onMounted(() => { void refresh(); timer=window.setInterval(refresh,5*60*1000); window.addEventListener('online',refresh); document.addEventListener('visibilitychange',refresh); window.addEventListener('plan-platform:reminders-changed',refresh); window.addEventListener('storage',storage); });
onUnmounted(() => { active=false; window.clearInterval(timer); window.removeEventListener('online',refresh); document.removeEventListener('visibilitychange',refresh); window.removeEventListener('plan-platform:reminders-changed',refresh); window.removeEventListener('storage',storage); });
</script>
<template>
  <aside v-if="visible" class="in-app-reminder" aria-label="站内事项提醒">
    <div><strong>{{ count }} 项事项值得留意</strong><p>包含今天、未来 {{ settings.lookAheadDays }} 天{{ settings.showOverdue ? '及逾期' : '' }}的未完成事项，仅统计已同步数据。</p></div>
    <div class="button-row"><router-link class="btn" to="/overview">查看事项</router-link><button type="button" class="btn" @click="dismiss">今天不再提示</button></div>
  </aside>
</template>
<style scoped>
.in-app-reminder{margin:16px auto;max-width:1320px;border:1px solid #ddd;border-left:4px solid #002fa7;padding:16px 20px;display:flex;justify-content:space-between;align-items:center;gap:16px;background:#fff}.in-app-reminder p{margin:6px 0 0;color:#555;font-size:13px;line-height:1.7}.in-app-reminder .button-row{flex-shrink:0}
@media(max-width:700px){.in-app-reminder{margin:12px 16px;flex-direction:column;align-items:stretch;padding:16px}.button-row{flex-wrap:wrap}.button-row .btn{flex:1}}
</style>
