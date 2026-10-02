<script setup lang="ts">
import { clientUpdates as state, dismissClientUpdate } from '@/clientUpdates';
</script>
<template>
  <aside v-if="state.update && (state.update.mandatory || state.dismissedVersion !== state.update.version)" class="client-update-notice" aria-label="客户端版本提示" :role="state.update.mandatory ? 'alert' : 'status'">
    <div><strong>{{ state.update.mandatory ? '当前版本已停止支持，请更新客户端' : `发现新版本 ${state.update.version}` }}</strong><p>{{ state.update.mandatory ? '暂停新增和修改；本地数据与待同步操作保留，可先查看同步中心或导出。' : '可现在更新，也可稍后处理。不会自动安装或清除数据。' }}</p></div>
    <div class="button-row"><router-link class="btn primary" to="/desktop">查看更新</router-link><button v-if="!state.update.mandatory" class="btn" type="button" @click="dismissClientUpdate">稍后处理</button></div>
  </aside>
</template>
<style scoped>
.client-update-notice{display:flex;justify-content:space-between;align-items:center;gap:20px;margin:18px 28px;padding:18px;border:1px solid #002fa7;background:#fff;color:#002fa7}.client-update-notice p{margin:6px 0 0;font-size:.82rem}.button-row{flex-shrink:0}@media(max-width:600px){.client-update-notice{margin:12px;display:grid}.button-row{flex-wrap:wrap}}
</style>
