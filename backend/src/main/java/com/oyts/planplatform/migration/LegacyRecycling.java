package com.oyts.planplatform.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;

/** Pure conversion of the schema1-6 recycle envelopes. Never edits the retained original snapshot. */
final class LegacyRecycling {
    record Key(String type, String parent, String id) {}
    record Converted(JsonNode payload, Map<Key,Instant> deleted, int markersOnly) {
        Instant deletion(String type,String parent,String id) { return deleted.get(new Key(type,parent,id)); }
    }
    private static final Set<String> TYPES=Set.of("plan","mainline","mainline-item","tag");

    static Converted convert(JsonNode source) {
        if(!source.path("workspace").isObject())return new Converted(source.deepCopy(),Map.of(),0);
        ObjectNode root=source.deepCopy();ObjectNode workspace=(ObjectNode)root.path("workspace");
        ArrayNode trash=array(workspace,"trash"),markers=array(workspace,"deletionMarkers");
        if(trash.size()>1000||markers.size()>10000)throw invalid("回收站最多1000项、删除标记最多10000项；原文件未改变。");
        Map<Key,JsonNode> entries=new LinkedHashMap<>();Map<Key,Instant> deleted=new LinkedHashMap<>();Set<String> entryIds=new HashSet<>();
        for(JsonNode entry:trash){
            String type=text(entry,"type");if(!TYPES.contains(type)||!entry.path("payload").isObject())throw invalid("不支持的回收站类型或快照。");
            JsonNode body=entry.path("payload");String parent="",id;
            switch(type){
                case "plan" -> id=text(body.path("plan"),"id");
                case "mainline" -> id=text(body.path("track"),"id");
                case "mainline-item" -> {parent=text(body,"mainlineId");id=text(body.path("task"),"id");}
                default -> {parent=text(body,"planId");id=text(body.path("tag"),"id");}
            }
            if(!text(entry,"id").equals("trash:"+type+":"+id)||!entryIds.add(text(entry,"id")))throw invalid("回收站编号与快照不一致或重复。");
            Key key=new Key(type,parent,id);if(entries.putIfAbsent(key,body)!=null)throw invalid("重复的回收站对象。");
            deleted.put(key,deletionTime(entry));
        }
        // Deletion markers are tombstones. A marker without a snapshot must not
        // fabricate a recoverable record or resurrect a stale active copy.
        Set<Key> permanent=new LinkedHashSet<>();Set<String> seen=new HashSet<>();Map<Key,String> replacements=new HashMap<>();
        for(JsonNode marker:markers){
            String type=text(marker,"type");if(!TYPES.contains(type))throw invalid("不支持的删除标记类型。");
            String id=text(marker,"entityId"),parent=(type.equals("tag")||type.equals("mainline-item"))?text(marker,"parentId"):"";
            if(!text(marker,"id").equals("trash:"+type+":"+id))throw invalid("删除标记编号与对象不一致。");
            Instant markerTime=deletionTime(marker);
            Key key=new Key(type,parent,id);if(!seen.add(text(marker,"id")))throw invalid("重复的删除标记。");
            if(!entries.containsKey(key)){
                if(entryIds.contains(text(marker,"id")))throw invalid("删除标记与快照父对象不一致。");
                permanent.add(key);if(marker.path("replacementId").isTextual())replacements.put(key,text(marker,"replacementId"));
            }else if(!markerTime.equals(deleted.get(key)))throw invalid("删除标记与回收站快照时间不一致，请先核对旧文件。");
        }
        ArrayNode plans=array(workspace,"customPlans"),lines=array(workspace,"customMainlines");
        array(workspace,"taskTemplates");
        ObjectNode progress=object(root,"progress"),reflections=object(root,"reflections");
        for(Key key:permanent){
            switch(key.type()){
                case "plan" -> {remove(plans,key.id());JsonNode catalog=root.path("migrationCatalog");if(catalog.isObject()&&catalog.path("defaultPlan").path("id").asText().equals(key.id()))((ObjectNode)catalog).remove("defaultPlan");}
                case "mainline" -> {remove(lines,key.id());JsonNode built=root.path("migrationCatalog").path("builtInMainlines");if(built.isArray())remove((ArrayNode)built,key.id());}
                case "mainline-item" -> {ObjectNode parent=find(root,key.parent(),false);if(parent!=null)remove(array(parent,"items"),key.id());}
                case "tag" -> {ObjectNode parent=find(root,key.parent(),true);if(parent!=null){remove(array(parent,"tags"),key.id());replacement(parent,key.id(),replacements.get(key));}}
            }
        }
        // Parents first; children may belong to another recycle snapshot.
        for(var row:entries.entrySet()){
            Key key=row.getKey();JsonNode body=row.getValue();
            if(key.type().equals("plan")||key.type().equals("mainline")){
                boolean plan=key.type().equals("plan");JsonNode snapshot=body.path(plan?"plan":"track");
                if(!snapshot.isObject())throw invalid("缺少完整父对象快照。");
                if(permanent.contains(new Key(key.type(),"",key.id())))throw invalid("回收站与永久删除标记冲突。");
                if(find(root,key.id(),plan)!=null)throw invalid("活动对象与回收站父对象编号重复，请先核对旧备份。");
                (plan?plans:lines).add(snapshot.deepCopy());merge(progress,body.path("progress"));
                if(plan)merge(reflections,body.path("reflections"));
            }
        }
        for(var row:entries.entrySet()){
            Key key=row.getKey();JsonNode body=row.getValue();if(!key.type().equals("tag")&&!key.type().equals("mainline-item"))continue;
            boolean tag=key.type().equals("tag");ObjectNode parent=find(root,key.parent(),tag);
            if(parent==null)throw invalid("回收站子项缺少原计划／路线；请保留快照并先找回父对象。");
            JsonNode snapshot=body.path(tag?"tag":"task");ArrayNode children=array(parent,tag?"tags":"items");
            if(!snapshot.isObject()||contains(children,key.id()))throw invalid("回收站子项快照缺失或与现有编号冲突。");
            if(!body.path("index").isIntegralNumber()||!body.path("index").canConvertToInt())throw invalid("回收站子项顺序无效。");
            children.insert(Math.max(0,Math.min(body.path("index").asInt(),children.size())),snapshot.deepCopy());
            if(!tag&&body.path("progress").isObject())progress.set(key.id(),body.path("progress").deepCopy());
        }
        // Missing tag tombstones cannot leave invalid tag references in active tasks.
        Set<String> taskIds=new HashSet<>();
        for(JsonNode plan:all(root,true)){
            Set<String> tagIds=new HashSet<>();
            for(JsonNode tag:array((ObjectNode)plan,"tags"))if(!tagIds.add(text(tag,"id")))throw invalid("同一计划中的标签编号重复。");
            for(JsonNode task:array((ObjectNode)plan,"tasks")){
                if(!taskIds.add(text(task,"id")))throw invalid("任务／步骤编号重复。");
                String id=task.path("tagId").asText("");if(!id.isEmpty()&&!tagIds.contains(id))throw invalid("任务引用缺失标签，不能静默丢失关联。");
            }
        }
        for(JsonNode line:all(root,false))for(JsonNode item:array((ObjectNode)line,"items"))if(!taskIds.add(text(item,"id")))throw invalid("任务／步骤编号重复。");
        return new Converted(root,Map.copyOf(deleted),permanent.size());
    }
    private static void replacement(ObjectNode plan,String removed,String replacement){
        for(JsonNode task:plan.path("tasks"))if(task.path("tagId").asText().equals(removed)){
            if(replacement==null||replacement.equals(removed)||!contains(plan.path("tags"),replacement))throw invalid("永久删除标签仍被任务引用且缺少有效替代标签。");
            ((ObjectNode)task).put("tagId",replacement);
        }
    }
    private static List<JsonNode> all(JsonNode root,boolean plans){
        List<JsonNode> result=new ArrayList<>();JsonNode catalog=root.path("migrationCatalog");
        if(plans){if(catalog.path("defaultPlan").isObject())result.add(catalog.path("defaultPlan"));}
        else catalog.path("builtInMainlines").forEach(result::add);
        root.path("workspace").path(plans?"customPlans":"customMainlines").forEach(result::add);
        if(result.stream().anyMatch(n->!n.isObject()))throw invalid("父对象必须是JSON对象。");
        Set<String> ids=new HashSet<>();for(JsonNode node:result)if(!ids.add(text(node,"id")))throw invalid("父对象编号重复。");return result;
    }
    private static ObjectNode find(JsonNode root,String id,boolean plans){
        List<JsonNode> found=all(root,plans).stream().filter(n->n.path("id").asText().equals(id)).toList();
        if(found.size()>1)throw invalid("父对象编号重复。");return found.isEmpty()?null:(ObjectNode)found.getFirst();
    }
    private static void merge(ObjectNode target,JsonNode value){
        if(!value.isObject())throw invalid("回收站缺少完整进度／复盘快照。");
        value.properties().forEach(row->target.set(row.getKey(),row.getValue().deepCopy()));
    }
    private static boolean contains(JsonNode nodes,String id){for(JsonNode node:nodes)if(node.path("id").asText().equals(id))return true;return false;}
    private static void remove(ArrayNode nodes,String id){for(int i=nodes.size()-1;i>=0;i--)if(nodes.get(i).path("id").asText().equals(id))nodes.remove(i);}
    private static ArrayNode array(ObjectNode node,String field){if(node.path(field).isMissingNode())return node.putArray(field);if(!node.path(field).isArray())throw invalid(field+"必须是数组。");return (ArrayNode)node.path(field);}
    private static ObjectNode object(ObjectNode node,String field){if(!node.path(field).isObject())throw invalid(field+"必须是对象。");return (ObjectNode)node.path(field);}
    private static String text(JsonNode node,String field){JsonNode value=node.path(field);if(!value.isTextual()||value.asText().isBlank()||value.asText().length()>250)throw invalid("缺少有效的"+field+"。");return value.asText();}
    private static Instant deletionTime(JsonNode node){
        try{Instant value=Instant.parse(text(node,"deletedAt"));
            if(value.isBefore(Instant.parse("1970-01-01T00:00:01Z"))||value.isAfter(Instant.parse("2038-01-19T03:14:07Z")))throw invalid("删除时间超出MySQL时间范围。");
            return value;
        }catch(java.time.DateTimeException failure){throw invalid("删除时间无效。");}
    }
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException("旧版回收站预检："+message);}
}
