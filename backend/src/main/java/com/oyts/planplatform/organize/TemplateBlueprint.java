package com.oyts.planplatform.organize;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Strict structural whitelist shared by template capture and private backup preflight. */
public final class TemplateBlueprint {
    private TemplateBlueprint(){}
    public static final int MAX_BYTES=262144;
    static final Map<String,List<String>> FIELDS=Map.of(
        "PLAN",List.of("id","name","description","start_date","end_date","rest_days_mask"),
        "ROUTE",List.of("id","name","description","color","execution_mode"),
        "plan_tags",List.of("id","name","color","overall_tag"),
        "plan_tasks",List.of("id","tag_id","task_date","start_time","end_time","planned_minutes","category","title","description","acceptance_json","resource_url","required_task","sort_order","recurrence_id"),
        "plan_milestones",List.of("id","name","milestone_date","description","acceptance_json"),
        "mainline_stages",List.of("id","name","description","sort_order"),
        "mainline_items",List.of("id","title","description","acceptance_json","planned_minutes","sort_order","stage_id","dependency_mode","prerequisite_ids_json"));
    static List<String> tables(String kind){return kind.equals("PLAN")?List.of("plan_tags","plan_tasks","plan_milestones"):List.of("mainline_stages","mainline_items");}
    static String encode(ObjectMapper mapper,Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw bad();}}
    static JsonNode parse(ObjectMapper mapper,String value){try{return mapper.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value);}catch(Exception e){throw bad();}}
    static ObjectNode capture(ObjectMapper mapper,String kind,OrchestrationService.Snapshot snapshot){
        ObjectNode root=mapper.createObjectNode();root.put("schemaVersion",1);root.put("kind",kind);
        root.set("parent",pick(mapper,snapshot.parent(),kind));ObjectNode rows=root.putObject("rows");
        for(String table:tables(kind)){var list=rows.putArray(table);for(var row:snapshot.rows().get(table))list.add(pick(mapper,row,table));}
        validate(root,kind);return root;
    }
    private static ObjectNode pick(ObjectMapper mapper,Map<String,Object> source,String table){ObjectNode n=mapper.createObjectNode();for(String f:FIELDS.get(table))n.set(f,mapper.valueToTree(source.get(f)));return n;}
    static OrchestrationService.Snapshot snapshot(ObjectMapper mapper,JsonNode node){
        validate(node,node.path("kind").asText());Map<String,List<Map<String,Object>>> rows=new LinkedHashMap<>();
        for(String table:tables(node.path("kind").asText())){var list=new ArrayList<Map<String,Object>>();for(var row:node.path("rows").path(table))list.add(mapper.convertValue(row,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));rows.put(table,list);}
        return new OrchestrationService.Snapshot(mapper.convertValue(node.path("parent"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}),rows);
    }
    static ObjectNode detach(ObjectMapper mapper,JsonNode source){
        ObjectNode result=source.deepCopy();Map<String,String> ids=new HashMap<>();ids.put(result.path("parent").path("id").asText(),UUID.randomUUID().toString());
        result.path("rows").elements().forEachRemaining(list->list.forEach(row->ids.put(row.path("id").asText(),UUID.randomUUID().toString())));
        List<JsonNode> all=new ArrayList<>();all.add(result.path("parent"));result.path("rows").elements().forEachRemaining(list->list.forEach(all::add));
        Map<String,String> recurrence=new HashMap<>();
        for(JsonNode row:all){ObjectNode r=(ObjectNode)row;r.put("id",ids.get(r.path("id").asText()));for(String f:List.of("tag_id","stage_id"))if(r.hasNonNull(f))r.put(f,ids.get(r.get(f).asText()));
            if(r.hasNonNull("recurrence_id"))r.put("recurrence_id",recurrence.computeIfAbsent(r.get("recurrence_id").asText(),k->UUID.randomUUID().toString()));
            if(r.has("prerequisite_ids_json")){var list=parse(mapper,r.get("prerequisite_ids_json").asText());List<String> mapped=new ArrayList<>();list.forEach(x->mapped.add(ids.get(x.asText())));r.put("prerequisite_ids_json",encode(mapper,mapped));}}
        validate(result,result.path("kind").asText());return result;
    }
    public static void validate(JsonNode root,String kind){
        if(!Set.of("PLAN","ROUTE").contains(kind)||!root.isObject()||!keys(root).equals(Set.of("schemaVersion","kind","parent","rows"))||!root.path("schemaVersion").isInt()||root.path("schemaVersion").asInt()!=1||!root.path("kind").asText().equals(kind)||root.toString().getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw bad();
        var mapper=new ObjectMapper();JsonNode parent=root.path("parent"),rows=root.path("rows");
        if(!rows.isObject()||!keys(rows).equals(new HashSet<>(tables(kind))))throw bad();
        Set<String> ids=new HashSet<>();validateRow(mapper,parent,kind,ids);
        Map<String,Map<String,JsonNode>> indexes=new HashMap<>();
        for(String table:tables(kind)){var list=rows.path(table);if(!list.isArray()||list.size()>1000)throw bad();var index=new LinkedHashMap<String,JsonNode>();for(JsonNode row:list){validateRow(mapper,row,table,ids);index.put(row.path("id").asText(),row);}indexes.put(table,index);}
        if(kind.equals("PLAN")){
            LocalDate from=LocalDate.parse(parent.path("start_date").asText()),to=LocalDate.parse(parent.path("end_date").asText());if(to.isBefore(from))throw bad();
            Set<String> names=new HashSet<>();int overall=0;for(JsonNode tag:rows.path("plan_tags")){if(!names.add(tag.path("name").asText().toLowerCase(Locale.ROOT)))throw bad();if(tag.path("overall_tag").asBoolean())overall++;}if(overall>1)throw bad();
            for(String table:List.of("plan_tasks","plan_milestones"))for(JsonNode row:rows.path(table)){LocalDate date=LocalDate.parse(row.path(table.equals("plan_tasks")?"task_date":"milestone_date").asText());if(date.isBefore(from)||date.isAfter(to))throw bad();if(row.hasNonNull("tag_id")&&!indexes.get("plan_tags").containsKey(row.path("tag_id").asText()))throw bad();}
        }else{
            Map<String,List<String>> dependencies=new HashMap<>();Set<Integer> order=new HashSet<>();
            for(JsonNode item:rows.path("mainline_items")){
                if(!order.add(item.path("sort_order").asInt())||item.hasNonNull("stage_id")&&!indexes.get("mainline_stages").containsKey(item.path("stage_id").asText()))throw bad();
                List<String> deps=new ArrayList<>();var list=parse(mapper,item.path("prerequisite_ids_json").asText());list.forEach(x->deps.add(x.asText()));String mode=item.path("dependency_mode").asText();
                if(!mode.equals("EXPLICIT")&&!deps.isEmpty())throw bad();
                if(mode.equals("INHERIT")&&parent.path("execution_mode").asText().equals("SEQUENTIAL"))for(var other:rows.path("mainline_items"))if(other.path("sort_order").asInt()<item.path("sort_order").asInt())deps.add(other.path("id").asText());
                if(deps.stream().anyMatch(id->!indexes.get("mainline_items").containsKey(id)||id.equals(item.path("id").asText()))||new HashSet<>(deps).size()!=deps.size())throw bad();dependencies.put(item.path("id").asText(),deps);
            }
            // Iterative topological validation; no recursive stack exhaustion on long routes.
            Set<String> done=new HashSet<>();while(done.size()<dependencies.size()){int before=done.size();dependencies.forEach((id,deps)->{if(done.containsAll(deps))done.add(id);});if(done.size()==before)throw bad();}
        }
    }
    private static void validateRow(ObjectMapper mapper,JsonNode row,String table,Set<String> ids){
        if(!row.isObject()||!keys(row).equals(new HashSet<>(FIELDS.get(table))))throw bad();
        for(String field:FIELDS.get(table)){
            JsonNode v=row.get(field);boolean nullable=Set.of("tag_id","stage_id","recurrence_id","start_time","end_time").contains(field);if(v.isNull()){if(!nullable)throw bad();continue;}
            if(field.equals("id")||field.endsWith("_id")){if(!v.isTextual()||!v.asText().matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")||field.equals("id")&&!ids.add(v.asText()))throw bad();}
            else if(Set.of("overall_tag","required_task").contains(field)){if(!v.isBoolean())throw bad();}
            else if(Set.of("planned_minutes","sort_order","rest_days_mask").contains(field)){
                long min=field.equals("planned_minutes")?1:field.equals("sort_order")&&!table.equals("mainline_items")?Integer.MIN_VALUE:0;
                long max=field.equals("rest_days_mask")?127:field.equals("planned_minutes")&&table.equals("plan_tasks")?1440:Integer.MAX_VALUE;
                if(!v.isIntegralNumber()||!v.canConvertToInt()||v.asLong()<min||v.asLong()>max)throw bad();
            }else{
                if(!v.isTextual())throw bad();String text=v.asText();int max=switch(field){case "name"->table.equals("plan_tags")?60:120;case "title"->180;case "description"->table.equals("plan_tasks")||table.equals("mainline_items")?2000:1000;case "category"->40;case "resource_url"->1000;case "acceptance_json","prerequisite_ids_json"->256000;default->40;};
                if(text.length()>max||Set.of("name","title","category","color").contains(field)&&text.isBlank())throw bad();
                try{
                    if(field.endsWith("_date")){LocalDate d=LocalDate.parse(text);if(d.getYear()<1000||d.getYear()>9998)throw bad();}
                    if(field.endsWith("_time"))LocalTime.parse(text);
                }catch(DateTimeException e){throw bad();}
                if(field.equals("color")&&!text.matches("#[a-fA-F0-9]{6}"))throw bad();
                if(field.equals("execution_mode")&&!Set.of("SEQUENTIAL","FLEXIBLE").contains(text)||field.equals("dependency_mode")&&!Set.of("NONE","INHERIT","EXPLICIT").contains(text))throw bad();
                if(field.equals("resource_url")&&!text.isEmpty()&&!text.matches("(?i)https?://[^\\s]+"))throw bad();
                if(field.endsWith("_json")){JsonNode list=parse(mapper,text);if(!list.isArray()||list.size()>512)throw bad();for(JsonNode x:list)if(!x.isTextual()||x.asText().length()>500)throw bad();}
            }
        }
    }
    private static Set<String> keys(JsonNode node){Set<String> k=new HashSet<>();node.fieldNames().forEachRemaining(k::add);return k;}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("模板结构无效、超过1000项或256KiB，或含执行／未知字段、无效关系。请保留原内容并检查后重试。");}
}
