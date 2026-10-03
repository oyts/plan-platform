<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, apiMessage } from '@/api/client';
import { requiresConnection } from '@/connectionState';
import type { Mainline, MainlineItem } from '@/types';
const route = useRoute(); const router = useRouter();
const lines = ref<Mainline[]>([]); const selectedId = ref(typeof route.query.line === 'string' ? route.query.line : ''); const error = ref('');
const lineDraft = reactive({ name: '', description: '', color: '#002FA7' });
const itemDraft = reactive({ title: '', description: '', acceptanceText: '完成本步骤', plannedMinutes: 60 });
const selected = computed(() => lines.value.find(line => line.id === selectedId.value) ?? lines.value[0]);
function showError(e: unknown) { error.value = apiMessage(e).replace(/主线/g, '目标路线'); }
async function selectLine(id: string) { selectedId.value = id; await router.replace({ path: '/mainlines', query: { line: id } }); }
async function load() {
  lines.value = (await api.get<Mainline[]>('/mainlines')).data;
  if (selected.value && selectedId.value !== selected.value.id) await selectLine(selected.value.id);
}
async function createLine() { try { const { data } = await api.post<Mainline>('/mainlines', lineDraft); lineDraft.name = ''; lineDraft.description = ''; selectedId.value = data.id; await load(); await selectLine(data.id); } catch (e) { showError(e); } }
async function addItem() { if (!selected.value) return; try { await api.post('/mainlines/' + selected.value.id + '/items', { title: itemDraft.title, description: itemDraft.description, acceptance: itemDraft.acceptanceText.split('\n').filter(Boolean), plannedMinutes: itemDraft.plannedMinutes, sortOrder: selected.value.items.length }); itemDraft.title = ''; itemDraft.description = ''; await load(); } catch (e) { showError(e); } }
async function action(item: MainlineItem, type: 'start' | 'pause' | 'complete') { try { if (type === 'complete') await api.post('/mainlines/items/' + item.id + '/complete', { note: '', version: item.version }); else await api.post('/mainlines/items/' + item.id + '/' + type, null, { params: { version: item.version } }); await load(); } catch (e) { showError(e); } }
async function removeLine() { if (!selected.value || !confirm('将目标路线“' + selected.value.name + '”移入回收站？')) return; try { await api.delete('/mainlines/' + selected.value.id); selectedId.value = ''; await load(); if (!selected.value) await router.replace('/mainlines'); } catch (e) { showError(e); } }
async function removeItem(item: MainlineItem) { if (!confirm('将步骤“' + item.title + '”移入回收站？')) return; try { await api.delete('/mainlines/items/' + item.id); await load(); } catch (e) { showError(e); } }
watch(() => route.query.line, id => { selectedId.value = typeof id === 'string' ? id : ''; });
onMounted(() => load().catch(showError));
</script>
<template><div class="page"><section class="page-head"><div><h1>目标路线</h1><p>不设结束日期，按自己的节奏逐步推进。</p></div><p>当前路线沿用顺序解锁：完成前置步骤后继续下一步。</p></section>
  <p v-if="error" class="error-text">{{ error }}</p>
  <div class="mainline-layout"><aside class="line-list" aria-label="路线列表">
    <button v-for="line in lines" :key="line.id" :class="{ active: selected?.id === line.id }" @click="selectLine(line.id)"><i :style="{ background: line.color }"/><strong>{{ line.name }}</strong><span>{{ line.completed }}/{{ line.total }}</span></button>
    <form @submit.prevent="createLine"><label>路线名称<input v-model="lineDraft.name" placeholder="新路线名称" required/></label><label>路线说明<textarea v-model="lineDraft.description" placeholder="希望达成什么目标"/></label><div class="button-row"><input v-model="lineDraft.color" type="color" aria-label="路线颜色"/><button class="btn primary">新建路线</button></div></form>
  </aside><section v-if="selected" class="section"><header class="section-title"><div><p class="helper-text">{{ selected.completed }} / {{ selected.total }} 步骤</p><h2>{{ selected.name }}</h2><p>{{ selected.description }}</p></div><button class="text-btn danger-text" :disabled="requiresConnection()" @click="removeLine">移入回收站</button></header>
    <article v-for="(item, index) in selected.items" :key="item.id" class="mainline-step" :class="{ locked: !item.unlocked }"><strong class="step-number">{{ String(index + 1).padStart(2, '0') }}</strong><div><span>{{ item.status }}</span><h3>{{ item.title }}</h3><p>{{ item.description }}</p></div><div class="button-row"><button v-if="item.status !== 'RUNNING' && item.status !== 'COMPLETED'" class="btn" :disabled="!item.unlocked || requiresConnection()" @click="action(item, 'start')">开始</button><button v-if="item.status === 'RUNNING'" class="btn" :disabled="requiresConnection()" @click="action(item, 'pause')">暂停</button><button v-if="item.status !== 'COMPLETED'" class="btn primary" :disabled="!item.unlocked || requiresConnection()" @click="action(item, 'complete')">完成</button><button class="text-btn danger-text" :disabled="requiresConnection()" @click="removeItem(item)">移入回收站</button></div></article>
    <form class="editor-card horizontal-editor" @submit.prevent="addItem"><h3>添加下一步</h3><label>标题<input v-model="itemDraft.title" required/></label><label>说明<input v-model="itemDraft.description"/></label><label>参考分钟<input v-model.number="itemDraft.plannedMinutes" type="number" min="1"/></label><button class="btn primary">添加</button></form><p class="helper-text">参考分钟用于估算一次练习的投入，不代表每日配额或完成期限。</p>
  </section><section v-else class="empty section">先创建一条目标路线。</section></div>
</div></template>
