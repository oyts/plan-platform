<script setup lang="ts">
import {onMounted,ref} from 'vue';
import {api,apiMessage} from '@/api/client';
import {requiresConnection} from '@/connectionState';
interface Batch {importId:string;sourceVersion:string;status:string}
interface Entry {type:string;legacyId:string;legacyParentId:string;deletedAt:string;action:string;targetParentId:string|null;parentVersion:number|null}
interface Preview {importId:string;fingerprint:string;alreadyIncluded:boolean;writeSupported:boolean;retainedPermanentMarkers:number;entries:Entry[];notice:string}
const batches=ref<Batch[]>([]),selected=ref(''),preview=ref<Preview|null>(null),confirmed=ref(false),busy=ref(false),error=ref(''),notice=ref('');
let operationKey='';
const names:Record<string,string>={plan:'周期计划',mainline:'目标路线','mainline-item':'路线步骤',tag:'计划标签'};
async function load(){try{batches.value=(await api.get<Batch[]>('/migrations/legacy')).data.filter(b=>b.status==='COMPLETED');}catch(reason){error.value=apiMessage(reason);}}
async function inspect(){
  if(busy.value||requiresConnection()||!selected.value)return;
  busy.value=true;error.value='';notice.value='';preview.value=null;confirmed.value=false;operationKey='';
  try{preview.value=(await api.get<Preview>(`/migrations/legacy/${selected.value}/recycling-preview`)).data;operationKey=crypto.randomUUID();}
  catch(reason){error.value=apiMessage(reason);}finally{busy.value=false;}
}
async function apply(){
  const current=preview.value;
  if(busy.value||requiresConnection()||!confirmed.value||!current?.writeSupported||current.alreadyIncluded||!current.entries.length)return;
  busy.value=true;error.value='';
  try{
    await api.post(`/migrations/legacy/${current.importId}/recycling-supplement`,{operationKey,fingerprint:current.fingerprint,confirmed:true});
    notice.value='回收站补迁完成，原有任务与历史未被覆盖。请到回收站查看；原删除时间保持不变。';
    preview.value=null;confirmed.value=false;await load();
  }catch(reason){error.value=apiMessage(reason);/* Keep the exact request key for a safe retry after an unknown network result. */}
  finally{busy.value=false;}
}
onMounted(()=>void load());
</script>
<template>
  <section class="section recycling-panel" aria-labelledby="recycling-title">
    <header><h2 id="recycling-title">补迁旧版回收站</h2><p>仅用于已经完成旧版迁移、但当时未转换回收站的批次。不需要回滚或重新导入。</p></header>
    <p v-if="requiresConnection()" class="helper-text">预检与补迁需要联网确认，不会进入离线队列。</p>
    <p v-if="error" role="alert" class="error-text">{{error}}</p><p v-if="notice" role="status" class="success-text">{{notice}}</p>
    <p v-if="!batches.length&&!error">暂无已完成的旧版迁移批次。</p>
    <div v-else class="button-row"><label>已完成批次<select v-model="selected" :disabled="busy" @change="preview=null;confirmed=false"><option value="">请选择批次</option><option v-for="batch in batches" :key="batch.importId" :value="batch.importId">{{batch.sourceVersion}} · {{batch.importId}}</option></select></label><button type="button" class="btn" :disabled="busy||!selected||requiresConnection()" @click="inspect">{{busy?'处理中…':'只读预检'}}</button></div>
    <div v-if="preview" class="recycling-preview">
      <p v-if="preview.alreadyIncluded" role="status">此批次已经包含回收站转换，无需重复补迁。</p>
      <template v-else>
        <h3>待补快照 {{preview.entries.length}} 项</h3><p>{{preview.notice}}</p>
        <p>另保留 {{preview.retainedPermanentMarkers}} 项无正文的永久删除标记，不会据此删除你当前的数据。</p>
        <ul class="recycling-entries"><li v-for="entry in preview.entries" :key="`${entry.type}:${entry.legacyParentId}:${entry.legacyId}`"><strong>{{names[entry.type]||entry.type}}</strong><span>{{entry.legacyId}}</span><small>原删除时间 {{new Date(entry.deletedAt).toLocaleString()}} · {{entry.action==='IN_PARENT_SNAPSHOT'?'随父快照补入':entry.action==='ATTACH_DELETED_CHILD'?'补入原父对象的回收站':'新增已删除的父快照'}}</small></li></ul>
        <p v-if="!preview.entries.length">没有可补迁的回收站正文。</p>
        <template v-else-if="preview.writeSupported"><label class="recycling-confirm"><input v-model="confirmed" type="checkbox" :disabled="busy"/>我已核对待补内容；理解不会覆盖当前记录，回滚原批次将同时删除原迁移及补迁内容。</label><button type="button" class="btn primary" :disabled="busy||!confirmed||requiresConnection()" @click="apply">确认补迁回收站</button></template>
      </template>
    </div>
  </section>
</template>
<style scoped>
.recycling-panel{padding:24px;border:1px solid #d8d8df;background:#fff}.recycling-panel h2{margin-top:0}.recycling-panel .button-row{align-items:end;flex-wrap:wrap}.recycling-panel label{min-width:0}.recycling-panel select{max-width:100%}.recycling-preview{border-top:1px solid #d8d8df;margin-top:24px;padding-top:16px}.recycling-entries{list-style:none;padding:0}.recycling-entries li{display:grid;grid-template-columns:110px minmax(0,1fr);gap:4px 16px;padding:12px 0;border-bottom:1px solid #d8d8df}.recycling-entries small{grid-column:2}.recycling-entries span,.recycling-entries small{overflow-wrap:anywhere}.recycling-confirm{display:flex;gap:12px;align-items:start;margin:20px 0}.recycling-confirm input{width:18px;height:18px;min-height:18px;padding:0;margin:3px 0;flex-shrink:0}
@media(max-width:700px){.recycling-panel{padding:16px}.recycling-panel .button-row{display:grid}.recycling-entries li{grid-template-columns:1fr}.recycling-entries small{grid-column:auto}.recycling-panel .btn{width:100%}}
</style>
