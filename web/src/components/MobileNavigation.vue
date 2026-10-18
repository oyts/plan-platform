<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { requiredClientUpdate, updateRouteAllowed } from '@/clientUpdates';
import { isNativeClient } from '@/desktop';
import { workspace } from '@/workspace';
import {primaryNavigation,navigationGroups} from '@/navigationCatalog';
import NavigationGroups from './NavigationGroups.vue';
import AppIcon from './AppIcon.vue';
import AppSheet from './AppSheet.vue';
import PwaControl from './PwaControl.vue';
const emit = defineEmits<{ logout: [] }>();
const auth = useAuthStore(); const route = useRoute(); const open = ref(false);
const primary = computed(() => primaryNavigation(workspace.mode));
const groups=computed(()=>navigationGroups(workspace.mode,auth.user?.role==='ADMIN',isNativeClient()));
const secondary=computed(()=>groups.value.flatMap(group=>group.items));
const moreActive = computed(() => secondary.value.some(item => item.path === route.path));
function allowed(path: string) { return !requiredClientUpdate() || updateRouteAllowed(path); }
watch(() => route.fullPath, () => { open.value = false; });
function logout() { open.value = false; emit('logout'); }
</script>
<template>
  <nav class="mobile-navigation" :class="{ 'mobile-navigation--todo': workspace.mode === 'todo' }" aria-label="手机主导航">
    <template v-for="item in primary" :key="item.path">
      <router-link v-if="allowed(item.path)" :to="item.path" :class="{ active: item.path === '/' ? route.path === '/' : route.path.startsWith(item.path) }"><AppIcon :name="item.icon"/><span>{{ item.label }}</span></router-link>
      <button v-else disabled type="button"><AppIcon :name="item.icon"/><span>{{ item.label }}</span></button>
    </template>
    <button type="button" :class="{ active: moreActive || open }" :aria-expanded="open" aria-haspopup="dialog" @click="open = true"><AppIcon name="more"/><span>更多</span></button>
  </nav>
  <AppSheet :open="open" title="更多功能" title-id="mobile-menu-title" @close="open = false">
    <p class="mobile-account-name">{{ auth.user?.displayName }}</p>
    <NavigationGroups :groups="groups" @navigate="open=false"/>
    <PwaControl v-if="!isNativeClient()"/>
    <button class="btn mobile-logout" type="button" @click="logout">{{ auth.user?.displayName }} · 退出</button>
  </AppSheet>
</template>
<style scoped>@media(max-width:900px){.mobile-navigation--todo{grid-template-columns:repeat(2,minmax(0,1fr))}}</style>
