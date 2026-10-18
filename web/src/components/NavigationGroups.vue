<script setup lang="ts">
import type {NavigationGroup} from '@/navigationCatalog';
import {requiredClientUpdate,updateRouteAllowed} from '@/clientUpdates';
defineProps<{groups:NavigationGroup[]}>();
defineEmits<{navigate:[]}>();
</script>
<template>
  <section v-for="group in groups" :key="group.title" class="navigation-group">
    <h3>{{group.title}}</h3>
    <nav class="feature-menu" :aria-label="group.title">
      <template v-for="item in group.items" :key="item.path">
        <router-link v-if="!requiredClientUpdate()||updateRouteAllowed(item.path)" :to="item.path" :aria-label="item.label" @click="$emit('navigate')"><strong>{{item.label}}</strong><span>{{item.description}}</span></router-link>
        <div v-else class="feature-menu-disabled" aria-disabled="true"><strong>{{item.label}}</strong><span>更新客户端后可用</span></div>
      </template>
    </nav>
  </section>
</template>
<style>
.navigation-group{margin:0 0 24px}.navigation-group h3{color:var(--muted);font-size:.85rem;margin:0 0 10px}.feature-menu{display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr));border-top:1px solid var(--line);border-left:1px solid var(--line)}.feature-menu a,.feature-menu-disabled{display:grid;gap:6px;align-content:center;min-width:0;min-height:78px;padding:14px;border-right:1px solid var(--line);border-bottom:1px solid var(--line);color:var(--ink);text-decoration:none;overflow-wrap:anywhere}.feature-menu a.router-link-active{background:var(--blue-soft);color:var(--blue)}.feature-menu span{font-size:.75rem;font-weight:400;color:var(--muted);line-height:1.5}.feature-menu-disabled{opacity:.5}
</style>
