<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { api, apiMessage } from '@/api/client';
import { quotaLimitValid, quotaRule, type AiPolicy, type AiMember, type AiPage, type AiAudit } from '@/aiQuota';
import AppSheet from './AppSheet.vue';

const policy = ref<AiPolicy>({ dailyLimit: 0, version: 0 });
const dailyLimit = ref<number | string>(0), loaded = ref(false), loading = ref(false), busy = ref(false), error = ref(''), message = ref('');
const query = ref(''), page = ref(0), members = ref<AiPage<AiMember>>({ items: [], page: 0, size: 10, total: 0 }), audits = ref<AiAudit[]>([]);
const selected = ref<AiMember | null>(null), editor = reactive({ inherit: true, dailyLimit: 0 as number | string, paused: false });
async function loadMembers() { members.value = (await api.get<AiPage<AiMember>>('/admin/ai/users', { params: { query: query.value, page: page.value, size: 10 } })).data; }
async function loadAudit() { audits.value = (await api.get<AiPage<AiAudit>>('/admin/ai/audit', { params: { page: 0, size: 5 } })).data.items; }
async function refresh() {
  loading.value = true; error.value = '';
  try { policy.value = (await api.get<AiPolicy>('/admin/ai/settings')).data; dailyLimit.value = policy.value.dailyLimit; await Promise.all([loadMembers(), loadAudit()]); loaded.value = true; }
  catch (reason) { error.value = apiMessage(reason); } finally { loading.value = false; }
}
async function saveDefault() {
  if (!quotaLimitValid(dailyLimit.value)) { error.value = '每日额度必须为 0 至 10000 的整数。'; return; }
  busy.value = true; error.value = ''; message.value = '';
  try { policy.value = (await api.put<AiPolicy>('/admin/ai/settings', { dailyLimit: dailyLimit.value, version: policy.value.version })).data; message.value = '默认额度已保存，对下一次请求生效；当天用量未重置。'; await Promise.all([loadMembers(), loadAudit()]); }
  catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; }
}
function edit(member: AiMember) { selected.value = member; editor.inherit = member.quota.overrideLimit === null; editor.dailyLimit = member.quota.overrideLimit ?? member.quota.dailyLimit; editor.paused = member.quota.paused; error.value = ''; message.value = ''; }
async function saveMember() {
  if (!selected.value) return;
  if (!editor.inherit && !quotaLimitValid(editor.dailyLimit)) { error.value = '每日额度必须为 0 至 10000 的整数。'; return; }
  busy.value = true; error.value = ''; message.value = '';
  try {
    await api.put(`/admin/ai/users/${selected.value.id}`, { dailyLimit: editor.inherit ? null : editor.dailyLimit, paused: editor.paused, version: selected.value.quota.version });
    selected.value = null; message.value = '成员额度已保存，对下一次请求生效；当天用量未重置。'; await Promise.all([loadMembers(), loadAudit()]);
  } catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; }
}
async function search(next = 0) { loading.value = true; error.value = ''; page.value = next; try { await loadMembers(); } catch (reason) { error.value = apiMessage(reason); } finally { loading.value = false; } }
onMounted(refresh);
</script>
<template>
  <section class="section ai-quota" aria-labelledby="ai-quota-title">
    <header class="section-title"><div><h2 id="ai-quota-title">AI 对话额度</h2><p>按北京时间每日零点更新，修改额度不会清零当天用量。</p></div><button class="btn" :disabled="busy || loading" @click="refresh">刷新额度</button></header>
    <div class="quota-intro"><p>额度为 0 表示不允许新对话。次数不等同于金额预算；调用开关和密钥由服务器管理。</p><p>占用包括正在处理和结果待核实的请求；暂停仅影响 AI 新请求，不影响计划使用。</p></div>
    <p v-if="error && !selected" class="error-text quota-notice" role="alert">{{ error }}</p><p v-if="message" class="helper-text quota-notice" role="status">{{ message }}</p>
    <template v-if="loaded">
      <form class="quota-default" @submit.prevent="saveDefault"><label>默认每日对话次数<input v-model.number="dailyLimit" type="number" min="0" max="10000" step="1" required inputmode="numeric" :disabled="busy || loading" /></label><button class="btn primary" :disabled="busy || loading">保存默认额度</button><p>未设置个人额度的成员使用此值。每次成功回复计 1 次，内部重试不重复计次。</p></form>
      <form class="quota-search" @submit.prevent="search()"><label>搜索成员<input v-model="query" maxlength="100" placeholder="姓名或邮箱" :disabled="busy || loading" /></label><button class="btn" :disabled="busy || loading">搜索</button></form>
      <div class="table-scroll quota-table"><table><thead><tr><th>成员</th><th>额度规则</th><th>每日额度</th><th>已用</th><th>占用</th><th>剩余</th><th>操作</th></tr></thead><tbody><tr v-for="member in members.items" :key="member.id"><td><strong>{{ member.displayName }}</strong><small>{{ member.email }}</small></td><td>{{ quotaRule(member.quota.overrideLimit) }}<small v-if="member.quota.paused">AI 已暂停</small></td><td>{{ member.quota.dailyLimit }}</td><td>{{ member.quota.used }}</td><td>{{ member.quota.pending }}</td><td class="quota-remaining">{{ member.quota.remaining }}</td><td><button class="btn" :disabled="busy || loading" :aria-label="`修改 ${member.displayName} 的额度`" @click="edit(member)">修改额度</button></td></tr></tbody></table></div>
      <div class="quota-cards"><article v-for="member in members.items" :key="member.id"><header><div><h3>{{ member.displayName }}</h3><p>{{ member.email }}</p></div><span v-if="member.quota.paused">AI 已暂停</span></header><p>{{ quotaRule(member.quota.overrideLimit) }}</p><dl><div><dt>每日额度</dt><dd>{{ member.quota.dailyLimit }}</dd></div><div><dt>已用</dt><dd>{{ member.quota.used }}</dd></div><div><dt>占用</dt><dd>{{ member.quota.pending }}</dd></div><div><dt>剩余</dt><dd class="quota-remaining">{{ member.quota.remaining }}</dd></div></dl><button class="btn" :disabled="busy || loading" :aria-label="`修改 ${member.displayName} 的额度`" @click="edit(member)">修改额度</button></article></div>
      <p v-if="!members.items.length" class="empty">没有找到成员。</p>
      <nav class="quota-paging" aria-label="成员额度分页"><span>共 {{ members.total }} 位成员 · 第 {{ page + 1 }} 页</span><button class="btn" :disabled="busy || loading || page === 0" @click="search(page - 1)">上一页</button><button class="btn" :disabled="busy || loading || (page + 1) * 10 >= members.total" @click="search(page + 1)">下一页</button></nav>
      <div class="quota-audit"><h3>最近额度修改</h3><p v-if="!audits.length">还没有修改记录。</p><ol v-else><li v-for="(audit, index) in audits" :key="index"><strong>{{ audit.target ?? '默认额度' }}</strong><span>{{ quotaRule(audit.beforeLimit) }} → {{ quotaRule(audit.afterLimit) }}<template v-if="audit.beforePaused !== audit.afterPaused">；{{ audit.afterPaused ? '暂停 AI' : '恢复 AI' }}</template></span><small>{{ audit.actor }} · {{ new Date(audit.createdAt).toLocaleString('zh-CN') }}</small></li></ol></div>
    </template><p v-else-if="loading" class="empty" role="status">正在读取额度…</p>
    <AppSheet :open="selected !== null" title="修改成员额度" title-id="ai-quota-editor-title" :busy="busy" @close="selected = null"><form class="sheet-form quota-editor" @submit.prevent="saveMember"><p>{{ selected?.displayName }} · {{ selected?.email }}</p><p v-if="error" class="error-text" role="alert">{{ error }}</p><label class="quota-check"><input v-model="editor.inherit" type="checkbox" :disabled="busy" />使用默认额度</label><label v-if="!editor.inherit">个人每日对话次数<input v-model.number="editor.dailyLimit" type="number" min="0" max="10000" step="1" inputmode="numeric" required :disabled="busy" /></label><label class="quota-check"><input v-model="editor.paused" type="checkbox" :disabled="busy" />暂停该成员 AI 新请求</label><p class="helper-text">不影响已接收的请求、账号登录或私人计划；不会清零当天用量。如提示资料已修改，请关闭面板并刷新额度。</p><div class="button-row sheet-actions"><button class="btn" type="button" :disabled="busy" @click="selected = null">取消</button><button class="btn primary" :disabled="busy">保存成员额度</button></div></form></AppSheet>
  </section>
