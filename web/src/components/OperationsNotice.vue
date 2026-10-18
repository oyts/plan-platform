<script setup lang="ts">
import {onMounted,onUnmounted,ref} from 'vue';
import {api,currentQueueOwner} from '@/api/client';
import {requiresConnection} from '@/connectionState';
const owner=currentQueueOwner(),count=ref(0);let active=true,timer:ReturnType<typeof setInterval>|undefined;
async function refresh(){if(requiresConnection()||document.visibilityState==='hidden')return;try{const result=(await api.get<{active:boolean}[]>('/admin/operations/alerts')).data;if(active&&currentQueueOwner()===owner)count.value=result.filter(item=>item.active).length;}catch{/* Administrative monitoring must never break ordinary business pages. */}}
onMounted(()=>{void refresh();timer=setInterval(()=>{void refresh();},300000);});onUnmounted(()=>{active=false;if(timer)clearInterval(timer);});
</script>
<template><aside v-if="count" class="operations-notice" role="status"><router-link to="/operations">运行状态有 {{count}} 项需要关注，查看备份与主机证据</router-link><span>站内提醒 · 不发送邮件</span></aside></template>
<style scoped>.operations-notice{border-bottom:1px solid #d8d8df;padding:16px 4%;display:flex;gap:20px;flex-wrap:wrap;background:#f7f7f8}.operations-notice a{color:#002fa7;font-weight:700}.operations-notice span{color:#646b77;font-size:13px}</style>
