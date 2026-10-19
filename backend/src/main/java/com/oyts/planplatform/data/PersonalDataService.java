package com.oyts.planplatform.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.common.*;
import com.oyts.planplatform.plan.PlanDtos.PageResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.oyts.planplatform.data.BackupSchema.*;

@Service @Transactional(readOnly=true)
class PersonalDataService {
    static final int MAX_BYTES=5*1024*1024,MAX_ROWS=20000;
    static final String FORMAT="plan-cabin-private-backup";
    private static final Set<String> STATES=Set.of("PENDING","RUNNING","PAUSED","COMPLETED","PARTIAL","SKIPPED","DEFERRED");
    private final JdbcTemplate jdbc;private final EntityManager em;private final ObjectMapper mapper;private final String version;
    PersonalDataService(JdbcTemplate jdbc,EntityManager em,ObjectMapper mapper,@Value("${app.version}")String version){this.jdbc=jdbc;this.em=em;this.mapper=mapper;this.version=version;}
    record Preview(String checksum,Map<String,Integer> counts,List<String> warnings,int totalRecords){}
    record RestoreReport(String batchId,Instant createdAt,Map<String,Integer> counts,List<String> planIds,List<String> routeIds){}
    private record Validated(ObjectNode payload,Map<String,List<ObjectNode>> rows,Preview preview){}
    record WeeklyCopy(String id,String batchId,LocalDate weekStart,String wins,String blockers,String nextFocus){}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    ObjectNode backup(String owner){
        ObjectNode result=mapper.createObjectNode();result.put("format",FORMAT);result.put("schemaVersion",2);result.put("sourceVersion",version);result.put("exportedAt",Instant.now().toString());
        result.set("records",queryRecords(owner,NAMES));validate(result);return result;
    }
    private ObjectNode queryRecords(String owner,Set<String> requested){
        ObjectNode records=mapper.createObjectNode();int total=0;int[] bytes={0};
        for(Table table:TABLES){
            if(!requested.contains(table.name()))continue;
            String columns=String.join(",",table.fields().stream().map(f->"t."+f.name()).toList());
            String sql="select "+columns+" from "+table.from();Object[] args={owner};
            if(table.name().equals("weekly_reviews")){sql+=" union all select "+columns+" from restored_weekly_reviews t where t.owner_id=?";args=new Object[]{owner,owner};}
            sql+=" order by id limit "+(MAX_ROWS+1);
            String query=sql;Object[] parameters=args;
            var rows=jdbc.query(connection->{
                var statement=connection.prepareStatement(query);for(int i=0;i<parameters.length;i++)statement.setString(i+1,(String)parameters[i]);
                // Connector/J streaming prevents buffering every large private row before the byte cap can stop the read.
                if(connection.getMetaData().getDatabaseProductName().equals("MySQL"))statement.setFetchSize(Integer.MIN_VALUE);
                return statement;
            },(rs,n)->{
                ObjectNode row=mapper.createObjectNode();
                for(Field field:table.fields()){
                    Object value=rs.getObject(field.name());if(value==null){row.putNull(field.name());continue;}
                    switch(field.kind()){
                        case DATE -> row.put(field.name(),rs.getDate(field.name()).toLocalDate().toString());
                        case TIME -> row.put(field.name(),rs.getTime(field.name()).toLocalTime().toString());
                        case INSTANT -> row.put(field.name(),rs.getTimestamp(field.name()).toInstant().toString());
                        case BOOLEAN -> row.put(field.name(),rs.getBoolean(field.name()));
                        case INTEGER -> row.put(field.name(),rs.getLong(field.name()));
                        case STRINGS,BOOLEANS,IDS -> row.set(field.name(),parseArray(rs.getString(field.name())));
                        default -> row.put(field.name(),rs.getString(field.name()));
                    }
                }
                bytes[0]+=json(row).getBytes(StandardCharsets.UTF_8).length;
                if(bytes[0]>MAX_BYTES)throw new IllegalArgumentException("本次导出超过 5 MiB，请保留原数据并联系管理员规划分批导出。");
                return row;
            });
            total+=rows.size();if(total>MAX_ROWS)throw new IllegalArgumentException("已同步记录超过单次备份 20000 条上限，请联系管理员规划分批迁移，不要清理原数据。");
            records.set(table.name(),mapper.valueToTree(rows));
        }
        return records;
    }
    Preview preview(JsonNode payload){return validate(payload).preview();}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    String readable(String owner,String kind){
        Set<String> tables=switch(kind){case "todos"->Set.of("todos");case "weekly"->Set.of("weekly_reviews");case "routes"->Set.of("mainlines","mainline_stages","mainline_items");default->throw new IllegalArgumentException("导出类型不支持。");};
        var data=queryRecords(owner,tables);CsvBuffer csv=new CsvBuffer();
        if(kind.equals("todos")){
            csvRow(csv,List.of("日期","任务","备注","已完成","完成时间","已删除"));for(JsonNode row:data.path("todos"))csvRow(csv,List.of(text(row,"due_date"),text(row,"title"),text(row,"note"),text(row,"done"),text(row,"completed_at"),text(row,"deleted")));
        }else if(kind.equals("weekly")){
            csvRow(csv,List.of("周一日期","本周收获","阻塞","下周重点"));for(JsonNode row:data.path("weekly_reviews"))csvRow(csv,List.of(text(row,"week_start"),text(row,"wins"),text(row,"blockers"),text(row,"next_focus")));
        }else if(kind.equals("routes")){
            csvRow(csv,List.of("路线","路线说明","推进方式","归档","阶段","步骤","步骤说明","状态","计划分钟","累计秒数","完成时间","依赖方式","前置步骤编号","删除时间"));
            Map<String,JsonNode> stages=new HashMap<>();data.path("mainline_stages").forEach(row->stages.put(text(row,"id"),row));
            for(JsonNode line:data.path("mainlines")){
                var items=new ArrayList<JsonNode>();data.path("mainline_items").forEach(item->{if(text(item,"mainline_id").equals(text(line,"id")))items.add(item);});
                if(items.isEmpty())csvRow(csv,List.of(text(line,"name"),text(line,"description"),text(line,"execution_mode"),text(line,"archived"),"","","","","","","","","",""));
                for(var item:items){JsonNode stage=stages.get(text(item,"stage_id"));csvRow(csv,List.of(text(line,"name"),text(line,"description"),text(line,"execution_mode"),text(line,"archived"),stage==null?"":text(stage,"name"),text(item,"title"),text(item,"description"),text(item,"status"),text(item,"planned_minutes"),text(item,"accumulated_seconds"),text(item,"completed_at"),text(item,"dependency_mode"),item.path("prerequisite_ids_json").toString(),text(item,"deleted_at")));}
            }
        }else throw new IllegalArgumentException("导出类型不支持。");return csv.toString();
    }
    private String text(JsonNode row,String key){return row.path(key).isNull()?"":row.path(key).asText();}
    private static final class CsvBuffer{
        final StringBuilder text=new StringBuilder();int bytes;
        @Override public String toString(){return text.toString();}
    }
    private void csvRow(CsvBuffer out,List<String> values){String row=String.join(",",values.stream().map(CsvCell::encode).toList())+'\n';out.bytes+=row.getBytes(StandardCharsets.UTF_8).length;if(out.bytes>MAX_BYTES)throw new IllegalArgumentException("CSV 超过 5 MiB，请保留原数据并联系管理员分批导出。");out.text.append(row);}

