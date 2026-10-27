<script setup lang="ts">
import { clientUpdates as state, dismissClientUpdate,ignoreClientUpdate,clientNoticeVisible } from '@/clientUpdates';
import {targetHighlights} from '@/releaseNotes';
</script>
<template>
  <aside v-if="state.update && clientNoticeVisible()" class="client-update-notice" aria-label="客户端版本提示" :role="state.update.mandatory ? 'alert' : 'status'">
    <div><strong>{{ state.update.mandatory ? '当前版本已停止支持，请更新客户端' : `发现新版本 ${state.update.version}` }}</strong><p>{{ state.update.mandatory ? '暂停新增和修改；本地数据与待同步操作保留，可先查看同步中心或导出。' : '可现在更新，也可稍后处理。不会自动安装或清除数据。' }}</p></div>
    <ul v-if="targetHighlights(state.update).length" class="update-highlights"><li v-for="(item,index) in targetHighlights(state.update).slice(0,3)" :key="index">{{item}}</li></ul>
    <div class="button-row"><router-link class="btn primary" to="/desktop">查看更新</router-link><template v-if="!state.update.mandatory"><button class="btn" type="button" @click="dismissClientUpdate">稍后处理</button><button class="btn" type="button" @click="ignoreClientUpdate">忽略此版本</button></template></div>
  </aside>
</template>
<style scoped>
.client-update-notice{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:12px 20px;margin:18px 28px;padding:18px;border:1px solid #002fa7;background:#fff;color:#002fa7}.client-update-notice p{margin:6px 0 0;font-size:.82rem}.update-highlights{grid-column:1;margin:0;padding-left:20px;font-size:.85rem;overflow-wrap:anywhere}.button-row{grid-column:2;grid-row:1/3;align-self:center;flex-wrap:wrap}@media(max-width:800px){.client-update-notice{margin:12px;grid-template-columns:minmax(0,1fr)}.button-row{grid-column:1;grid-row:auto}}
</style>
