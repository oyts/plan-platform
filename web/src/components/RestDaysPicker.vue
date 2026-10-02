<script setup lang="ts">
import { WEEKDAYS } from '@/restDays';
defineProps<{ disabled?: boolean }>();
const days = defineModel<number[]>({ required: true });
</script>
<template>
  <fieldset class="rest-days" :disabled="disabled">
    <legend>每周休息日</legend>
    <div class="weekdays"><label v-for="(label, index) in WEEKDAYS" :key="label" :class="{ selected: days.includes(index + 1) }"><input v-model="days" type="checkbox" :value="index + 1" />{{ label }}</label></div>
    <p>可多选；不选表示没有固定休息日。休息日仍可安排任务和补做，不会删除已有任务。</p>
  </fieldset>
</template>
<style scoped>
.rest-days{min-width:0;margin:0;padding:12px;border:1px solid #d7dce4}.rest-days legend{padding:0 6px;color:#343840;font-size:.85rem;font-weight:700}.weekdays{display:flex;flex-wrap:wrap;gap:6px}.weekdays label{display:flex;align-items:center;gap:6px;padding:8px;border:1px solid #9ba3b0;background:#fff;color:#343840;cursor:pointer}.weekdays label.selected{border-color:#002fa7;color:#002fa7;background:#e8eeff}.weekdays input{width:16px;min-height:16px;height:16px;margin:0;accent-color:#002fa7}.rest-days p{margin:10px 0 0;font-size:.78rem;line-height:1.6;color:#667085}.rest-days:disabled{opacity:.65}
</style>