    @Transactional
    RestoreReport restore(String owner,JsonNode payload,String checksum,String operationKey,boolean confirmed){
        if(!confirmed||operationKey==null||!uuid(operationKey))throw new IllegalArgumentException("请先预检并明确确认恢复为新副本。");
        var validated=validate(payload);
        if(!validated.preview().checksum().equals(checksum))throw new ConflictException("文件或预检内容已变化，请重新预检再确认。");
        var user=em.find(UserAccount.class,owner,LockModeType.PESSIMISTIC_WRITE);
        if(user==null||!user.isEnabled())throw new ForbiddenException("当前账号不可用。");
        var prior=jdbc.queryForList("select checksum,report_json from personal_restore_batches where owner_id=? and operation_key=?",owner,operationKey);
        if(!prior.isEmpty()){
            if(!checksum.equals(prior.getFirst().get("checksum")))throw new ConflictException("该恢复编号已用于另一份文件。");
            return report((String)prior.getFirst().get("report_json"));
        }
        var same=jdbc.queryForList("select report_json from personal_restore_batches where owner_id=? and checksum=?",owner,checksum);
        if(!same.isEmpty())return report((String)same.getFirst().get("report_json"));
        String batch=UUID.randomUUID().toString();Instant now=Instant.now();
        Map<String,String> ids=new HashMap<>();
        validated.rows().forEach((table,rows)->rows.forEach(row->ids.put(table+":"+row.path("id").asText(),UUID.randomUUID().toString())));
        try{
            jdbc.update("insert into personal_restore_batches(id,owner_id,operation_key,checksum,report_json,created_at) values(?,?,?,?,?,?)",batch,owner,operationKey,checksum,"{}",Timestamp.from(now));
            for(Table table:TABLES)for(ObjectNode row:validated.rows().get(table.name())){
                List<String> columns=new ArrayList<>();List<Object> values=new ArrayList<>();
                for(Field field:table.fields()){
                    columns.add(field.name());JsonNode node=row.get(field.name());Object value=sqlValue(field,node);
                    if(!node.isNull()){
                        if(field.kind()==Kind.ID)value=ids.get(table.name()+":"+node.asText());
                        if(field.kind()==Kind.REFERENCE)value=ids.get(field.reference()+":"+node.asText());
                        if(field.kind()==Kind.BATCH)value=ids.computeIfAbsent("recurrence:"+node.asText(),k->UUID.randomUUID().toString());
                        if(field.kind()==Kind.IDS){List<String> mapped=new ArrayList<>();node.forEach(id->mapped.add(ids.get("mainline_items:"+id.asText())));value=json(mapped);}
                    }
                    if((table.name().equals("task_progress")||table.name().equals("mainline_items"))&&field.name().equals("started_at"))value=null;
                    if(field.name().equals("status")&&node.asText().equals("RUNNING"))value="PAUSED";
                    if(table.name().equals("task_templates")&&field.name().equals("name")){
                        String suffix="（恢复"+ids.get(table.name()+":"+row.path("id").asText()).substring(0,8)+"）";
                        String name=node.asText();int end=Math.min(name.length(),80-suffix.length());if(end>0&&Character.isHighSurrogate(name.charAt(end-1)))end--;value=name.substring(0,end)+suffix;
                    }
                    if(table.name().equals("task_templates")&&field.name().equals("restored_original_name")&&node.isNull())value=row.path("name").asText();
                    values.add(value);
                }
                if(table.owned()){columns.add("owner_id");values.add(owner);}
                if(table.name().equals("weekly_reviews")){columns.add("batch_id");values.add(batch);}
                else if(!table.name().equals("time_entries")){columns.add("version");values.add(0);}
                String sql="insert into "+table.target()+"("+String.join(",",columns)+") values("+String.join(",",Collections.nCopies(columns.size(),"?"))+")";
                jdbc.update(sql,values.toArray());
            }
            var report=new RestoreReport(batch,now,validated.preview().counts(),newIds(validated,ids,"plans"),newIds(validated,ids,"mainlines"));
            jdbc.update("update personal_restore_batches set report_json=? where id=?",json(report),batch);return report;
        }catch(DataIntegrityViolationException failure){throw new ConflictException("恢复未完成，整批数据已回滚。文件可能违反唯一性或数据库约束；请保留原文件并反馈，不要删除原数据。");}
    }
    PageResponse<RestoreReport> history(String owner,int page){
        page(page);long count=jdbc.queryForObject("select count(*) from personal_restore_batches where owner_id=?",Long.class,owner);
        var rows=jdbc.query("select report_json from personal_restore_batches where owner_id=? order by created_at desc,id limit 10 offset ?",(rs,n)->report(rs.getString(1)),owner,page*10);
        return new PageResponse<>(rows,page,10,count,(int)((count+9)/10));
    }
    PageResponse<WeeklyCopy> weeklyCopies(String owner,int page){
        page(page);long count=jdbc.queryForObject("select count(*) from restored_weekly_reviews where owner_id=?",Long.class,owner);
        var rows=jdbc.query("select id,batch_id,week_start,wins,blockers,next_focus from restored_weekly_reviews where owner_id=? order by week_start desc,id limit 10 offset ?",(rs,n)->new WeeklyCopy(rs.getString(1),rs.getString(2),rs.getDate(3).toLocalDate(),rs.getString(4),rs.getString(5),rs.getString(6)),owner,page*10);
        return new PageResponse<>(rows,page,10,count,(int)((count+9)/10));
    }
    private void page(int page){if(page<0||page>100000)throw new IllegalArgumentException("页码无效。");}
    private List<String> newIds(Validated v,Map<String,String> ids,String table){return v.rows().get(table).stream().map(row->ids.get(table+":"+row.path("id").asText())).toList();}
    private RestoreReport report(String value){try{return mapper.readValue(value,RestoreReport.class);}catch(JsonProcessingException e){throw new IllegalStateException("恢复记录无法读取。");}}

