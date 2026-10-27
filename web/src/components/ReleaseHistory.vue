<script setup lang="ts">
import {computed} from 'vue';
import {version} from '../../package.json';
import {clientUpdates} from '@/clientUpdates';
import {isNativeClient} from '@/desktop';
import {visibleHistory} from '@/releaseNotes';
const installed=computed(()=>isNativeClient()?clientUpdates.info?.version??'':version);
const entries=computed(()=>visibleHistory(installed.value,isNativeClient()?clientUpdates.info:null));
</script>
<template><section id="release-history" class="release-history section" aria-label="版本说明与历史"><h2>版本说明与历史</h2><p>{{isNativeClient()?'按本机安装版本／构建展示，不把服务器版本当作已安装。':'按当前已加载的网页界面版本展示，不代表后台已更新或网页壳已刷新。'}} 说明来自随包文本；缺少旧版本说明不会阻止安全更新。</p><p v-if="!entries.length">本机版本暂没有随包说明，请核对安装版本；未知不等于更新成功。</p><details v-for="entry in entries" :key="entry.version" :open="entry.version===installed"><summary>{{entry.version}} · {{entry.date}}</summary><ul><li v-for="item in entry.highlights" :key="item">{{item}}</li></ul><p v-for="item in entry.cautions" :key="item" class="helper-text">{{item}}</p></details></section></template>
<style scoped>.release-history{padding:24px;scroll-margin-top:24px}.release-history>p{color:var(--muted);line-height:1.7}.release-history details{padding:18px 0;border-top:1px solid var(--line)}summary{font-weight:700;cursor:pointer}li{margin:10px 0;overflow-wrap:anywhere}@media(max-width:700px){.release-history{padding:18px}}</style>
