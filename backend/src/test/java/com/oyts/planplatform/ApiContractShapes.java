package com.oyts.planplatform;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;

/** Whitelisted dynamic wire shapes reviewed against the corresponding controller/service.
 * This catalog documents JSON, not SQL entities. It never loads application/user records. */
final class ApiContractShapes {
    private final ObjectMapper json;
    private final Function<JavaType,ObjectNode> typed;
    ApiContractShapes(ObjectMapper json,Function<JavaType,ObjectNode> typed){this.json=json;this.typed=typed;}
    ObjectNode str(){return json.createObjectNode().put("type","string");}
    ObjectNode integer(){return json.createObjectNode().put("type","integer");}
    ObjectNode bool(){return json.createObjectNode().put("type","boolean");}
    ObjectNode text(String...values){ObjectNode n=str();n.set("enum",json.valueToTree(values));return n;}
    ObjectNode date(){return str().put("format","date");}
    ObjectNode instant(){return str().put("format","date-time");}
    ObjectNode uuid(){return str().put("format","uuid");}
    ObjectNode nullable(ObjectNode n){n.put("nullable",true);return n;}
    ObjectNode array(ObjectNode items){ObjectNode n=json.createObjectNode().put("type","array");n.set("items",items);return n;}
    ObjectNode object(Object...pairs){
        ObjectNode n=json.createObjectNode().put("type","object").put("additionalProperties",false),p=n.putObject("properties");
        ArrayNode required=n.putArray("required");for(int i=0;i<pairs.length;i+=2){p.set((String)pairs[i],(JsonNode)pairs[i+1]);required.add((String)pairs[i]);}return n;
    }
    ObjectNode type(String name){try{return typed.apply(json.constructType(Class.forName("com.oyts.planplatform."+name)));}catch(ClassNotFoundException e){throw new IllegalStateException(e);}}
    ObjectNode page(ObjectNode items){return object("content",array(items),"page",integer(),"size",integer(),"totalElements",integer(),"totalPages",integer());}
    ObjectNode response(String key){return switch(key){
        case "GET /health" -> object("ok",bool(),"service",text("plan-platform-api"),"version",str(),"apiVersion",integer(),"capabilities",object("structureTemplates",bool()));
        case "GET /admin/governance/config" -> object("enabled",bool(),"mode",text("NEXT_ONLINE_REQUEST"),"revokesRefreshSessions",bool(),"oldTokensReviveOnRestore",bool(),"offlineIdentityHours",integer());
        case "GET /ai/capabilities" -> object("schemaVersion",integer(),"available",bool(),"provider",text("deepseek"),"model",str(),"status",text("READY","NOT_CONFIGURED","DISABLED"),"message",str(),"transport",text("ASYNC_POLLING"),"timezone",text("Asia/Shanghai"),"maxInputChars",integer().put("maximum",8000),"adjustmentEnabled",bool());
        case "POST /ai/jobs" -> object("code",text("AI_NOT_AVAILABLE"),"message",str());
        case "GET /client-updates/android/latest" -> object("version",str(),"versionCode",integer(),"minimumSupportedVersionCode",integer(),"sha256",str().put("pattern","[a-f0-9]{64}"),"size",integer(),"notes",str(),"packageName",str(),"url",str(),"accountId",uuid());
        case "GET /client-updates/android/releases/{versionCode}/apk" -> str().put("format","binary");
        case "GET /mainlines/{id}/steps/page" -> page(type("mainline.MainlineDtos$ItemResponse"));
        case "GET /admin/operations/alerts", "POST /admin/operations/evaluate" -> array(object("code",str(),"incident_id",str(),"active",bool(),"first_seen",instant(),"last_seen",instant(),"resolved_at",nullable(instant())));
        case "GET /admin/operations/policy-audit" -> page(object("id",uuid(),"actor_id",uuid(),"previous_version",integer(),"new_version",integer(),"before_json",str(),"after_json",str(),"created_at",instant()));
        case "GET /admin/governance/audit" -> page(object("id",uuid(),"actor_id",uuid(),"target_id",uuid(),"action",str(),"reason",str(),"created_at",instant()));
        case "GET /reflection/analyses" -> array(object("id",uuid(),"version",integer(),"createdAt",instant(),"expiresAt",instant(),"kind",text("DAY","WEEK"),"start",date()));
        case "GET /reflection/targets" -> page(object("id",uuid(),"name",str()));
        case "GET /data/backup" -> backup();
        default -> null;
    };}
    ObjectNode backup(){
        ObjectNode root=object("format",text("plan-cabin-private-backup"),"schemaVersion",json.createObjectNode().put("type","integer").set("enum",json.valueToTree(List.of(4))),"sourceVersion",str(),"exportedAt",instant(),"records",backupRecords());
        root.put("x-max-total-rows",20000);root.put("x-max-utf8-bytes",5242880);root.put("description","格式4。外键、依赖环、状态、日期范围、计时关系及总字节数由服务端只读预检联合校验；恢复为新记录，不覆盖现有数据。");return root;
    }
    ObjectNode backupInput(){
        ObjectNode root=json.createObjectNode();ArrayNode versions=root.putArray("oneOf");
        for(int version=1;version<=4;version++){
            ObjectNode variant=backup(),props=(ObjectNode)variant.get("properties");
            props.set("schemaVersion",integer().put("minimum",version).put("maximum",version));
            ObjectNode records=(ObjectNode)props.get("records");
            if(version<4)removeProperty(records,"personal_templates");
            if(version<3){removeProperty(records,"daily_reviews");ObjectNode weekly=(ObjectNode)records.path("properties").path("weekly_reviews").path("items");for(String field:List.of("energy","burden","reviewed","open_to_increase"))removeProperty(weekly,field);}
            if(version==1)removeProperty((ObjectNode)records.path("properties").path("task_progress").path("items"),"ever_started");
            variant.put("description","私有备份输入格式"+version+"，缺省字段只按服务端历史版本规则补足；外键、状态和完整性由预检验证。");versions.add(variant);
        }return root;
    }
    private void removeProperty(ObjectNode schema,String name){((ObjectNode)schema.get("properties")).remove(name);ArrayNode required=(ArrayNode)schema.get("required");for(int i=required.size()-1;i>=0;i--)if(required.get(i).asText().equals(name))required.remove(i);}
    ObjectNode request(String key){return switch(key){case "POST /data/preview"->backupInput();case "POST /data/restore"->object("backup",backupInput(),"checksum",str().put("pattern","[a-f0-9]{64}"),"operationKey",uuid(),"confirmed",bool());default->new ApiLegacyContract(json).request(key);};}
    private ObjectNode backupRecords(){
        try{
            Field catalog=Class.forName("com.oyts.planplatform.data.BackupSchema").getDeclaredField("TABLES");catalog.setAccessible(true);
            ObjectNode records=object(),props=(ObjectNode)records.get("properties");ArrayNode required=(ArrayNode)records.get("required");
            for(Object table:(List<?>)catalog.get(null)){
                String name=(String)value(table,"name");ObjectNode row=object(),fields=(ObjectNode)row.get("properties");ArrayNode keys=(ArrayNode)row.get("required");
                for(Object field:(List<?>)value(table,"fields")){
                    String key=(String)value(field,"name"),kind=value(field,"kind").toString();
                    ObjectNode shape=switch(kind){
                        case "ID","REFERENCE","BATCH"->uuid();case "BOOLEAN"->bool();case "INTEGER"->integer().put("minimum",(long)value(field,"min")).put("maximum",(long)value(field,"upper"));
                        case "DATE"->date();case "TIME"->str().put("format","time");case "INSTANT"->instant();
                        case "STRINGS"->array(str());case "BOOLEANS"->array(bool());case "IDS"->array(uuid());default->str();
                    };
                    if((int)value(field,"max")>0)shape.put("maxLength",(int)value(field,"max"));if((boolean)value(field,"nonblank"))shape.put("x-not-blank",true);
                    if((boolean)value(field,"nullable"))shape.put("nullable",true);if(value(field,"reference")!=null)shape.put("x-reference-table",value(field,"reference").toString());
                    fields.set(key,shape);keys.add(key);
                }
                props.set(name,array(row).put("maxItems",20000));required.add(name);
            }return records;
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private Object value(Object record,String name)throws ReflectiveOperationException{Method m=record.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(record);}
    ObjectNode field(Class<?> owner,String field){
        String name=owner.getSimpleName();
        if(owner.getName().contains("feedback.FeedbackDtos")&&field.equals("diagnostic"))return diagnostic();
        if(owner.getName().contains("sync.SyncRecordController")&&field.equals("fields"))return editableFields();
        if(owner.getName().contains("organize.PersonalTemplateService")&&field.equals("blueprint"))return nullable(blueprint());
        if(owner.getName().contains("ai.AiWorkbenchService")&&field.equals("content"))return workspace();
        if(owner.getName().contains("ai.AiConversationService")&&name.equals("Session")){
            if(field.equals("applied"))return type("ai.AiDraftApplier$Applied");
            if(field.equals("review"))return nullable(review());
        }
        return null;
    }
    ObjectNode workspace(){
        ObjectNode draft=json.createObjectNode().put("type","object").put("additionalProperties",true).put("nullable",true);
        draft.put("description","有意允许不完整的编辑草稿；保存不代表通过AiDrafts校验，显式恢复对话时才严格校验。");
        return object("schemaVersion",json.createObjectNode().put("type","integer").set("enum",json.valueToTree(List.of(1))),"mode",nullable(text("PLAN","ROUTE","TODO")),"message",str().put("maxLength",8000),"importJson",str().put("maxLength",128000),"draft",draft,"sourceSessionId",nullable(str().put("pattern","[a-f0-9-]{36}")),"sourceVersion",nullable(integer().put("minimum",0)));
    }
    ObjectNode diagnostic(){
        ObjectNode n=nullable(object("schemaVersion",integer().put("minimum",1).put("maximum",1),"clientVersion",str().put("maxLength",64),"platform",text("WEB","WINDOWS","ANDROID"),"occurredAt",instant(),"errorKind",text("NONE","NETWORK_OR_TLS","TLS","AUTH","PERMISSION","VERSION","BUSY","VALIDATION","CONFLICT","SERVER","UNKNOWN"),"queueCount",integer().put("minimum",0).put("maximum",100000),"conflictCount",integer().put("minimum",0).put("maximum",100000)));
        ObjectNode p=(ObjectNode)n.get("properties");p.set("serverVersion",nullable(str().put("maxLength",64)));p.set("apiVersion",nullable(integer().put("minimum",1).put("maximum",100)));p.set("requestId",nullable(uuid()));n.put("x-max-json-chars",2000);n.put("description","可选诊断白名单；conflictCount不能大于queueCount，版本字符串还须通过FeedbackService.VERSION校验。");return n;
    }
    ObjectNode editableFields(){
        ObjectNode n=json.createObjectNode();ArrayNode variants=n.putArray("oneOf");
        variants.add(object("name",str(),"description",str(),"startDate",date(),"endDate",date(),"restDays",array(integer().put("minimum",1).put("maximum",7))));
        variants.add(object("name",str(),"color",str()));
        variants.add(object("tagId",nullable(uuid()),"date",date(),"startTime",nullable(str().put("format","time")),"endTime",nullable(str().put("format","time")),"plannedMinutes",integer(),"category",str(),"title",str(),"description",str(),"acceptance",array(str()),"resourceUrl",str(),"required",bool(),"sortOrder",integer()));
        variants.add(object("name",str(),"date",date(),"description",str(),"acceptance",array(str())));
        variants.add(object("name",str(),"category",str(),"title",str(),"description",str(),"plannedMinutes",integer(),"acceptance",array(str()),"resourceUrl",str(),"required",bool()));
        variants.add(object("name",str(),"description",str(),"color",str()));
        variants.add(object("title",str(),"description",str(),"acceptance",array(str()),"plannedMinutes",integer(),"sortOrder",integer()));
        variants.add(object("title",str(),"note",str(),"dueDate",date(),"done",bool(),"deleted",bool()));
        n.put("description","按请求path选择对应可编辑字段集；不返回所有权、账号、令牌或计时操作。不是任意字段查询接口。");return n;
    }
    ObjectNode blueprint(){
        try{
            Field catalog=Class.forName("com.oyts.planplatform.organize.TemplateBlueprint").getDeclaredField("FIELDS");catalog.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String,List<String>> fields=(Map<String,List<String>>)catalog.get(null);
            ObjectNode result=json.createObjectNode();ArrayNode alternatives=result.putArray("oneOf");
            for(String kind:List.of("PLAN","ROUTE")){
                ObjectNode rows=object();ObjectNode props=(ObjectNode)rows.get("properties");ArrayNode required=(ArrayNode)rows.get("required");
                for(String table:kind.equals("PLAN")?List.of("plan_tags","plan_tasks","plan_milestones"):List.of("mainline_stages","mainline_items")){
                    props.set(table,array(blueprintRow(table,fields.get(table))).put("maxItems",1000));required.add(table);
                }
                alternatives.add(object("schemaVersion",integer().put("minimum",1).put("maximum",1),"kind",text(kind),"parent",blueprintRow(kind,fields.get(kind)),"rows",rows));
            }result.put("x-max-utf8-bytes",262144);result.put("description","结构模板v1，严格字段白名单取自TemplateBlueprint.FIELDS；关系、顺序、依赖环和日期范围由服务端联合验证。不包含执行历史。");return result;
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private ObjectNode blueprintRow(String table,List<String> fields){
        ObjectNode row=object(),props=(ObjectNode)row.get("properties");ArrayNode required=(ArrayNode)row.get("required");
        for(String f:fields){
            ObjectNode shape;
            if(f.equals("id")||f.endsWith("_id"))shape=uuid();
            else if(Set.of("overall_tag","required_task").contains(f))shape=bool();
            else if(Set.of("planned_minutes","sort_order","rest_days_mask").contains(f))shape=integer().put("minimum",f.equals("planned_minutes")?1:f.equals("sort_order")&&!table.equals("mainline_items")?Integer.MIN_VALUE:0).put("maximum",f.equals("rest_days_mask")?127:f.equals("planned_minutes")&&table.equals("plan_tasks")?1440:Integer.MAX_VALUE);
            else{
                int max=switch(f){case "name"->table.equals("plan_tags")?60:120;case "title"->180;case "description"->Set.of("plan_tasks","mainline_items").contains(table)?2000:1000;case "category"->40;case "resource_url"->1000;case "acceptance_json","prerequisite_ids_json"->256000;default->40;};
                shape=str().put("maxLength",max);if(f.endsWith("_date"))shape.put("format","date");if(f.endsWith("_time"))shape.put("format","time");
                if(f.equals("color"))shape.put("pattern","#[a-fA-F0-9]{6}");if(f.equals("execution_mode"))shape=text("SEQUENTIAL","FLEXIBLE");if(f.equals("dependency_mode"))shape=text("NONE","INHERIT","EXPLICIT");
                if(f.endsWith("_json"))shape.put("description","编码为字符串的JSON数组，不是直接JSON数组；最多512个文本项，每项最多500字符，严格重复字段与尾随内容检查。");
            }
            if(Set.of("tag_id","stage_id","recurrence_id","start_time","end_time").contains(f))shape.put("nullable",true);props.set(f,shape);required.add(f);
        }return row;
    }
    ObjectNode review(){
        ObjectNode facts=object("kind",text("DAY","WEEK"),"start",date(),"end",date(),"timezone",str(),"plan",type("reflection.ReflectionDtos$Mode"),"todo",type("reflection.ReflectionDtos$Mode"),"routeCompleted",integer(),"recordedPlanSeconds",integer(),"hasApplicableItems",bool(),"allApplicableComplete",bool());
        ObjectNode review=object("intent",text("REVIEW_ANALYSIS"),"kind",text("DAY","WEEK"),"facts",facts,"notesIncluded",bool()),p=(ObjectNode)review.get("properties");
        ObjectNode notes=json.createObjectNode();notes.putArray("anyOf").add(type("reflection.ReflectionDtos$Notes")).add(type("daily.DailyDtos$Review"));p.set("notes",notes);p.set("increaseAllowed",bool());p.set("automaticTrendEligible",bool());p.set("trendReason",str());
        ObjectNode selected=json.createObjectNode();ArrayNode modes=selected.putArray("oneOf");
        modes.add(object("mode",text("TODO"),"planning",object("title",str(),"note",str(),"due_date",date())));
        modes.add(object("mode",text("PLAN"),"planning",object("name",str(),"description",str(),"start_date",date(),"end_date",date(),"rest_days_mask",integer()),"items",array(object("title",str(),"description",str(),"task_date",date(),"planned_minutes",integer())).put("maxItems",100)));
        modes.add(object("mode",text("ROUTE"),"planning",object("name",str(),"description",str(),"execution_mode",str()),"items",array(object("title",str(),"description",str(),"planned_minutes",integer())).put("maxItems",100)));
        p.set("selectedPlanning",selected);review.put("description","只包含本人明确同意发送的复盘汇总、可选文字及可选单一规划。WEEK才含趋势字段，includeNotes时才含notes；编码上下文不超过12000字符。calculatedAt不会发给AI。");return review;
    }
    static Integer status(String key){return switch(key){case "DELETE /ai/sessions/{id}","DELETE /structure-templates/{id}"->204;case "POST /ai/sessions/{id}/jobs"->202;case "POST /ai/jobs"->503;default->null;};}
    static String media(String key){return switch(key){case "GET /client-updates/android/releases/{versionCode}/apk"->"application/vnd.android.package-archive";case "GET /data/export/{kind}"->"text/csv;charset=UTF-8";default->"application/json";};}
}
