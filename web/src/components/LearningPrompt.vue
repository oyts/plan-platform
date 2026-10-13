<script setup lang="ts">
import { computed, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { bindLearning, dismissLesson, learning, resetLearning } from '@/learning';
import { lessons, lessonForPath } from '@/learningModel';
import { requiredClientUpdate } from '@/clientUpdates';
const route = useRoute(); const auth = useAuthStore();
watch(() => auth.user?.id, id => { if (id) void bindLearning(); else resetLearning(); }, { immediate: true });
const lesson = computed(() => lessons.find(item => item.id === lessonForPath(route.path)));
const visible = computed(() => auth.user && learning.ready && lesson.value && !requiredClientUpdate() && !learning.progress[lesson.value.id].dismissed && !learning.progress[lesson.value.id].completed);
</script>
<template>
  <aside v-if="visible && lesson" class="learning-prompt" aria-label="首次使用教学">
    <div><strong>第一次使用{{ lesson.title }}？</strong><p>用“{{ lesson.example }}”练习 {{ lesson.steps.length }} 步，不会改变真实数据。</p></div>
    <div class="button-row"><router-link class="btn primary" :to="`/learn/${lesson.id}`">开始教学</router-link><button type="button" class="btn" @click="dismissLesson(lesson.id)">稍后再看</button></div>
    <small>随时从“使用教学”重新打开。</small>
  </aside>
</template>
<style scoped>
.learning-prompt{margin:20px auto 0;max-width:1240px;padding:18px 24px;border-left:4px solid #002fa7;background:#f1f4fb;display:flex;align-items:center;gap:20px;flex-wrap:wrap}.learning-prompt>div:first-child{flex:1;min-width:220px}.learning-prompt p{margin:6px 0;font-size:14px}.learning-prompt small{color:#555;width:100%}
@media(max-width:900px){.learning-prompt{margin:12px 16px 0;padding:16px;gap:12px}.learning-prompt .button-row{width:100%;flex-wrap:wrap}.learning-prompt .btn{flex:1}}
</style>
