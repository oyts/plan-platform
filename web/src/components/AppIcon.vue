<script setup lang="ts">
defineProps<{ name: 'today' | 'schedule' | 'plans' | 'more' | 'close' }>();
</script>
<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
    <template v-if="name === 'today'"><path d="m3 10 9-7 9 7v10H3Z"/><path d="M9 20v-7h6v7"/></template>
    <template v-else-if="name === 'schedule'"><rect x="3" y="5" width="18" height="16" rx="1"/><path d="M7 3v4m10-4v4M3 10h18m-13 5h3m3 0h2"/></template>
    <template v-else-if="name === 'plans'"><rect x="4" y="3" width="16" height="18" rx="1"/><path d="m7 9 1 1 2-2m3 1h4m-10 6 1 1 2-2m3 1h4"/></template>
    <template v-else-if="name === 'more'"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></template>
    <path v-else d="m6 6 12 12M6 18 18 6"/>
  </svg>
</template>
