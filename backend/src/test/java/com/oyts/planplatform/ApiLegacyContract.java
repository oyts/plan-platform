package com.oyts.planplatform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

/** Canonical legacy wire guide, separate from intentionally permissive raw snapshot intake. */
final class ApiLegacyContract {
    private final ObjectMapper json;
    private final ApiContractShapes s;
    ApiLegacyContract(ObjectMapper json){this.json=json;s=new ApiContractShapes(json,t->{throw new IllegalStateException("No DTO needed");});}
    private ObjectNode open(Object...fields){ObjectNode n=s.object(fields);n.put("additionalProperties",true);n.remove("required");return n;}
    private ObjectNode map(ObjectNode value){ObjectNode n=open();n.set("additionalProperties",value);return n;}
    private ObjectNode stamp(){return s.str().put("description","旧时间支持ISO Instant、带偏移量时间和按Asia/Shanghai解释的本地日期时间；不是仅UTC格式。");}
    ObjectNode canonical(){
        ObjectNode tag=open("id",s.str(),"name",s.str(),"color",s.str(),"isOverall",s.bool());
        ObjectNode task=open("id",s.str(),"date",s.date(),"startTime",s.str(),"endTime",s.str(),"plannedMinutes",s.integer(),"category",s.str(),"title",s.str(),"description",s.str(),"acceptance",s.array(s.str()),"required",s.bool(),"tagId",s.str(),"sourceRef",s.str(),"resources",s.array(open("label",s.str(),"url",s.str())));
        ObjectNode plan=open("id",s.str(),"name",s.str(),"startDate",s.date(),"endDate",s.date(),"overallTagId",s.str(),"tags",s.array(tag),"tasks",s.array(task));
        ObjectNode line=open("id",s.str(),"name",s.str(),"description",s.str(),"color",s.str(),"builtIn",s.bool(),"items",s.array(task));
        ObjectNode progress=open("taskId",s.str(),"status",s.text("pending","running","paused","completed","partial","skipped","deferred"),"accumulatedSeconds",s.integer(),"startedAt",s.nullable(stamp()),"completedAt",s.nullable(stamp()),"timeEntries",s.array(open("id",s.str(),"taskId",s.str(),"startedAt",stamp(),"endedAt",stamp(),"seconds",s.integer(),"source",s.text("timer","manual","legacy"))),"acceptanceChecks",s.array(s.bool()),"note",s.str(),"deferTo",s.nullable(s.str()),"skipReason",s.nullable(s.str()),"updatedAt",stamp());
        ObjectNode reflection=open("date",s.date(),"completedSummary",s.str(),"unfinishedSummary",s.str(),"blockers",s.str(),"firstTaskTomorrow",s.str(),"energy",s.nullable(s.integer()),"note",s.str(),"updatedAt",stamp());
        ObjectNode trash=json.createObjectNode();ArrayNode types=trash.putArray("oneOf");
        for(String kind:new String[]{"plan","mainline","mainline-item","tag"}){
            ObjectNode payload=switch(kind){
                case "plan"->open("plan",plan,"progress",map(progress),"reflections",map(reflection));
                case "mainline"->open("track",line,"progress",map(progress));
                case "mainline-item"->open("mainlineId",s.str(),"index",s.integer(),"task",task,"progress",s.nullable(progress.deepCopy()));
                default->open("planId",s.str(),"index",s.integer(),"tag",tag,"reassignedTaskIds",s.array(s.str()),"replacementTagId",s.nullable(s.str()));
            };
            ObjectNode row=open("id",s.str(),"type",s.text(kind),"title",s.str(),"deletedAt",stamp(),"payload",payload);row.putArray("required").add("type").add("payload");types.add(row);
        }
        ObjectNode marker=open("id",s.str(),"type",s.text("plan","mainline","mainline-item","tag"),"entityId",s.str(),"parentId",s.nullable(s.str()),"replacementId",s.nullable(s.str()),"deletedAt",stamp());
        ObjectNode template=task.deepCopy();((ObjectNode)template.get("properties")).set("name",s.str());((ObjectNode)template.get("properties")).set("resourceUrl",s.str());
        ObjectNode workspace=open("activePlanId",s.str(),"defaultPlanTags",s.array(tag),"customPlans",s.array(plan),"customMainlines",s.array(line),"taskTemplates",s.array(template),"trash",s.array(trash).put("maxItems",1000),"deletionMarkers",s.array(marker).put("maxItems",10000),"updatedAt",stamp());
        ObjectNode catalog=open("defaultPlan",plan,"builtInMainlines",s.array(line));
        ObjectNode common=open("schemaVersion",s.integer().put("minimum",1).put("maximum",6),"planVersion",s.str(),"progress",map(progress),"reflections",map(reflection),"gates",map(open("gateId",s.str(),"checks",map(s.bool()),"note",s.str(),"reviewedAt",s.nullable(stamp()))),"workspace",workspace,"migrationCatalog",catalog,"exportedAt",stamp(),"updatedAt",stamp());
        common.putArray("required").add("schemaVersion").add("progress").add("reflections");
        ObjectNode versions=json.createObjectNode();ArrayNode all=versions.putArray("allOf");all.add(common);ArrayNode one=all.addObject().putArray("oneOf");
        for(int v=1;v<=6;v++){
            ObjectNode root=open("schemaVersion",s.integer().put("minimum",v).put("maximum",v));root.putArray("required").add("schemaVersion");
            root.put("description","旧标准格式"+v+"的兼容字段指南；workspace与catalog可缺省。早期可缺timeEntries/acceptanceChecks，schema2可缺customMainlines、schema3可缺defaultPlanTags、schema4可缺trash/deletionMarkers。未知字段保留在快照；关系及时间另由预检/事务验证。");one.add(root);
        }
        return versions;
    }
    ObjectNode request(String key){
        if(!java.util.Set.of("POST /migrations/legacy/preview","POST /migrations/legacy/import","POST /migrations/legacy-snapshot").contains(key))return null;
        ObjectNode raw=json.createObjectNode();
        raw.put("x-intentionally-unconstrained-json",true);
        raw.put("description",key.endsWith("/preview")?"任意可解析JSON可提交预检；不支持格式返回200且ready=false，不是成功导入。":key.endsWith("legacy-snapshot")?"任意可解析JSON可保存原始快照，不要求预检ready=true；不表示关系转换成功。":"兼容JsonNode入口；成功导入先要求schemaVersion按Jackson asInt解释为1—6、progress/reflections为对象及预检ready=true，再执行事务校验。错误输入仍可提交但不保证成功。标准字段见x-canonical-legacy-envelopes；该扩展不是额外拒绝旧数据的运行时验证器。");
        raw.putObject("x-canonical-legacy-envelopes").put("$ref","#/components/schemas/legacy.CanonicalEnvelopes");return raw;
    }
}
