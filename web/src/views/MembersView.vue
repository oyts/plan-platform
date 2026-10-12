<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { api, apiMessage } from '@/api/client';
import type { Invitation } from '@/types';
import AppSheet from '@/components/AppSheet.vue';

const rows = ref<Invitation[]>([]); const error = ref(''); const message = ref(''); const busy = ref(false); const oneTimeLink = ref(''); const draft = reactive({ email: '', expiresInHours: 168 });
const creating = ref(false); const loading = ref(true);
async function load() { rows.value = (await api.get<Invitation[]>('/admin/invitations')).data; }
function status(row: Invitation) { return row.revokedAt ? '已撤销' : row.acceptedAt ? '已接受' : new Date(row.expiresAt).getTime() <= Date.now() ? '已过期' : '有效'; }
async function create() { busy.value = true; error.value = ''; message.value = ''; oneTimeLink.value = ''; try { const { data } = await api.post<Invitation>('/admin/invitations', draft); draft.email = ''; oneTimeLink.value = data.registrationUrl ?? ''; message.value = data.registrationUrl ? '邮件服务不可用，注册链接仅在当前页面显示一次，请通过可信渠道发送给受邀人。' : '邀请邮件已发送。'; await load(); } catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; } }
async function revoke(row: Invitation) { if (!confirm(`撤销给 ${row.email} 的邀请？`)) return; busy.value = true; try { await api.delete(`/admin/invitations/${row.id}`); await load(); } catch (reason) { error.value = apiMessage(reason); } finally { busy.value = false; } }
async function copyLink() { try { await navigator.clipboard.writeText(oneTimeLink.value); message.value = '注册链接已复制。'; } catch { error.value = '浏览器无法访问剪贴板，请手动复制链接。'; } }
onMounted(() => load().catch(reason => error.value = apiMessage(reason)).finally(() => loading.value = false));
</script>
<template>
  <div class="page members-page">
    <header class="page-head"><div><h1>成员邀请</h1><p>管理邀请，不查看成员的私人计划。周期计划、目标路线与今日待办始终属于各自账号。</p></div><button class="btn primary" :disabled="busy" @click="creating = true">创建邀请</button></header>
    <p v-if="error && !creating" class="error-text" role="alert">{{ error }}</p><p v-if="message && !creating" class="helper-text" role="status">{{ message }}</p>
    <div v-if="oneTimeLink && !creating" class="members-link-reminder"><p>本次注册链接只在当前页面保存，请及时发送给受邀人。</p><button class="btn" @click="creating = true">查看本次注册链接</button></div>
    <section class="section invitation-ledger" aria-labelledby="invitation-ledger-title">
      <header class="section-title"><h2 id="invitation-ledger-title">邀请记录</h2><span :aria-label="`${rows.length} 条邀请`">{{ rows.length }}</span></header>
      <p v-if="loading" class="empty" role="status">正在读取邀请…</p>
      <template v-else-if="rows.length">
        <div class="table-scroll invitation-table"><table><thead><tr><th>受邀邮箱</th><th>状态</th><th>到期时间</th><th>创建人</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td>{{ row.email }}</td><td>{{ status(row) }}</td><td>{{ new Date(row.expiresAt).toLocaleString('zh-CN') }}</td><td>{{ row.createdBy }}</td><td><button v-if="status(row)==='有效'" class="text-btn danger-text" :disabled="busy" @click="revoke(row)">撤销</button></td></tr></tbody></table></div>
        <div class="invitation-cards"><article v-for="row in rows" :key="row.id" class="invitation-card"><header><span class="invitation-status" :class="{ active: status(row)==='有效' }">{{ status(row) }}</span><button v-if="status(row)==='有效'" class="btn danger" :disabled="busy" @click="revoke(row)">撤销</button></header><h3>{{ row.email }}</h3><dl><div><dt>到期时间</dt><dd>{{ new Date(row.expiresAt).toLocaleString('zh-CN') }}</dd></div><div><dt>创建人</dt><dd>{{ row.createdBy }}</dd></div></dl></article></div>
      </template>
      <p v-else-if="!error" class="empty">还没有邀请记录。创建邀请后，受邀人可使用链接注册。</p>
    </section>
    <AppSheet :open="creating" title="创建邀请" title-id="invite-editor-title" :busy="busy" @close="creating = false"><form class="sheet-form invitation-editor" @submit.prevent="create">
      <p v-if="error" class="error-text" role="alert">{{ error }}</p><p v-if="message" class="helper-text" role="status">{{ message }}</p>
      <label>受邀邮箱<input v-model="draft.email" type="email" autocomplete="email" inputmode="email" required /></label><label>有效小时数<input v-model.number="draft.expiresInHours" type="number" min="1" max="720" inputmode="numeric" required /></label><p class="helper-text">邀请有效期为 1 至 720 小时，默认 7 天。</p>
      <div v-if="oneTimeLink" class="invitation-link"><label>一次性注册链接<textarea :value="oneTimeLink" readonly rows="3" /></label><button class="btn" type="button" @click="copyLink">复制链接</button></div>
      <div class="button-row sheet-actions"><button class="btn" type="button" :disabled="busy" @click="creating = false">关闭</button><button class="btn primary" :disabled="busy">{{ busy ? '正在创建…' : '创建邀请' }}</button></div>
    </form></AppSheet>
  </div>
