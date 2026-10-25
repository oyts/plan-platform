<script setup lang="ts">
import {onMounted,onUnmounted,ref} from 'vue';
import {useRouter} from 'vue-router';
import {api,apiMessage,currentQueueOwner,serverScope} from '@/api/client';
import {workspaceEntry} from '@/workspace';
import {adminNavigation} from '@/adminCenter';
const router=useRouter(),ready=ref(false),allowed=ref(false),error=ref('');let active=true;
async function verify(){ready.value=false;allowed.value=false;error.value='';const owner=currentQueueOwner(),scope=serverScope();try{const r=await api.get<{allowed:boolean}>('/admin/access');if(active&&owner===currentQueueOwner()&&scope===serverScope())allowed.value=r.data.allowed===true;}catch(e){if(active)error.value=apiMessage(e);}finally{if(active)ready.value=true;}}
async function personal(){await router.push(await workspaceEntry());}
onMounted(verify);onUnmounted(()=>{active=false;});
</script>
<template><div class="admin-shell"><header class="admin-header"><div><strong>计划舱</strong><span>管理中心</span></div><button class="btn" @click="personal">返回个人工作区</button></header>
<p v-if="!ready" class="admin-gate" role="status">正在联网核对管理权限…</p><section v-else-if="!allowed" class="admin-gate" role="alert"><h1>管理中心暂不可访问</h1><p>{{error||'当前账号没有管理权限。'}}</p><p>管理操作需要联网并经服务端验证；不会使用离线缓存或提交同步队列。</p><button class="btn" @click="verify">重新核对权限</button></section>
<div v-else class="admin-body"><nav class="admin-nav" aria-label="管理导航"><router-link v-for="item in adminNavigation" :key="item.path" :to="item.path" :class="{selected:router.currentRoute.value.path===item.path}"><strong>{{item.label}}</strong><span>{{item.description}}</span></router-link></nav><section class="admin-content" aria-label="管理内容"><router-view/></section></div></div></template>
<style scoped>
.admin-shell{min-height:100vh;background:#f7f7f8}.admin-header{display:flex;justify-content:space-between;gap:20px;align-items:center;padding:18px 28px;border-bottom:1px solid var(--line-strong);background:#fff}.admin-header>div{display:flex;align-items:baseline;gap:18px}.admin-header strong{color:#002fa7}.admin-header span{font-size:1.25rem;font-weight:800}.admin-header .btn{min-height:44px}.admin-body{display:grid;grid-template-columns:220px minmax(0,1fr)}.admin-nav{border-right:1px solid var(--line);background:#fff;align-self:start;position:sticky;top:0}.admin-nav a{display:grid;gap:7px;padding:22px;text-decoration:none;border-bottom:1px solid var(--line);color:var(--ink)}.admin-nav span{font-size:.75rem;line-height:1.6;color:var(--muted)}.admin-nav .selected{border-left:4px solid #002fa7;background:var(--blue-soft);padding-left:18px;color:#002fa7}.admin-content{min-width:0}.admin-content :deep(.page){padding:28px 24px 60px}.admin-content :deep(h1){font-size:clamp(1.8rem,3.6vw,3rem);line-height:1.15}.admin-gate{padding:32px;max-width:900px;margin:auto}.admin-gate h1{font-size:2rem;line-height:1.3}
@media(max-width:900px){.admin-body{grid-template-columns:minmax(0,1fr)}.admin-header{padding:16px;align-items:flex-start}.admin-header>div{display:grid;gap:4px}.admin-nav{position:static;border-right:0;display:grid;grid-template-columns:repeat(2,minmax(0,1fr))}.admin-nav a{padding:14px;min-height:70px;border-right:1px solid var(--line);min-width:0;overflow-wrap:anywhere}.admin-nav span{display:none}.admin-nav .selected{padding-left:10px}.admin-content :deep(.page){padding:20px 16px 48px}.admin-gate{padding:24px 16px}}
</style>
