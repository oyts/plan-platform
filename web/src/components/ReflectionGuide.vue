<script setup lang="ts">
import {onMounted,onUnmounted,ref} from 'vue';
import {api,currentQueueOwner,serverScope} from '@/api/client';
import {requiresConnection} from '@/connectionState';
import {queueCount} from '@/offlineQueue';
const owner=currentQueueOwner(),scope=serverScope(),guide=ref<{message:string;path:string}|null>(null);let active=true,timer:ReturnType<typeof setTimeout>|undefined;
async function check(){guide.value=null;if(!owner||!active||owner!==currentQueueOwner()||scope!==serverScope()||requiresConnection())return;try{if(await queueCount(owner)>0)return;const value=(await api.post<{message:string;path:string}[]>('/reflection/guides/claim')).data;if(active&&owner===currentQueueOwner()&&Array.isArray(value)&&value[0]?.path?.match(/^\/reflection\?kind=DAY&date=\d{4}-\d{2}-\d{2}$/))guide.value=value[0];}catch{/* Optional guidance never blocks manual work or a legacy server. */}}
function refresh(){clearTimeout(timer);timer=setTimeout(()=>void check(),1000);}
onMounted(()=>{void check();window.addEventListener('plan-platform:business-changed',refresh);});onUnmounted(()=>{active=false;clearTimeout(timer);window.removeEventListener('plan-platform:business-changed',refresh);});
</script>
<template><aside v-if="guide" class="reflection-guide" aria-label="日复盘引导"><p>{{guide.message}}</p><router-link class="btn" :to="guide.path">回顾今天</router-link><button class="btn" @click="guide=null">暂时不用</button></aside></template>
<style scoped>.reflection-guide{margin:16px 24px;padding:16px 20px;background:#f7f7f8;border-left:3px solid #002fa7;display:flex;align-items:center;gap:16px}.reflection-guide p{flex:1;line-height:1.8}@media(max-width:700px){.reflection-guide{margin:12px;flex-wrap:wrap}.reflection-guide p{flex-basis:100%}}</style>