</template>
<style scoped>
.ai-quota{margin-top:24px;min-width:0;background:#fff}.ai-quota .section-title{gap:16px;align-items:center}.ai-quota h2{margin:0}.ai-quota .section-title p{margin:8px 0 0;font-size:.8rem;color:var(--muted)}.quota-intro{padding:16px 20px;border-bottom:1px solid var(--line);background:#f7f7f8}.quota-intro p{margin:0;font-size:.8rem;line-height:1.7}.quota-intro p+p{margin-top:6px}.quota-notice{margin:16px 20px}.quota-default,.quota-search{display:flex;align-items:end;gap:12px;padding:20px;border-bottom:1px solid var(--line)}.quota-default label,.quota-search label,.quota-editor>label:not(.quota-check){display:grid;gap:8px;font-size:.8rem;font-weight:700}.quota-default input{max-width:170px}.quota-default p{flex:1;margin:0 0 4px;font-size:.75rem;color:var(--muted);line-height:1.6}.quota-search label{flex:1;max-width:420px}.ai-quota .btn{min-height:44px}.quota-table{max-width:100%}.quota-table td{overflow-wrap:anywhere;font-variant-numeric:tabular-nums}.quota-table small{display:block;margin-top:6px;color:var(--muted);font-size:.7rem}.quota-remaining{color:#002fa7;font-weight:700}.quota-cards{display:none}.quota-paging{display:flex;gap:10px;align-items:center;justify-content:flex-end;padding:16px 20px;border-top:1px solid var(--line)}.quota-paging span{margin-right:auto;font-size:.75rem;color:var(--muted)}.quota-audit{padding:20px;border-top:1px solid var(--line)}.quota-audit h3{font-size:.9rem;margin:0 0 14px}.quota-audit p{font-size:.8rem;color:var(--muted)}.quota-audit ol{list-style:none;padding:0;margin:0;display:grid;gap:12px}.quota-audit li{display:grid;gap:5px;font-size:.8rem;overflow-wrap:anywhere}.quota-audit small{color:var(--muted)}.quota-editor{display:grid;gap:20px}.quota-editor>p{margin:0;overflow-wrap:anywhere}.quota-check{display:flex;align-items:center;gap:10px;min-height:44px;font-size:.85rem}.quota-check input{width:20px;height:20px;flex-shrink:0}
@media(max-width:900px){.ai-quota .section-title{padding:16px;align-items:start;flex-wrap:wrap}.quota-intro,.quota-audit{padding:16px}.quota-default{display:grid;grid-template-columns:1fr;padding:16px}.quota-default input{max-width:none}.quota-default p{margin:0}.quota-search{padding:16px;align-items:end}.quota-search label{min-width:0}.quota-table{display:none}.quota-cards{display:grid}.quota-cards article{padding:16px;border-bottom:1px solid var(--line);min-width:0}.quota-cards header{display:flex;gap:12px;justify-content:space-between;align-items:start}.quota-cards h3{font-size:.95rem;margin:0}.quota-cards p{font-size:.75rem;color:var(--muted);overflow-wrap:anywhere;margin:8px 0}.quota-cards header>span{font-size:.75rem;white-space:nowrap;color:#002fa7}.quota-cards dl{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));margin:16px 0;border-top:1px solid var(--line);border-bottom:1px solid var(--line)}.quota-cards dl>div{padding:12px 6px;border-right:1px solid var(--line)}.quota-cards dl>div:first-child{padding-left:0}.quota-cards dl>div:last-child{border-right:0}.quota-cards dt{font-size:.65rem;color:var(--muted)}.quota-cards dd{margin:8px 0 0;font-size:1.45rem;font-variant-numeric:tabular-nums}.quota-cards article>.btn{width:100%}.quota-paging{padding:16px;flex-wrap:wrap}.quota-paging span{width:100%}}
</style>
