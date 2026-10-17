<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { api, apiMessage, currentQueueOwner, serverScope } from '@/api/client';
import { requiresConnection } from '@/connectionState';
import { listOperations, onQueueChange } from '@/offlineQueue';
import { downloadFile, downloadErrorMessage } from '@/download';
import { backupRecordLabels, readPrivateBackup, MAX_BACKUP_BYTES, type BackupPreview, type RestoreReport, type WeeklyCopy } from '@/personalData';
import type { PageResponse } from '@/types';
const emit=defineEmits<{restored:[]}>();
const owner=currentQueueOwner(), scope=serverScope();let active=true,stopQueue:(()=>void)|undefined;
const busy=ref(''), error=ref(''), notice=ref(''), queue=ref<number|null>(null), fileName=ref(''), backup=ref<unknown>(null), preview=ref<BackupPreview|null>(null);
const mayPreview=ref(false), confirmed=ref(false), fileInput=ref<HTMLInputElement|null>(null), operation=ref(crypto.randomUUID());
const history=ref<PageResponse<RestoreReport>|null>(null),historyPage=ref(0),copies=ref<PageResponse<WeeklyCopy>|null>(null),copyPage=ref(0);
const inContext=()=>active&&owner===currentQueueOwner()&&scope===serverScope();
watch(mayPreview,value=>{if(!value){preview.value=null;confirmed.value=false;}});
const stamp=()=>new Date().toISOString().replace(/[-:.]/g,'').slice(0,15);
async function counts(){try{queue.value=owner?(await listOperations(owner)).length:0;}catch{queue.value=null;}}
async function choose(event:Event){
  backup.value=null;preview.value=null;confirmed.value=false;mayPreview.value=false;fileName.value='';error.value='';notice.value='';operation.value=crypto.randomUUID();
  const file=(event.target as HTMLInputElement).files?.[0];if(!file)return;busy.value='read';
  try{if(file.size>MAX_BACKUP_BYTES)throw new Error('备份文件不能超过 5 MiB。');const value=readPrivateBackup(await file.text(),file.size);if(inContext()){backup.value=value;fileName.value=file.name;}}
  catch(e){if(inContext())error.value=apiMessage(e);}finally{busy.value='';}
}
async function download(kind:'backup'|'routes'|'todos'|'weekly'){
  if(busy.value||requiresConnection())return;busy.value=kind;error.value='';notice.value='';
  try{const result=await api.get(kind==='backup'?'/data/backup':`/data/export/${kind}`,{responseType:'blob',timeout:60000});if(!inContext())return;
    await downloadFile(result.data as Blob,kind==='backup'?`plan-cabin-private-${stamp()}.json`:`plan-cabin-${kind}-${stamp()}.csv`);
    notice.value=kind==='backup'?'私有备份文件已交给系统保存。请确认保存位置并妥善保管；不包含本机待同步操作。':'导出文件已交给系统保存。';
  }catch(e){const message=await downloadErrorMessage(e);if(inContext())error.value=message??apiMessage(e);}finally{busy.value='';}
}
async function preflight(){
  if(!backup.value||!mayPreview.value||busy.value||requiresConnection())return;busy.value='preview';error.value='';notice.value='';confirmed.value=false;
  try{const value=(await api.post<BackupPreview>('/data/preview',backup.value,{timeout:60000})).data;if(inContext())preview.value=value;}
  catch(e){if(inContext())error.value=apiMessage(e);}finally{busy.value='';}
}
async function restore(){
  if(!backup.value||!preview.value||!mayPreview.value||!confirmed.value||busy.value||requiresConnection())return;busy.value='restore';error.value='';notice.value='';
  try{const result=(await api.post<RestoreReport>('/data/restore',{backup:backup.value,checksum:preview.value.checksum,operationKey:operation.value,confirmed:true},{timeout:120000})).data;
    if(inContext()){notice.value=`恢复批次 ${result.batchId} 已完成。相同文件重复确认不会重复建记录。原有内容和本机队列未改变。`;confirmed.value=false;emit('restored');await loadHistory();await loadCopies();}
  }catch(e){if(inContext())error.value=`${apiMessage(e)} 若发送后网络中断，请保留原文件，恢复连接后原样重试；服务端会核对文件指纹防重。`;}
  finally{busy.value='';}
}
async function loadHistory(){if(requiresConnection())return;try{const data=(await api.get<PageResponse<RestoreReport>>('/data/restores',{params:{page:historyPage.value}})).data;if(inContext())history.value=data;}catch(e){if(inContext())error.value=apiMessage(e);}}
async function loadCopies(){if(requiresConnection())return;try{const data=(await api.get<PageResponse<WeeklyCopy>>('/data/restored-weekly-reviews',{params:{page:copyPage.value}})).data;if(inContext())copies.value=data;}catch(e){if(inContext())error.value=apiMessage(e);}}
async function moveHistory(delta:number){historyPage.value+=delta;await loadHistory();}
async function moveCopies(delta:number){copyPage.value+=delta;await loadCopies();}
onMounted(()=>{void counts();stopQueue=onQueueChange(()=>void counts());});
onUnmounted(()=>{active=false;stopQueue?.();});
</script>
<template><section class="personal-data" aria-label="个人备份与恢复">
  <p v-if="error" class="error-text" role="alert">{{error}}</p><p v-if="notice" class="success-text" role="status">{{notice}}</p>
  <p v-if="requiresConnection()" class="data-notice">当前离线：仍可选择本机文件查看格式；云端导出、完整预检和恢复需要联网，不会加入离线队列。</p>
  <p class="data-notice">当前设备{{queue===null?'队列数量暂不可读取':`有 ${queue} 项待同步操作`}}。以下文件仅包含已同步数据，不会提交、清除或打包本机队列。私有 JSON 含计划与复盘正文，请勿公开分享。</p>
  <div class="data-layout"><section aria-labelledby="private-backup"><h2 id="private-backup">可恢复的私有备份</h2><p>保存三种模式的结构、休息日、进度、已结算流水和复盘。恢复时重新分配编号，归属当前账号，不覆盖已有内容。</p><p class="helper-text">不包含账号凭据、AI 对话、反馈、本机草稿与队列、客户端偏好或服务器配置。正在运行的计时只保留已结算部分，恢复为暂停。每份文件最多 5 MiB、20000 条记录，每条路线最多 1000 步。</p><button class="btn primary" :disabled="!!busy||requiresConnection()" @click="download('backup')">{{busy==='backup'?'正在生成私有备份…':'下载私有 JSON 备份'}}</button>
    <div class="data-readable"><h3>便于阅读的导出</h3><p>CSV 供表格阅读，不用于关系恢复。所有导出仅含本人已同步数据，包含保留的归档与删除状态。</p><div class="button-row"><button class="btn" :disabled="!!busy||requiresConnection()" @click="download('routes')">导出目标路线 CSV</button><button class="btn" :disabled="!!busy||requiresConnection()" @click="download('todos')">导出今日待办 CSV</button><button class="btn" :disabled="!!busy||requiresConnection()" @click="download('weekly')">导出周复盘 CSV</button></div></div>
  </section><section aria-labelledby="private-restore"><h2 id="private-restore">恢复为新副本</h2><label>选择私有 JSON 备份<input ref="fileInput" type="file" accept=".json,application/json" :disabled="!!busy" @change="choose"/></label><p class="helper-text">文件只在本页内存中读取，尚未发送。离开后需重新选择，原文件不会被修改。</p>
    <template v-if="backup"><p>已选择：<strong class="data-file">{{fileName}}</strong></p><label class="data-check"><input v-model="mayPreview" type="checkbox" :disabled="!!busy"/>我确认这是获准使用的私有备份，同意将文件发送到当前登录服务器进行只读预检。</label><button class="btn" :disabled="!!busy||!mayPreview||requiresConnection()" @click="preflight">{{busy==='preview'?'正在预检…':'只读预检，不恢复数据'}}</button></template>
    <section v-if="preview" class="data-preview" aria-label="备份预检结果"><h3>预检通过 · {{preview.totalRecords}} 条记录</h3><dl class="data-counts"><template v-for="(label,key) in backupRecordLabels" :key="key"><dt>{{label}}</dt><dd>{{preview.counts[key]??0}}</dd></template></dl><ul><li v-for="warning in preview.warnings" :key="warning">{{warning}}</li></ul><p class="helper-text data-file">文件指纹：{{preview.checksum}}</p><label class="data-check"><input v-model="confirmed" type="checkbox" :disabled="!!busy"/>我已核对以上数量和限制，确认恢复为新副本。周复盘保存在下方只读副本中，不覆盖当前周。</label><button class="btn primary" :disabled="!!busy||!confirmed||requiresConnection()" @click="restore">{{busy==='restore'?'正在原子恢复，请稍候…':'确认恢复为新副本'}}</button></section>
  </section></div>
  <section class="data-history"><header class="data-section-head"><h2>恢复批次</h2><button class="btn" :disabled="!!busy||requiresConnection()" @click="loadHistory">读取恢复记录</button></header><p v-if="!history" class="helper-text">联网后可读取，仅显示当前账号的恢复批次。</p><p v-else-if="!history.content.length">还没有恢复记录。</p><article v-for="item in history?.content" :key="item.batchId"><h3>{{new Date(item.createdAt).toLocaleString('zh-CN')}}</h3><p class="helper-text data-file">批次 {{item.batchId}}</p><p>周期计划 {{item.counts.plans??0}}，目标路线 {{item.counts.mainlines??0}}，待办 {{item.counts.todos??0}}，周复盘副本 {{item.counts.weekly_reviews??0}}。</p><div class="button-row"><router-link v-for="id in item.planIds" :key="id" class="btn" :to="`/plans/${id}`">打开恢复的计划</router-link><router-link v-if="item.routeIds.length" class="btn" to="/routes">查看目标路线</router-link><router-link v-if="item.counts.todos" class="btn" to="/todos">查看今日待办</router-link></div></article><footer v-if="history" class="pagination-bar"><span>共 {{history.totalElements}} 批</span><div class="button-row"><button class="btn" :disabled="historyPage===0||!!busy" @click="moveHistory(-1)">前一页批次</button><button class="btn" :disabled="historyPage>=history.totalPages-1||!!busy" @click="moveHistory(1)">后一页批次</button></div></footer></section>
  <section class="data-history"><header class="data-section-head"><h2>恢复的周复盘副本</h2><button class="btn" :disabled="!!busy||requiresConnection()" @click="loadCopies">读取周复盘副本</button></header><p class="helper-text">保留原周次与正文，不会覆盖“周复盘”中正在编写的内容，也不会擅自挪到其他周。可阅读或手动复制需要的部分。</p><p v-if="copies&&!copies.content.length">还没有恢复的周复盘副本。</p><details v-for="item in copies?.content" :key="item.id"><summary>{{item.weekStart}} 这一周 · 只读副本</summary><dl><dt>本周收获</dt><dd>{{item.wins||'未填写'}}</dd><dt>阻塞</dt><dd>{{item.blockers||'未填写'}}</dd><dt>下周重点</dt><dd>{{item.nextFocus||'未填写'}}</dd></dl></details><footer v-if="copies" class="pagination-bar"><span>共 {{copies.totalElements}} 份</span><div class="button-row"><button class="btn" :disabled="copyPage===0||!!busy" @click="moveCopies(-1)">前一页副本</button><button class="btn" :disabled="copyPage>=copies.totalPages-1||!!busy" @click="moveCopies(1)">后一页副本</button></div></footer></section>
