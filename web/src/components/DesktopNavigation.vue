<script setup lang="ts">
import {computed,ref,watch} from 'vue';
import {useRoute} from 'vue-router';
import {useAuthStore} from '@/stores/auth';
import {workspace} from '@/workspace';
import {navigationGroups,primaryNavigation} from '@/navigationCatalog';
import {isNativeClient} from '@/desktop';
import {requiredClientUpdate,updateRouteAllowed} from '@/clientUpdates';
import AppSheet from './AppSheet.vue';
import NavigationGroups from './NavigationGroups.vue';
const auth=useAuthStore(),route=useRoute(),open=ref(false);
const groups=computed(()=>navigationGroups(workspace.mode,auth.user?.role==='ADMIN',isNativeClient()));
const primary=computed(()=>[...primaryNavigation(workspace.mode),{path:'/ai',label:'AI 规划'},{path:'/sync',label:'同步'}]);
const moreActive=computed(()=>!primary.value.some(item=>item.path===route.path||item.path==='/plans'&&route.path.startsWith('/plans/'))&&groups.value.some(group=>group.items.some(item=>item.path===route.path)));
watch(()=>route.fullPath,()=>{open.value=false;});
const allowed=(path:string)=>!requiredClientUpdate()||updateRouteAllowed(path);
</script>
<template>
  <nav class="desktop-navigation" aria-label="主导航">
    <template v-for="item in primary" :key="item.path"><router-link v-if="allowed(item.path)" :to="item.path">{{item.label}}</router-link><span v-else class="unavailable-link" aria-disabled="true">{{item.label}}</span></template>
    <button type="button" class="more-button" :class="{active:moreActive}" aria-haspopup="dialog" :aria-expanded="open" @click="open=true">更多</button>
  </nav>
  <AppSheet :open="open" title="更多功能" title-id="desktop-menu-title" @close="open=false"><NavigationGroups :groups="groups" @navigate="open=false"/></AppSheet>
</template>
<style scoped>
.desktop-navigation{min-width:0;overflow:visible!important}.desktop-navigation a,.unavailable-link,.more-button{flex:1;min-width:52px!important;min-height:72px;padding:0 8px;white-space:nowrap;text-align:center}.unavailable-link{display:grid;place-items:center;opacity:.45;border-right:1px solid var(--line)}.more-button{flex:0 0 66px;border:0;border-right:1px solid var(--line);background:var(--paper);font-weight:700;font-size:.85rem;cursor:pointer;color:var(--ink)}.more-button.active{box-shadow:inset 0 -4px var(--blue);color:var(--blue);background:var(--blue-soft)}@media(max-width:1000px){.desktop-navigation a,.unavailable-link,.more-button{padding:0 5px;min-width:47px!important}}
</style>
