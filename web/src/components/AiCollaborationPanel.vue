<script setup lang="ts">
import {computed,ref,watch} from 'vue';
import {api,apiMessage} from '@/api/client';
import type {AiDraft,AiSession} from '@/aiPlanning';
import {aiDifferences} from '@/aiPlanning';
import {collaborationInput,collaborationText,referenceMaterial,type CollaborationMaterial,type CollaborationPreview} from '@/aiCollaboration';
import {downloadFile} from '@/download';
const props=defineProps<{session:AiSession|null;draft:AiDraft|null;disabled:boolean;detached:boolean;sourceValue:string;instructionsValue:string}>();
const emit=defineEmits<{accepted:[AiSession];source:[string];instructions:[string]}>();
const instructions=ref(''),consent=ref(false),material=ref<CollaborationMaterial|null>(null),source=ref(''),preview=ref<CollaborationPreview|null>(null),confirmed=ref(false),busy=ref(false),error=ref(''),status=ref('');
const differences=computed(()=>{const p=preview.value;if(!p?.original)return [];return [...aiDifferences(p.original,p.proposed),...p.original.items.filter(i=>!p.proposed.items.some(next=>next.localId===i.localId)).map(i=>({id:`removed:${i.localId}`,title:i.title,kind:'REMOVE',fields:[{label:'事项',before:`${i.title} · ${i.note}`,after:'从返回草稿移除（不直接删除真实业务）'}]})),...p.original.stages.filter(s=>!p.proposed.stages.some(next=>next.localId===s.localId)).map(s=>({id:`removed-stage:${s.localId}`,title:s.title,kind:'REMOVE',fields:[{label:'阶段',before:s.title,after:'从返回草稿移除'}]}))];});
watch(source,value=>{preview.value=null;confirmed.value=false;emit('source',value);});
watch(()=>props.sourceValue,value=>{if(source.value!==value)source.value=value;},{immediate:true});
watch(()=>[props.session?.id,props.session?.version,props.draft],()=>{material.value=null;preview.value=null;confirmed.value=false;consent.value=false;},{deep:true});
watch(instructions,value=>{material.value=null;consent.value=false;emit('instructions',value);});
watch(()=>props.instructionsValue,value=>{if(instructions.value!==value)instructions.value=value;},{immediate:true});
async function prepare(){busy.value=true;error.value='';try{if(!props.draft||!consent.value||!instructions.value.trim())throw new Error('请填写修改要求，并确认所选内容的外发范围。');material.value=props.session&&!props.detached?(await api.post<CollaborationMaterial>(`/ai/sessions/${props.session.id}/collaboration/export`,{version:props.session.version,instructions:instructions.value,consent:true})).data:referenceMaterial(props.draft,instructions.value);status.value='已准备所选协作材料；尚未发给任何外部服务。';}catch(e){error.value=apiMessage(e);}finally{busy.value=false;}}
async function copy(){if(!material.value)return;try{await navigator.clipboard.writeText(collaborationText(material.value));status.value='已复制提示词和协作JSON。请只粘贴到你选择的外部AI。';}catch{error.value='复制不可用，请手动复制下方材料。';}}
async function save(){if(!material.value)return;try{const okay=await downloadFile(new Blob([JSON.stringify(material.value,null,2)],{type:'application/json'}),'plan-cabin-ai-collaboration.json');status.value=okay?'已交给下载或系统分享；请自行确认保存／发送结果。':'已取消保存，原材料保留。';}catch(e){error.value=apiMessage(e);}}
async function file(event:Event){const input=event.target as HTMLInputElement;try{const value=input.files?.[0];if(!value)return;if(value.size>480000)throw new Error('协作文件过大，最多480000字节。原文件未改动。');const text=await value.text();source.value=text;collaborationInput(text);status.value='已读取材料，还未预检或保存。';}catch(e){error.value=apiMessage(e);}finally{input.value='';}}
async function inspect(){error.value='';busy.value=true;try{if(!props.session)throw new Error('请先创建或载入一个对应模式对话；独立材料只能作为新规划参考。');collaborationInput(source.value);preview.value=(await api.post<CollaborationPreview>(`/ai/sessions/${props.session.id}/collaboration/preview`,{version:props.session.version,source:source.value})).data;confirmed.value=false;}catch(e){error.value=apiMessage(e);}finally{busy.value=false;}}
async function accept(){if(!props.session||!preview.value||!confirmed.value)return;busy.value=true;error.value='';try{const value=(await api.post<AiSession>(`/ai/sessions/${props.session.id}/collaboration/accept`,{version:props.session.version,source:source.value,fingerprint:preview.value.fingerprint,confirmed:true})).data;emit('accepted',value);preview.value=null;confirmed.value=false;status.value='已保存到对话草稿，未调用AI，也未应用到真实计划。请再次核对后确认生成／应用。';}catch(e){error.value=apiMessage(e);}finally{busy.value=false;}}
</script>
<template>
<details class="collaboration" aria-label="外部AI协作"><summary>交给外部 AI 修改／导回</summary>
  <p>只提供当前所选草稿和你填写的修改要求，不提供账号、会话历史、业务ID、服务器地址或同步队列。文件含私人规划，请自行决定接收方。独立保存副本没有原对象修改权限。</p>
  <label>外部修改要求<textarea v-model="instructions" rows="3" maxlength="2000" :disabled="busy"/></label>
  <details v-if="draft"><summary>提交前预览：{{draft.title}} · {{draft.items.length}}项 · {{draft.stages.length}}阶段</summary><pre>{{JSON.stringify(draft,null,2)}}</pre></details>
  <p v-if="session?.adjustment">受保护事项：{{session.adjustment.protectedItems.map(id=>draft?.items.find(i=>i.localId===id)?.title??id).join('、')||'无'}}。必须保留原事项、阶段和顺序，不能改写执行历史。</p>
  <label class="consent"><input v-model="consent" type="checkbox"/>我已核对所选草稿和要求，同意自己将这些内容提供给选定的外部AI。</label>
  <button class="btn" :disabled="disabled||busy||!draft||!consent||!instructions.trim()" @click="prepare">准备协作材料（不调用 AI）</button>
  <template v-if="material"><label>提示词与协作JSON<textarea :value="collaborationText(material)" readonly rows="6"/></label><div class="button-row"><button class="btn" @click="copy">复制协作材料</button><button class="btn" @click="save">保存／分享协作 JSON</button></div></template>
  <hr/><p>外部AI返回后，粘贴完整协作JSON或选择文件。先只读预检、比较差异，再确认保存草稿；不会自动调用AI或修改真实计划。</p>
  <label>外部返回协作JSON<textarea v-model="source" rows="5" maxlength="160000" :disabled="busy"/></label>
  <label class="file">选择协作JSON文件<input type="file" accept="application/json,.json" :disabled="busy" @change="file"/></label>
  <button class="btn" :disabled="disabled||busy||!session||!source.trim()" @click="inspect">只读预检协作材料</button>
  <section v-if="preview" aria-label="协作差异预览"><h3>{{preview.binding==='BOUND'?'来源已绑定':'仅作新规划参考'}}</h3><p role="status">{{preview.message}}</p><p v-if="!preview.canSave">请保留材料，在新建对应模式对话中导入，不会自动转换或更改原对象。</p><p v-if="!preview.original">将保存 {{preview.proposed.items.length}} 项新草稿，尚未创建真实业务。</p><ol v-else><li v-for="change in differences" :key="change.id"><strong>{{change.kind==='ADD'?'新增':change.kind==='REMOVE'?'移除':'修改'}} · {{change.title}}</strong><p v-for="field in change.fields" :key="field.label">{{field.label}}：{{field.before}} → {{field.after}}</p></li></ol><p v-if="preview.original&&!differences.length">内容与当前草稿相同，无新增或修改。</p><p>未列出的事项原样保留，执行历史不会从文件导入。</p><label class="consent"><input v-model="confirmed" type="checkbox" :disabled="!preview.canSave"/>我已核对差异，只保存对话草稿，真实业务仍须另行确认。</label><button class="btn primary" :disabled="disabled||busy||!confirmed||!preview.canSave" @click="accept">确认保存返回草稿</button></section>
  <p v-if="status" role="status">{{status}}</p><p v-if="error" role="alert" class="error-text">{{error}}</p>
</details>
</template>
<style scoped>
.collaboration{border-top:1px solid var(--line);padding-top:12px;min-width:0}.collaboration summary{cursor:pointer;padding:12px 0;min-height:44px;line-height:1.7;font-weight:700}.collaboration p,.collaboration li{font-size:.85rem;line-height:1.8;overflow-wrap:anywhere}.collaboration label:not(.consent){display:grid;gap:8px;margin:14px 0;font-size:.8rem;font-weight:700}.collaboration textarea{width:100%;max-width:100%;box-sizing:border-box;resize:vertical}.collaboration pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:.75rem}.consent{display:flex;gap:10px;align-items:flex-start;margin:16px 0;font-size:.8rem;line-height:1.8}.consent input{flex:0 0 20px;width:20px;height:20px;margin-top:4px}.collaboration .button-row{flex-wrap:wrap;gap:8px}.collaboration input[type=file]{max-width:100%}.collaboration h3{font-size:1rem}.collaboration hr{border:0;border-top:1px solid var(--line);margin:24px 0}
</style>
