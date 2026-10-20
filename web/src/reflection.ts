import {mondayOf} from './dailyExperience';
import type {AiSession} from './aiPlanning';
export type ReviewKind='DAY'|'WEEK';
export type Energy='UNSPECIFIED'|'LOW'|'STEADY'|'HIGH';
export type Burden='UNSPECIFIED'|'LIGHT'|'MANAGEABLE'|'HEAVY';
export const energyLabels:Record<Energy,string>={UNSPECIFIED:'不填写',LOW:'精力偏低',STEADY:'精力平稳',HIGH:'精力较充足'};
export const burdenLabels:Record<Burden,string>={UNSPECIFIED:'不填写',LIGHT:'负担较轻',MANAGEABLE:'负担可接受',HEAVY:'负担偏重'};
export interface ModeFacts{arranged:number;arrangedCompleted:number;open:number;skipped:number;actuallyCompleted:number;completedWithoutTime:number}
export interface ReflectionFacts{kind:ReviewKind;start:string;end:string;calculatedAt:string;timezone:string;plan:ModeFacts;todo:ModeFacts;routeCompleted:number;recordedPlanSeconds:number;hasApplicableItems:boolean;allApplicableComplete:boolean}
export interface ReflectionContent{wins:string;blockers:string;nextFocus:string;energy:Energy;burden:Burden;reviewed:boolean;openToIncrease:boolean}
export interface ReflectionNotes extends ReflectionContent{date?:string;weekStart?:string;version:number|null;updatedAt:string|null}
export interface ReflectionItem{kind:'PLAN'|'TODO'|'ROUTE';id:string;parentId:string;title:string;arrangedDate:string|null;status:string;completedAt:string|null}
export interface ReflectionPreferences{completionGuide:boolean;dailyEnabled:boolean;dailyTime:string;weeklyEnabled:boolean;weekDay:number;weeklyTime:string;trendEnabled:boolean;version:number|null}
export interface Trend{eligible:boolean;reason:string;nextEligibleAt:string|null;increaseAllowed:boolean;weeks:ReflectionFacts[];lastDecision:string|null}
export interface ReflectionPreview{kind:ReviewKind;start:string;fingerprint:string;contentJson:string}
export interface AnalysisSummary{id:string;version:number;kind:ReviewKind;start:string;createdAt:string;expiresAt:string}
export interface AnalysisSession extends AiSession{review:Record<string,unknown>|null}
export interface ReflectionDraft{schemaVersion:1;kind:ReviewKind;start:string;savedAt:string;baseVersion:number|null;content:ReflectionContent}
export const emptyReflection=():ReflectionContent=>({wins:'',blockers:'',nextFocus:'',energy:'UNSPECIFIED',burden:'UNSPECIFIED',reviewed:false,openToIncrease:false});
export const reflectionDefaults=():ReflectionPreferences=>({completionGuide:true,dailyEnabled:false,dailyTime:'22:30',weeklyEnabled:false,weekDay:7,weeklyTime:'22:30',trendEnabled:true,version:null});
export function reflectionStart(kind:ReviewKind,date:string){const d=new Date(date+'T12:00:00Z');if(!/^\d{4}-\d{2}-\d{2}$/.test(date)||!Number.isFinite(d.getTime())||d.toISOString().slice(0,10)!==date)throw Error('请选择有效日期。');return kind==='WEEK'?mondayOf(date):date;}
export function reflectionDraft(value:unknown,kind:ReviewKind,start:string):ReflectionDraft|null{
 if(value==null)return null;const d=value as ReflectionDraft;
 if(d.schemaVersion!==1||d.kind!==kind||d.start!==start||!Number.isFinite(Date.parse(d.savedAt))||!(d.baseVersion===null||Number.isSafeInteger(d.baseVersion)&&d.baseVersion>=0)||!d.content||!['wins','blockers','nextFocus'].every(k=>typeof(d.content as unknown as Record<string,unknown>)[k]==='string'&&String((d.content as unknown as Record<string,unknown>)[k]).length<=4000)||!(d.content.energy in energyLabels)||!(d.content.burden in burdenLabels)||typeof d.content.reviewed!=='boolean'||typeof d.content.openToIncrease!=='boolean')throw Error('本机复盘草稿格式无效；未覆盖原内容。');return d;
}
export function reflectionItemPath(i:ReflectionItem){return i.kind==='PLAN'?{path:'/schedule',query:{plan:i.parentId,date:i.arrangedDate??undefined,task:i.id}}:i.kind==='ROUTE'?{path:'/mainlines',query:{line:i.parentId}}:{path:'/todos',query:{date:i.arrangedDate??undefined,focus:i.id}};}
