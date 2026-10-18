<script setup lang="ts">
import {computed,onUnmounted,reactive,ref,watch} from 'vue';
import {api,apiMessage,currentQueueOwner,serverScope} from '@/api/client';
import {requiresConnection} from '@/connectionState';
import {replaceConflict,type OfflineOperation} from '@/offlineQueue';
import {comparisonRows,parseEditedValue,readableValue,type EditableSnapshot} from '@/syncComparison';
import AppSheet from './AppSheet.vue';
const props=defineProps<{operation:OfflineOperation|null}>();const emit=defineEmits<{close:[];replaced:[]}>();
const cloud=ref<EditableSnapshot|null>(null),error=ref(''),busy=ref(false),confirmed=ref(false);const choices=reactive<Record<string,string>>({}),edited=reactive<Record<string,string>>({});
let serial=0;onUnmounted(()=>{serial++;});const rows=computed(()=>props.operation&&cloud.value?comparisonRows(props.operation.url,props.operation.data,cloud.value,props.operation.base):[]);
watch(()=>props.operation,async operation=>{const n=++serial;cloud.value=null;error.value='';confirmed.value=false;Object.keys(choices).forEach(key=>delete choices[key]);if(!operation)return;
  busy.value=true;try{if(requiresConnection())throw new Error('请先恢复连接，再读取此冲突的最新云端内容。');const result=(await api.get<EditableSnapshot>('/sync/record',{params:{path:operation.url}})).data;if(n!==serial)return;
    // Reading a conflict never submits it, including tombstones and online-only payloads.
    comparisonRows(operation.url,operation.data,result,operation.base);cloud.value=result;
    for(const item of rows.value){choices[item.key]='';edited[item.key]=typeof item.local==='object'?JSON.stringify(item.local):String(item.local??'');}
  }catch(e){if(n===serial)error.value=apiMessage(e);}finally{if(n===serial)busy.value=false;}
},{immediate:true});
const allowed=computed(()=>!!props.operation&&!!cloud.value&&['PUT','PATCH'].includes(props.operation.method)&&!((props.operation.data as Record<string,unknown>|undefined)?.deleted)&&!cloud.value.fields.deleted);
const allChosen=computed(()=>rows.value.length>0&&rows.value.every(item=>['LOCAL','CLOUD','EDIT'].includes(choices[item.key])));
function chooseAll(choice:string){rows.value.forEach(item=>{choices[item.key]=choice;});confirmed.value=false;}
async function submit(){const operation=props.operation,snapshot=cloud.value,owner=currentQueueOwner(),scope=serverScope();if(!operation||!snapshot||!allowed.value||!allChosen.value||!confirmed.value||busy.value)return;
  busy.value=true;error.value='';try{if(!owner||owner!==operation.ownerId||requiresConnection())throw new Error('请先恢复当前账号连接，不会变更其他账号的队列。');
    const data:Record<string,unknown>={version:snapshot.version};for(const item of rows.value){data[item.key]=choices[item.key]==='EDIT'?parseEditedValue(edited[item.key],item.cloud):choices[item.key]==='LOCAL'?item.local:item.cloud;}
    if(owner!==currentQueueOwner()||scope!==serverScope())throw new Error('账号或服务器已变化，原操作仍保留。');
    await replaceConflict(operation,data,snapshot);emit('replaced');emit('close');
  }catch(e){error.value=apiMessage(e);}finally{busy.value=false;}
}
</script>
<template><AppSheet :open="!!operation" title="比较并重新提交" title-id="conflict-comparison-title" :busy="busy" @close="emit('close')"><section class="conflict-comparison">
  <p>仅比较你选择的这一条操作。不会扫描其他计划，也不会自动合并或发送修改。</p><p v-if="error" class="error-text" role="alert">{{error}}</p><p v-if="busy" role="status">正在读取最新内容…</p>
  <template v-if="cloud"><p>{{operation?.base?'下方包含编辑时的共同基础、本机修改及最新云端版本。':'旧操作没有可靠共同基础，只能展示本机与云端两方内容；不推测哪一方先改。'}}</p><p>最新云端版本：{{cloud.version}}。选择后会生成新的本机操作，还需回到同步中心提交；若云端再次变化，仍会返回冲突。</p>
    <div v-if="allowed" class="button-row"><button class="btn" :disabled="busy" @click="chooseAll('CLOUD')">全部保留云端</button><button class="btn" :disabled="busy" @click="chooseAll('LOCAL')">全部采用本机</button></div>
    <article v-for="item in rows" :key="item.key" class="comparison-field"><h3>{{item.label}}</h3><div class="comparison-values"><section v-if="operation?.base"><h4>共同基础</h4><p>{{readableValue(item.base)}}</p></section><section><h4>本机修改</h4><p>{{readableValue(item.local)}}</p></section><section><h4>最新云端</h4><p>{{readableValue(item.cloud)}}</p></section></div>
      <template v-if="allowed"><label>此字段如何处理<select v-model="choices[item.key]" :aria-label="item.label+'处理方式'" :disabled="busy" @change="confirmed=false"><option value="">请主动选择</option><option value="CLOUD">保留云端</option><option value="LOCAL">采用本机</option><option value="EDIT">手动填写</option></select></label><label v-if="choices[item.key]==='EDIT'">手动填写{{item.label}}<textarea v-model="edited[item.key]" :disabled="busy" maxlength="5000" rows="3" @input="confirmed=false"/></label><p v-if="choices[item.key]==='EDIT'&&Array.isArray(item.cloud)" class="helper-text">列表使用 JSON 数组，如 ["练习一项"] 或 [7]。</p></template>
    </article>
    <template v-if="allowed"><label class="check"><input v-model="confirmed" type="checkbox" :disabled="busy||!allChosen"/>我已核对每个字段，同意用最新版本生成新的本机操作；原修改保留在追溯记录中。</label><button class="btn primary" :disabled="busy||!allChosen||!confirmed||requiresConnection()" @click="submit">确认生成新的本机操作</button></template><p v-else>删除、回收站和在线专用操作仅供对照。请先在原页面联网处理，原冲突仍保留，不会自动删除。</p>
  </template></section></AppSheet></template>
<style scoped>
.conflict-comparison{display:grid;gap:20px;min-width:0}.comparison-field{border-top:1px solid #d8d8df;padding-top:20px;display:grid;gap:16px}.comparison-values{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px}.comparison-values section{background:#f7f7f8;padding:12px;min-width:0}.comparison-values h4{color:#002fa7;margin:0 0 10px}.comparison-values p{white-space:pre-wrap;overflow-wrap:anywhere;margin:0}.conflict-comparison label{display:grid;gap:8px}.conflict-comparison .check{display:flex;align-items:start;gap:10px}.check input{width:20px;height:20px;min-height:20px;flex:0 0 20px;accent-color:#002fa7}.conflict-comparison textarea{min-width:0;width:100%}@media(max-width:600px){.comparison-values{grid-template-columns:minmax(0,1fr)}}
</style>