</section></template>
<style scoped>
.data-check input[type=checkbox]{width:18px;height:18px;min-height:18px;padding:0;flex:0 0 18px}
.personal-data{margin:28px 0 40px}.personal-data p,.personal-data li{line-height:1.8}.data-notice{border-left:3px solid #002fa7;background:#f7f7f8;padding:16px}.data-layout{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:40px;margin:28px 0}.data-layout>section{min-width:0;border-top:2px solid #002fa7;padding-top:20px}.personal-data label{display:grid;gap:10px}.personal-data input[type=file]{width:100%;max-width:100%}.data-file{overflow-wrap:anywhere}.data-readable{border-top:1px solid #ddd;margin-top:28px;padding-top:20px}.personal-data .button-row{flex-wrap:wrap}.data-check{display:flex!important;align-items:flex-start;gap:10px;line-height:1.8;margin:20px 0}.data-check input{flex:0 0 auto;margin-top:7px}.data-preview{border:1px solid #ddd;background:#f7f7f8;padding:20px;margin-top:24px}.data-counts{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px 16px}.data-counts dd{margin:0;color:#002fa7;font-variant-numeric:tabular-nums}.data-history{border-top:1px solid #ddd;padding-top:24px;margin:32px 0}.data-section-head{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap}.data-history article,.data-history details{border-bottom:1px solid #ddd;padding:20px 0}.data-history summary{cursor:pointer;color:#002fa7;line-height:1.8}.data-history dd{white-space:pre-wrap;overflow-wrap:anywhere;margin:8px 0 20px;line-height:1.8}.personal-data .pagination-bar{flex-wrap:wrap;gap:12px}
@media(max-width:850px){.data-layout{grid-template-columns:minmax(0,1fr);gap:28px}.data-preview{padding:16px}.personal-data .button-row .btn{flex:1}.data-section-head{align-items:flex-start}.data-section-head>.btn{width:100%}.data-history article>.button-row{display:grid;grid-template-columns:minmax(0,1fr)}}
</style>
