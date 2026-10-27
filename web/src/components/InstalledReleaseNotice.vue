<script setup lang="ts">
import {computed,onMounted,ref} from 'vue';
import {version} from '../../package.json';
import {isNativeClient} from '@/desktop';
import {clientUpdates} from '@/clientUpdates';
import {installedRelease} from '@/releaseNotes';
const visible=ref(false),native=isNativeClient();
const installed=computed(()=>native?clientUpdates.info?.version??'':version);
const release=computed(()=>installedRelease(installed.value,native?clientUpdates.info:null));
onMounted(()=>{
 if(!release.value)return;
 const key=JSON.stringify([native?clientUpdates.info?.updateChannel??'native':'web',installed.value,native?clientUpdates.info?.versionCode??'':'']);
 try{const raw=JSON.parse(localStorage.getItem('plan-platform:release-highlights-seen')??'[]');const seen=Array.isArray(raw)?raw.filter(v=>typeof v==='string').slice(-24):[];if(seen.includes(key))return;localStorage.setItem('plan-platform:release-highlights-seen',JSON.stringify([...seen,key].slice(-24)));visible.value=true;}catch{/* No persistent storage: history remains available, no repetitive prompt. */}
});
</script>
<template><aside v-if="visible&&release" class="installed-release" aria-label="本版更新亮点" role="status"><header><strong>本版更新亮点 {{installed}}</strong><span>{{release.date}}</span></header><ul><li v-for="item in release.highlights" :key="item">{{item}}</li></ul><p>{{native?'说明对应本机安装版本，不代表服务器版本或新包安装结果。':'说明对应当前网页界面；仍在等待刷新的网页壳不会被当作已升级。'}}</p><div class="button-row"><router-link class="btn" to="/help#release-history">回看版本说明</router-link><button class="btn" type="button" @click="visible=false">知道了</button></div></aside></template>
<style scoped>.installed-release{margin:18px 28px;padding:18px;border:1px solid var(--line-strong);background:var(--paper)}header{display:flex;justify-content:space-between;gap:12px}header strong{color:var(--blue)}header span,p{font-size:.85rem;color:var(--muted)}ul{padding-left:20px}li{line-height:1.7;overflow-wrap:anywhere}.button-row{flex-wrap:wrap}@media(max-width:700px){.installed-release{margin:12px}header{display:grid}}</style>
