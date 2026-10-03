<script setup lang="ts">
import { useRouter } from 'vue-router';
import { workspace, workspaceSwitchTarget } from '@/workspace';
import { requiredClientUpdate } from '@/clientUpdates';
import type { WorkspaceMode } from '@/workspaceModel';
const router = useRouter();
async function change(event: Event) {
  const value = (event.target as HTMLSelectElement).value;
  if (requiredClientUpdate() || !['plan', 'route'].includes(value)) return;
  await router.push(await workspaceSwitchTarget(value as WorkspaceMode));
}
</script>
<template><div class="workspace-switcher"><select aria-label="工作模式" :value="workspace.mode" :disabled="requiredClientUpdate()" @change="change"><option value="plan">周期计划</option><option value="route">目标路线</option></select></div></template>
