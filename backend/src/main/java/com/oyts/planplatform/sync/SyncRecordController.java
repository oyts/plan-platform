package com.oyts.planplatform.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.common.NotFoundException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Read-only editable snapshot for one explicitly selected conflict, never arbitrary database access. */
@RestController @RequestMapping("/api/sync/record")
class SyncRecordController {
    record Snapshot(long version,Map<String,Object> fields){}
    private final JdbcTemplate db;private final ObjectMapper mapper;
    SyncRecordController(JdbcTemplate db,ObjectMapper mapper){this.db=db;this.mapper=mapper;}
    @GetMapping @Transactional(readOnly=true)
    ResponseEntity<Snapshot> read(@AuthenticationPrincipal Jwt jwt,@RequestParam String path){
        if(!path.matches("/(plans|tags|tasks|milestones|templates|mainlines(?:/items)?|todos)/[a-fA-F0-9-]{36}"))throw new IllegalArgumentException("此操作不支持逐项比较，请到原页面处理。不会自动重放计时或账号操作。");
        String kind=path.substring(1,path.lastIndexOf('/')),id=path.substring(path.lastIndexOf('/')+1);
        String from;String[] columns;Set<String> arrays=Set.of("acceptance"),booleans=Set.of("required","done","deleted");
        switch(kind){
            case "plans"->{from="plans t where t.owner_id=? and t.deleted_at is null";columns=new String[]{"name","description","start_date:startDate","end_date:endDate","rest_days_mask:restDays"};}
            case "tags"->{from="plan_tags t join plans p on p.id=t.plan_id where p.owner_id=? and t.deleted_at is null and p.deleted_at is null";columns=new String[]{"name","color"};}
            case "tasks"->{from="plan_tasks t join plans p on p.id=t.plan_id where p.owner_id=? and t.deleted_at is null and p.deleted_at is null";columns=new String[]{"tag_id:tagId","task_date:date","start_time:startTime","end_time:endTime","planned_minutes:plannedMinutes","category","title","description","acceptance_json:acceptance","resource_url:resourceUrl","required_task:required","sort_order:sortOrder"};}
            case "milestones"->{from="plan_milestones t join plans p on p.id=t.plan_id where p.owner_id=? and t.deleted_at is null and p.deleted_at is null";columns=new String[]{"name","milestone_date:date","description","acceptance_json:acceptance"};}
            case "templates"->{from="task_templates t where t.owner_id=? and t.deleted_at is null";columns=new String[]{"name","category","title","description","planned_minutes:plannedMinutes","acceptance_json:acceptance","resource_url:resourceUrl","required_task:required"};}
            case "mainlines"->{from="mainlines t where t.owner_id=? and t.deleted_at is null";columns=new String[]{"name","description","color"};}
            case "mainlines/items"->{from="mainline_items t join mainlines p on p.id=t.mainline_id where p.owner_id=? and t.deleted_at is null and p.deleted_at is null";columns=new String[]{"title","description","acceptance_json:acceptance","planned_minutes:plannedMinutes","sort_order:sortOrder"};}
            case "todos"->{from="todos t where t.owner_id=?";columns=new String[]{"title","note","due_date:dueDate","done","deleted"};}
            default->throw new IllegalArgumentException("不支持此操作。");
        }
        var snapshots=db.query("select t.* from "+from+" and t.id=?",(rs,n)->{
            Map<String,Object> fields=new LinkedHashMap<>();for(String spec:columns){String[] pair=spec.split(":");String column=pair[0],name=pair.length==1?column:pair[1];Object value=rs.getObject(column);
                if(value!=null){
                    if(name.equals("restDays")){int mask=rs.getInt(column);value=java.util.stream.IntStream.rangeClosed(1,7).filter(day->(mask&(1<<(day-1)))!=0).boxed().toList();}
                    else if(arrays.contains(name)){try{value=mapper.readTree(rs.getString(column));}catch(Exception error){throw new IllegalStateException("原记录内容无法读取。",error);}}
                    else if(booleans.contains(name))value=rs.getBoolean(column);
                    else if(value instanceof java.sql.Date day)value=day.toLocalDate().toString();else if(value instanceof java.sql.Time time)value=time.toLocalTime().toString();
                }fields.put(name,value);
            }return new Snapshot(rs.getLong("version"),fields);
        },jwt.getSubject(),id);
        if(snapshots.isEmpty())throw new NotFoundException("记录不存在、已移入回收站或不属于当前账号。原本地操作仍保留。");
        return ResponseEntity.ok().header("Cache-Control","no-store").body(snapshots.getFirst());
    }
}