    private Validated validate(JsonNode payload){
        if(payload==null||!payload.isObject()||!keys(payload).equals(Set.of("format","schemaVersion","sourceVersion","exportedAt","records"))||!FORMAT.equals(payload.path("format").asText())||!payload.path("schemaVersion").isIntegralNumber()||!payload.path("schemaVersion").canConvertToInt()||!Set.of(1,2).contains(payload.path("schemaVersion").asInt()))
            throw new IllegalArgumentException("仅支持计划舱私有备份 schemaVersion 1或2，不能使用旧版迁移或其他软件的文件。");
        if(json(payload).getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("备份文件不能超过 5 MiB。");
        if(!payload.path("sourceVersion").isTextual()||!payload.path("sourceVersion").asText().matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}([+-][A-Za-z0-9.-]{1,24})?"))throw new IllegalArgumentException("备份版本无效。");
        instant(payload.path("exportedAt"),true);
        JsonNode records=payload.path("records");if(!records.isObject()||!keys(records).equals(NAMES))throw new IllegalArgumentException("备份记录类型缺失或包含未允许的内容。");
        Map<String,List<ObjectNode>> rows=new LinkedHashMap<>();Map<String,Map<String,ObjectNode>> indexes=new HashMap<>();Map<String,Integer> counts=new LinkedHashMap<>();int total=0,running=0;
        for(Table table:TABLES){
            var values=records.path(table.name());if(!values.isArray())throw invalid(table.name(),"必须是记录数组");
            total+=values.size();if(total>MAX_ROWS)throw new IllegalArgumentException("备份不能超过 20000 条记录。");
            List<ObjectNode> list=new ArrayList<>();Map<String,ObjectNode> index=new HashMap<>();
            var allowed=table.fields().stream().map(Field::name).collect(java.util.stream.Collectors.toSet());
            boolean oldProgress=payload.path("schemaVersion").asInt()==1&&table.name().equals("task_progress");
            if(oldProgress)allowed.remove("ever_started");
            for(JsonNode value:values){
                if(!value.isObject()||!keys(value).equals(allowed))throw invalid(table.name(),"字段缺失或包含未知字段（不允许账号、凭据或所有权字段）");
                var row=(ObjectNode)value.deepCopy();
                if(oldProgress)row.put("ever_started",row.path("started_at").isTextual()||row.path("accumulated_seconds").asLong()>0||Set.of("RUNNING","PAUSED","PARTIAL","COMPLETED").contains(row.path("status").asText()));
                for(Field field:table.fields())check(table.name(),field,row.get(field.name()));
                if(index.putIfAbsent(row.path("id").asText(),row)!=null)throw invalid(table.name(),"记录编号重复");
                if(row.path("status").asText().equals("RUNNING"))running++;list.add(row);
            }
            rows.put(table.name(),list);indexes.put(table.name(),index);counts.put(table.name(),list.size());
        }
        for(Table table:TABLES)for(ObjectNode row:rows.get(table.name()))for(Field field:table.fields()){
            var node=row.get(field.name());if(field.kind()==Kind.REFERENCE&&!node.isNull()&&!indexes.get(field.reference()).containsKey(node.asText()))throw invalid(table.name(),"引用了备份以外的记录");
        }
        relations(rows,indexes);
        var warnings=new ArrayList<String>();warnings.add("只恢复为新记录，不覆盖现有内容；所有 ID 和关系重新映射。模板名附带恢复标记，过长时缩短显示名；完整原名另外保留在备份字段 restored_original_name。");
        warnings.add("不含账号凭据、AI 对话、反馈、本机未提交队列、客户端偏好和服务器配置。此文件包含私人业务内容，请妥善保存。");
        warnings.add("周复盘恢复为数据中心内的只读副本，保留原周次，不覆盖当前周复盘。已删除和归档状态保留。");
        if(running>0)warnings.add(running+" 个运行中计时将恢复为暂停；只保留已结算用时和流水，不估算尚未结算的时间。");
        String checksum=hash(json(canonical(payload)));return new Validated((ObjectNode)payload,rows,new Preview(checksum,Map.copyOf(counts),List.copyOf(warnings),total));
    }
    private void check(String table,Field f,JsonNode value){
        if(value==null||value.isNull()){if(!f.nullable())throw invalid(table,"必填字段为空");return;}
        try{
            switch(f.kind()){
                case STRING -> {if(!value.isTextual()||value.asText().length()>f.max()||f.nonblank()&&value.asText().isBlank())throw invalid(table,"文字长度或类型无效");
                    if(f.name().equals("resource_url")&&!value.asText().isBlank()&&!value.asText().matches("(?i)https?://[^\\s]+"))throw invalid(table,"资源链接仅支持 HTTP 或 HTTPS");}
                case ID,REFERENCE,BATCH -> {if(!value.isTextual()||!uuid(value.asText()))throw invalid(table,"记录编号无效");}
                case INTEGER -> {if(!value.isIntegralNumber()||!value.canConvertToLong()||value.asLong()<f.min()||value.asLong()>f.upper())throw invalid(table,"数字超出允许范围");}
                case BOOLEAN -> {if(!value.isBoolean())throw invalid(table,"布尔类型无效");}
                case DATE -> {if(!value.isTextual()||!value.asText().matches("\\d{4}-\\d{2}-\\d{2}"))throw invalid(table,"日期无效");LocalDate d=LocalDate.parse(value.asText());if(d.getYear()<1000)throw invalid(table,"日期超出数据库范围");}
                case TIME -> {if(!value.isTextual()||value.asText().length()>18)throw invalid(table,"时间无效");LocalTime.parse(value.asText());}
                case INSTANT -> instant(value,false);
                case STRINGS,BOOLEANS,IDS -> {
                    if(!value.isArray()||value.size()>512||json(value).length()>(f.kind()==Kind.IDS?4000:256000))throw invalid(table,"列表过大或格式错误");
                    Set<String> distinct=new HashSet<>();
                    for(JsonNode element:value){
                        if(f.kind()==Kind.BOOLEANS){if(!element.isBoolean())throw invalid(table,"勾选列表类型无效");}
                        else if(!element.isTextual()||element.asText().length()>500)throw invalid(table,"列表文字无效");
                        if(f.kind()==Kind.IDS&&(!uuid(element.asText())||!distinct.add(element.asText())))throw invalid(table,"依赖编号无效或重复");
                    }
                }
            }
        }catch(DateTimeException e){throw invalid(table,"日期或时间格式无效");}
    }
    private void relations(Map<String,List<ObjectNode>> rows,Map<String,Map<String,ObjectNode>> indexes){
        for(var row:rows.get("plans"))if(LocalDate.parse(row.path("end_date").asText()).isBefore(LocalDate.parse(row.path("start_date").asText())))throw invalid("plans","结束日期早于开始日期");
        unique(rows.get("plan_tags"),"plan_id","name");unique(rows.get("daily_reflections"),"plan_id","reflection_date");unique(rows.get("mainline_items"),"mainline_id","sort_order");unique(rows.get("task_progress"),"task_id");
        if(rows.get("task_progress").size()!=rows.get("plan_tasks").size())throw invalid("task_progress","每个任务必须有且仅有一份进度");
        for(var task:rows.get("plan_tasks"))if(!task.path("tag_id").isNull()&&!indexes.get("plan_tags").get(task.path("tag_id").asText()).path("plan_id").equals(task.path("plan_id")))throw invalid("plan_tasks","标签不属于对应计划");
        for(String table:List.of("task_progress","mainline_items"))for(var row:rows.get(table))if(!STATES.contains(row.path("status").asText()))throw invalid(table,"进度状态无效");
          for(var row:rows.get("time_entries")){
              if(Instant.parse(row.path("ended_at").asText()).isBefore(Instant.parse(row.path("started_at").asText())))throw invalid("time_entries","流水结束早于开始");
              indexes.get("task_progress").get(row.path("progress_id").asText()).put("ever_started",true);
          }
        for(var row:rows.get("weekly_reviews"))if(LocalDate.parse(row.path("week_start").asText()).getDayOfWeek()!=DayOfWeek.MONDAY)throw invalid("weekly_reviews","周次必须为周一");
        for(var line:rows.get("mainlines")){
            String lineId=line.path("id").asText(),mode=line.path("execution_mode").asText();
            if(!Set.of("SEQUENTIAL","FLEXIBLE").contains(mode))throw invalid("mainlines","推进方式无效");
            var items=rows.get("mainline_items").stream().filter(row->row.path("mainline_id").asText().equals(lineId)).toList();
            if(items.size()>1000)throw invalid("mainline_items","单路线不能超过 1000 步，请先规划分组迁移");
            Map<String,Integer> incoming=new HashMap<>();Map<String,List<String>> dependents=new HashMap<>();
            for(var item:items){
                String id=item.path("id").asText(),dependency=item.path("dependency_mode").asText();
                if(!Set.of("NONE","INHERIT","EXPLICIT").contains(dependency))throw invalid("mainline_items","依赖方式无效");
                if(!item.path("stage_id").isNull()&&!indexes.get("mainline_stages").get(item.path("stage_id").asText()).path("mainline_id").asText().equals(lineId))throw invalid("mainline_items","阶段不属于对应路线");
                List<String> parents=new ArrayList<>();
                if(dependency.equals("EXPLICIT"))for(JsonNode parent:item.path("prerequisite_ids_json")){
                    var target=indexes.get("mainline_items").get(parent.asText());if(target==null||!target.path("mainline_id").asText().equals(lineId)||parent.asText().equals(id))throw invalid("mainline_items","依赖引用其他路线或自身");parents.add(parent.asText());
                }
                else if(!item.path("prerequisite_ids_json").isEmpty())throw invalid("mainline_items","非指定依赖方式不能携带前置列表");
                if(dependency.equals("INHERIT")&&mode.equals("SEQUENTIAL"))items.stream().filter(other->other.path("sort_order").asInt()<item.path("sort_order").asInt()).forEach(other->parents.add(other.path("id").asText()));
                incoming.put(id,parents.size());for(String parent:parents)dependents.computeIfAbsent(parent,k->new ArrayList<>()).add(id);
            }
            ArrayDeque<String> available=new ArrayDeque<>();incoming.forEach((id,count)->{if(count==0)available.add(id);});int visited=0;
            while(!available.isEmpty()){String id=available.remove();visited++;for(String dependent:dependents.getOrDefault(id,List.of()))if(incoming.merge(dependent,-1,Integer::sum)==0)available.add(dependent);}
            if(visited!=items.size())throw invalid("mainline_items","依赖形成循环");
        }
    }
    private void unique(List<ObjectNode> rows,String...fields){Set<String> values=new HashSet<>();for(var row:rows){var key=Arrays.stream(fields).map(f->row.path(f).toString().toLowerCase(Locale.ROOT)).toList();if(!values.add(json(key)))throw new IllegalArgumentException("备份含重复的唯一关系，请保留文件并检查。");}}
    private Object sqlValue(Field f,JsonNode node){if(node.isNull())return null;return switch(f.kind()){
        case INTEGER -> node.asLong();case BOOLEAN -> node.asBoolean();case DATE -> java.sql.Date.valueOf(node.asText());case TIME -> Time.valueOf(LocalTime.parse(node.asText()));case INSTANT -> Timestamp.from(Instant.parse(node.asText()));
        case STRINGS,BOOLEANS,IDS -> json(node);default -> node.asText();};}
    private void instant(JsonNode value,boolean metadata){
        if(!value.isTextual()||value.asText().length()>40)throw new IllegalArgumentException("备份时间无效。");
        try{Instant parsed=Instant.parse(value.asText());
            if(!metadata&&(parsed.isBefore(Instant.parse("1970-01-01T00:00:01Z"))||parsed.isAfter(Instant.parse("2038-01-19T03:14:07.499999Z"))||parsed.getNano()%1000!=0))throw new IllegalArgumentException("记录时间超出 MySQL TIMESTAMP(6) 的范围或微秒精度。");
        }catch(DateTimeException e){throw new IllegalArgumentException("备份时间无效。");}
    }
    private JsonNode parseArray(String value){try{return mapper.readTree(value);}catch(JsonProcessingException e){throw new IllegalArgumentException("当前账号中有无法读取的列表，请保留原数据并反馈。");}}
    private Set<String> keys(JsonNode value){Set<String> keys=new HashSet<>();value.fieldNames().forEachRemaining(keys::add);return keys;}
    private boolean uuid(String value){return value!=null&&value.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}");}
    private IllegalArgumentException invalid(String table,String detail){return new IllegalArgumentException("备份 "+table+"："+detail+"。");}
    private JsonNode canonical(JsonNode value){
        if(value.isObject()){ObjectNode result=mapper.createObjectNode();new TreeSet<>(keys(value)).forEach(key->result.set(key,canonical(value.get(key))));return result;}
        if(value.isArray()){ArrayNode result=mapper.createArrayNode();value.forEach(item->result.add(canonical(item)));return result;}return value;
    }
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException("备份内容无法编码。");}}
    private String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
