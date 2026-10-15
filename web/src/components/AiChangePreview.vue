<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { aiDifferences, type AiAdjustment, type AiDraft } from '@/aiPlanning';
const props = defineProps<{ adjustment: AiAdjustment; draft: AiDraft }>();
const changes = computed(() => aiDifferences(props.adjustment.original, props.draft));
const page = ref(0), pages = computed(() => Math.max(1, Math.ceil(changes.value.length / 10)));
const visible = computed(() => changes.value.slice(page.value * 10, page.value * 10 + 10));
watch(pages, count => { page.value = Math.min(page.value, count - 1); });
</script>
<template>
  <section class="ai-diff" aria-label="调整前后对照">
    <h3>调整前后对照</h3>
    <p class="helper-text">{{ changes.length }} 组变化 · {{ adjustment.original.items.length }} 项原事项保留 · {{ adjustment.protectedItems.length }} 项执行历史保护。不会删除原事项、重置进度或清除计时记录。</p>
    <p v-if="!changes.length" class="helper-text">目前没有改动。告诉 AI 需要怎样调整，或在下方手动修改草稿。</p>
    <article v-for="change in visible" :key="change.id"><header><strong>{{ change.title }}</strong><span>{{ change.kind === 'ADD' ? '新增' : '修改' }}</span></header><dl><div v-for="field in change.fields" :key="field.label"><dt>{{ field.label }}</dt><dd><span class="diff-before"><small>原内容</small>{{ field.before }}</span><span class="diff-after"><small>建议内容</small>{{ field.after }}</span></dd></div></dl></article>
    <div v-if="pages > 1" class="button-row"><button type="button" class="btn" :disabled="page === 0" @click="page--">上一组变化</button><span>{{ page + 1 }} / {{ pages }}</span><button type="button" class="btn" :disabled="page + 1 >= pages" @click="page++">下一组变化</button></div>
  </section>
</template>
<style scoped>
.ai-diff{border:1px solid #002fa7;padding:16px;min-width:0}.ai-diff h3{margin:0;font-size:1rem}.ai-diff article{border-top:1px solid var(--line);padding-top:14px;margin-top:14px}.ai-diff header{display:flex;justify-content:space-between;gap:12px;font-size:.85rem}.ai-diff header span{color:#002fa7;white-space:nowrap}.ai-diff dl{margin:12px 0}.ai-diff dt{font-size:.75rem;color:var(--muted);margin:12px 0 6px}.ai-diff dd{margin:0;display:grid;grid-template-columns:1fr 1fr;gap:12px;font-size:.8rem;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}.ai-diff dd>span{min-width:0;padding:10px;background:#f7f7f8}.ai-diff small{display:block;color:var(--muted);font-size:.7rem}.diff-after{border-left:2px solid #002fa7}.ai-diff .button-row{flex-wrap:wrap;align-items:center;gap:8px}@media(max-width:550px){.ai-diff dd{grid-template-columns:1fr}.ai-diff{padding:12px}}
</style>