</template>
<style scoped>
.members-page .page-head p{max-width:640px;margin-bottom:0}.members-page .page-head>.btn{flex-shrink:0;min-height:44px}.invitation-ledger,.invitation-table{min-width:0;max-width:100%}.invitation-cards{display:none}.invitation-table td{overflow-wrap:anywhere}.invitation-editor{display:grid;gap:16px}.invitation-editor>label,.invitation-link label{display:grid;gap:8px;color:var(--muted);font-size:.8rem;font-weight:700}.invitation-editor .helper-text{margin:0}.invitation-link{display:grid;gap:12px;padding-top:16px;border-top:1px solid var(--line)}.invitation-link textarea{font-size:.8rem;overflow-wrap:anywhere}.invitation-link>.btn{justify-self:start;min-height:44px}.members-link-reminder{margin-top:18px;padding:16px;border:1px solid var(--line);background:var(--paper)}.members-link-reminder p{font-size:.85rem;color:var(--muted)}
@media(max-width:900px),(max-height:500px) and (pointer:coarse){
  .members-page .page-head{grid-template-columns:minmax(0,1fr);gap:16px}.members-page .page-head>.btn{justify-self:stretch}.invitation-ledger{margin-top:18px}.invitation-ledger .section-title{padding:16px;align-items:center;gap:12px}.invitation-ledger h2{font-size:1rem}.invitation-ledger .section-title>span{font-size:1.5rem;font-variant-numeric:tabular-nums}.invitation-table{display:none}.invitation-cards{display:grid}.invitation-card{min-width:0;padding:16px;border-bottom:1px solid var(--line)}.invitation-card:last-child{border-bottom:0}.invitation-card>header{display:flex;justify-content:space-between;align-items:center;gap:12px}.invitation-card .btn{min-height:44px}.invitation-status{color:var(--muted);font-size:.75rem;font-weight:700}.invitation-status.active{color:var(--blue)}.invitation-card h3{margin:12px 0;font-size:.95rem;line-height:1.5;overflow-wrap:anywhere}.invitation-card dl{display:grid;gap:8px;margin:0;font-size:.75rem}.invitation-card dl>div{display:grid;grid-template-columns:64px minmax(0,1fr);gap:12px}.invitation-card dt{color:var(--muted)}.invitation-card dd{margin:0;overflow-wrap:anywhere;line-height:1.5;font-variant-numeric:tabular-nums}.members-link-reminder{padding:14px}.members-link-reminder .btn{width:100%;min-height:44px}
}
</style>
