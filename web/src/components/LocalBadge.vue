<script setup lang="ts">
defineProps<{ state?: 'PENDING' | 'CONFLICT' | 'FAILED' }>();
</script>
<template><router-link v-if="state" class="local-badge" to="/sync">{{ state === 'PENDING' ? '本地修改 · 待同步' : state === 'CONFLICT' ? '同步冲突 · 待处理' : '同步失败 · 待重试' }}</router-link></template>
<style scoped>.local-badge{display:inline-block;font-size:12px;border:1px solid #002fa7;padding:3px 7px;margin:5px 0;color:#002fa7;text-decoration:none}</style>
