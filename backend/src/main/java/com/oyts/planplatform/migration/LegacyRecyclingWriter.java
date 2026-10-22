package com.oyts.planplatform.migration;

import com.fasterxml.jackson.databind.*;
import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.mainline.*;
import com.oyts.planplatform.mainline.domain.*;
import com.oyts.planplatform.plan.domain.*;
import com.oyts.planplatform.plan.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Creates only missing recycle snapshots; never updates an existing task or its history. */
@Component
class LegacyRecyclingWriter {
    private final PlanRepository plans; private final PlanTagRepository tags; private final PlanTaskRepository tasks;
    private final TaskProgressRepository progress; private final TimeEntryRepository entries; private final DailyReflectionRepository reflections;
    private final MainlineRepository lines; private final MainlineItemRepository items; private final ObjectMapper json;
    LegacyRecyclingWriter(PlanRepository plans,PlanTagRepository tags,PlanTaskRepository tasks,TaskProgressRepository progress,
                          TimeEntryRepository entries,DailyReflectionRepository reflections,MainlineRepository lines,
                          MainlineItemRepository items,ObjectMapper json) {
        this.plans=plans;this.tags=tags;this.tasks=tasks;this.progress=progress;this.entries=entries;
        this.reflections=reflections;this.lines=lines;this.items=items;this.json=json;
    }
    record Added(int tags,int tasks,int progress,int entries,int reflections,int items,List<String> plans,List<String> lines) {}
    Added write(UserAccount owner,LegacyRecycling.Converted converted,List<LegacyBackfillPreview.Entry> preview) {
        JsonNode root=converted.payload(),progressMap=root.path("progress");
        List<String> planIds=new ArrayList<>(),lineIds=new ArrayList<>();
        int tagCount=0,taskCount=0,entryCount=0,reflectionCount=0,itemCount=0;
        for(var entry:preview) {
            if(!entry.action().equals("CREATE_DELETED_PARENT"))continue;
            boolean isPlan=entry.type().equals("plan");JsonNode source=parent(root,entry.legacyId(),isPlan);
            if(isPlan) {
                Plan plan=plans.save(new Plan(owner,text(source,"name","旧版已删除计划",120),"从旧版回收站补迁",
                    LocalDate.parse(source.path("startDate").asText()),LocalDate.parse(source.path("endDate").asText())));
                plan.importDeletedAt(entry.deletedAt());plans.flush();planIds.add(plan.getId());
                Map<String,PlanTag> tagMap=new HashMap<>();
                for(JsonNode tag:source.path("tags")) {
                    PlanTag added=tag(plan,tag,converted.deletion("tag",entry.legacyId(),tag.path("id").asText()));
                    tagMap.put(tag.path("id").asText(),added);tagCount++;
                }
                int order=0;
                for(JsonNode taskNode:source.path("tasks")) {
                    PlanTask task=new PlanTask(plan);String resource=taskNode.path("resources").isArray()&&!taskNode.path("resources").isEmpty()
                        ?text(taskNode.path("resources").get(0),"url","",1000):text(taskNode,"sourceRef","",1000);
                    task.update(tagMap.get(taskNode.path("tagId").asText()),LocalDate.parse(taskNode.path("date").asText()),
                        time(taskNode,"startTime"),time(taskNode,"endTime"),Math.max(1,taskNode.path("plannedMinutes").asInt(1)),
                        text(taskNode,"category","custom",40).toUpperCase(),text(taskNode,"title","未命名任务",180),text(taskNode,"description","",2000),
                        array(taskNode,"acceptance"),resource,taskNode.path("required").asBoolean(true),order++);
                    tasks.save(task);taskCount++;
                    JsonNode old=progressMap.path(taskNode.path("id").asText());TaskProgress state=new TaskProgress(task);
                    TaskProgress.Status status=status(TaskProgress.Status.class,old.path("status").asText());
                    if(status==TaskProgress.Status.RUNNING)status=TaskProgress.Status.PAUSED;
                    state.importState(status,old.path("accumulatedSeconds").asLong(0),null,instant(old.path("completedAt")),
                        array(old,"acceptanceChecks"),text(old,"note","",2000),old.path("deferTo").asText("").isBlank()?null:LocalDate.parse(old.path("deferTo").asText()),text(old,"skipReason","",1000));
                    progress.save(state);
                    for(JsonNode oldEntry:old.path("timeEntries")) {
                        entries.save(new TimeEntry(state,instant(oldEntry.path("startedAt")),instant(oldEntry.path("endedAt")),
                            oldEntry.path("seconds").asLong(0),text(oldEntry,"source","legacy",20).toUpperCase()));entryCount++;
                    }
                }
                for(var row:root.path("reflections").properties()) {
                    if(!row.getKey().startsWith(entry.legacyId()+":"))continue;
                    JsonNode old=row.getValue();DailyReflection reflection=new DailyReflection(plan,LocalDate.parse(old.path("date").asText()));
                    reflection.update(text(old,"completedSummary","",2000),text(old,"unfinishedSummary","",2000),text(old,"blockers","",2000),
                        text(old,"firstTaskTomorrow","",1000),old.path("energy").isInt()?old.path("energy").asInt():null,text(old,"note","",2000));
                    reflections.save(reflection);reflectionCount++;
                }
            } else {
                Mainline line=lines.save(new Mainline(owner,text(source,"name","旧版已删除路线",120),text(source,"description","",1000),text(source,"color","#002FA7",20)));
                line.importDeletedAt(entry.deletedAt());lines.flush();lineIds.add(line.getId());int order=0;
                for(JsonNode node:source.path("items")) {
                    item(line,node,progressMap.path(node.path("id").asText()),order++,converted.deletion("mainline-item",entry.legacyId(),node.path("id").asText()));itemCount++;
                }
            }
        }
        for(var entry:preview) {
            if(!entry.action().equals("ATTACH_DELETED_CHILD"))continue;
            boolean isTag=entry.type().equals("tag");JsonNode parent=parent(root,entry.legacyParentId(),isTag);
            int index=0;JsonNode source=null;
            for(JsonNode node:parent.path(isTag?"tags":"items")) {if(node.path("id").asText().equals(entry.legacyId())){source=node;break;}index++;}
            if(source==null)throw new IllegalArgumentException("补迁子项快照缺失。");
            if(isTag){tag(plans.getReferenceById(entry.targetParentId()),source,entry.deletedAt());tagCount++;}
            else {
                // Existing order is user-owned. Append a deleted record without shifting live steps.
                Integer maximum=items.maximumOrder(entry.targetParentId());
                if(maximum!=null&&maximum==Integer.MAX_VALUE)throw new com.oyts.planplatform.common.ConflictException("原路线顺序空间不足，请先整理后重新预检。");
                item(lines.getReferenceById(entry.targetParentId()),source,progressMap.path(entry.legacyId()),maximum==null?0:maximum+1,entry.deletedAt());itemCount++;
                items.flush();
            }
        }
        return new Added(tagCount,taskCount,taskCount,entryCount,reflectionCount,itemCount,List.copyOf(planIds),List.copyOf(lineIds));
    }
    private PlanTag tag(Plan plan,JsonNode node,Instant deleted) {
        PlanTag tag=tags.save(new PlanTag(plan,text(node,"name","未命名标签",60),text(node,"color","#002FA7",20),node.path("isOverall").asBoolean(false)));
        if(deleted!=null)tag.importDeletedAt(deleted);return tag;
    }
    private void item(Mainline line,JsonNode node,JsonNode old,int order,Instant deleted) {
        MainlineItem item=new MainlineItem(line);item.update(text(node,"title","未命名步骤",180),text(node,"description","",2000),array(node,"acceptance"),Math.max(1,node.path("plannedMinutes").asInt(1)),order);
        MainlineItem.Status status=status(MainlineItem.Status.class,old.path("status").asText());if(status==MainlineItem.Status.RUNNING)status=MainlineItem.Status.PAUSED;
        item.importState(status,old.path("accumulatedSeconds").asLong(0),text(old,"note","",2000),null,instant(old.path("completedAt")));
        if(deleted!=null)item.importDeletedAt(deleted);items.save(item);
    }
    private JsonNode parent(JsonNode root,String id,boolean plan) {
        List<JsonNode> found=new ArrayList<>();JsonNode catalog=root.path("migrationCatalog");
        if(plan&&catalog.path("defaultPlan").isObject())found.add(catalog.path("defaultPlan"));
        if(!plan)catalog.path("builtInMainlines").forEach(found::add);
        root.path("workspace").path(plan?"customPlans":"customMainlines").forEach(found::add);
        return found.stream().filter(n->n.path("id").asText().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("补迁父快照缺失。"));
    }
    private String text(JsonNode node,String key,String fallback,int limit){String value=node.path(key).isTextual()?node.path(key).asText().strip():fallback;return value.substring(0,Math.min(value.length(),limit));}
    private String array(JsonNode node,String key){return node.path(key).isArray()?node.path(key).toString():"[]";}
    private LocalTime time(JsonNode node,String key){String value=node.path(key).asText("");return value.isBlank()?null:LocalTime.parse(value);}
    private Instant instant(JsonNode node){String value=node.asText("");if(value.isBlank())return null;try{return Instant.parse(value);}catch(java.time.format.DateTimeParseException ignored){}try{return OffsetDateTime.parse(value).toInstant();}catch(java.time.format.DateTimeParseException ignored){}return LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Shanghai")).toInstant();}
    private <E extends Enum<E>> E status(Class<E> type,String value){try{return Enum.valueOf(type,value.toUpperCase());}catch(IllegalArgumentException e){return Enum.valueOf(type,"PENDING");}}
}
