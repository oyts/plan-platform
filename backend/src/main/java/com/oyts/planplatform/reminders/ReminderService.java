package com.oyts.planplatform.reminders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.common.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.oyts.planplatform.reflection.ReflectionService;
import com.oyts.planplatform.reflection.ReflectionDtos.Kind;
import static com.oyts.planplatform.reminders.ReminderDtos.*;

/** Only fresh server state authorizes delivery. A lost claim response is not replayed. */
@Service @Transactional
public class ReminderService {
    private final JdbcTemplate db; private final ObjectMapper json;private final ReflectionService reflections;
    @Autowired(required=false) private Clock clock=Clock.systemUTC();
    public ReminderService(JdbcTemplate db,ObjectMapper json,ReflectionService reflections) { this.db=db;this.json=json;this.reflections=reflections; }
    private record Account(String password,long generation,boolean enabled) {}
    private Account lock(String owner) {
        var rows=db.query("select password_hash,auth_generation,enabled from users where id=? for update",(r,n)->new Account(r.getString(1),r.getLong(2),r.getBoolean(3)),owner);
        if(rows.isEmpty()||!rows.getFirst().enabled())throw new UnauthorizedException("提醒授权已失效，请重新登录。");
        return rows.getFirst();
    }
    private String stamp(Account a) { return hash(a.password()+":"+a.generation()); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    @Transactional(readOnly=true) public Preferences preferences(String owner) {
        var rows=db.query("select config_json,version from native_reminder_preferences where owner_id=?",(r,n)->{
            try { Preferences p=json.readValue(r.getString(1),Preferences.class);return version(p,r.getLong(2)); }
            catch(Exception invalid) { throw new IllegalStateException("提醒配置无法读取。"); }
        },owner);
        return rows.isEmpty()?Preferences.defaults():rows.getFirst();
    }
    private Preferences version(Preferences p,Long v) { return new Preferences(p.advanceEnabled(),p.advanceMinutes(),p.startEnabled(),p.todoEnabled(),p.todoTime(),p.zone(),p.quietEnabled(),p.quietFrom(),p.quietUntil(),p.hideDetails(),p.primaryDeviceId(),v); }
    public Preferences save(String owner,Preferences p) {
        lock(owner);
        try{ZoneId.of(p.zone());LocalTime.parse(p.todoTime());LocalTime.parse(p.quietFrom());LocalTime.parse(p.quietUntil());}
        catch(DateTimeException invalid){throw new IllegalArgumentException("请使用有效的时区和HH:mm时间。");}
        if(p.quietEnabled()&&p.quietFrom().equals(p.quietUntil()))throw new IllegalArgumentException("免打扰起止时间不能相同。");
        if(p.primaryDeviceId()!=null&&!p.primaryDeviceId().isBlank()&&db.queryForObject("select count(*) from native_reminder_devices where id=? and owner_id=? and enabled=true",Long.class,p.primaryDeviceId(),owner)!=1)throw new NotFoundException("请选择自己的有效提醒设备。");
        var current=preferences(owner);
        if(!Objects.equals(current.version(),p.version()))throw new ConflictException("提醒设置已在其他设备修改，请重新读取后再保存。");
        long next=current.version()==null?0:current.version()+1;
        try {
            String body=json.writeValueAsString(version(p,next));
            if(current.version()==null)db.update("insert into native_reminder_preferences(owner_id,config_json,version) values(?,?,?)",owner,body,next);
            else db.update("update native_reminder_preferences set config_json=?,version=? where owner_id=?",body,next,owner);
        } catch(com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalArgumentException("提醒配置无法保存。"); }
        return version(p,next);
    }
    public Registration register(String owner,Register request) {
        Account account=lock(owner);Instant now=clock.instant();
        if(db.queryForObject("select count(*) from native_reminder_devices where owner_id=? and enabled=true and expires_at>?",Long.class,owner,Timestamp.from(now))>=8)throw new IllegalArgumentException("最多启用8台提醒设备，请先关闭不再使用的设备。");
        String id=UUID.randomUUID().toString(),secret=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
        Instant expiry=now.plus(Duration.ofDays(30));
        db.update("insert into native_reminder_devices(id,owner_id,platform,label,token_hash,account_stamp,enabled,expires_at,created_at) values(?,?,?,?,?,?,true,?,?)",id,owner,request.platform(),request.label().trim(),hash(secret),stamp(account),Timestamp.from(expiry),Timestamp.from(now));
        return new Registration(id,id+"."+secret,expiry);
    }
    @Transactional(readOnly=true) public List<Device> devices(String owner) {
        return db.query("select id,platform,label,enabled,expires_at,last_seen_at from native_reminder_devices where owner_id=? order by created_at desc limit 50",(r,n)->new Device(r.getString(1),r.getString(2),r.getString(3),r.getBoolean(4),r.getTimestamp(5).toInstant(),r.getTimestamp(6)==null?null:r.getTimestamp(6).toInstant()),owner);
    }
    public void revoke(String owner,String id) {
        lock(owner);if(db.update("update native_reminder_devices set enabled=false where owner_id=? and id=?",owner,id)!=1)throw new NotFoundException("提醒设备不存在。");
    }
    private record Credential(String owner,String tokenHash,String accountStamp,boolean enabled,Instant expires) {}
    public Poll poll(String raw) {
        if(raw==null||!raw.matches("[a-f0-9-]{36}\\.[a-f0-9]{64}"))throw denied();
        String[] parts=raw.split("\\.");
        var values=db.query("select owner_id,token_hash,account_stamp,enabled,expires_at from native_reminder_devices where id=?",(r,n)->new Credential(r.getString(1),r.getString(2),r.getString(3),r.getBoolean(4),r.getTimestamp(5).toInstant()),parts[0]);
        if(values.isEmpty())throw denied();Credential credential=values.getFirst();
        if(!MessageDigest.isEqual(credential.tokenHash().getBytes(StandardCharsets.US_ASCII),hash(parts[1]).getBytes(StandardCharsets.US_ASCII)))throw denied();
        Account account=lock(credential.owner());Instant now=clock.instant();
        // Re-read after acquiring the same owner lock used by revoke/save/governance.
        if(db.queryForObject("select count(*) from native_reminder_devices where id=? and enabled=true and expires_at>?",Long.class,parts[0],Timestamp.from(now))!=1||!credential.accountStamp().equals(stamp(account)))throw denied();
        var p=preferences(credential.owner());
        db.update("update native_reminder_devices set last_seen_at=? where id=?",Timestamp.from(now),parts[0]);
        if(p.primaryDeviceId()!=null&&!p.primaryDeviceId().isBlank()&&!p.primaryDeviceId().equals(parts[0]))return new Poll(now.toEpochMilli(),now.plusSeconds(900).toEpochMilli(),List.of(),List.of(),false);
        ZoneId zone=ZoneId.of(p.zone());LocalDate date=now.atZone(zone).toLocalDate();
        List<Event> events=new ArrayList<>();
        List<Map<String,Object>> tasks=List.of();
        if(p.advanceEnabled()||p.startEnabled()) {
            tasks=db.queryForList("select t.id,t.title,p.id as plan_id,coalesce(g.defer_to,t.task_date) as effective_date,t.start_time from plan_tasks t join plans p on p.id=t.plan_id left join task_progress g on g.task_id=t.id where p.owner_id=? and p.deleted_at is null and p.archived=false and t.deleted_at is null and t.start_time is not null and coalesce(g.status,'PENDING') in ('PENDING','DEFERRED') and coalesce(g.ever_started,false)=false and not exists(select 1 from time_entries e where e.progress_id=g.id) and coalesce(g.defer_to,t.task_date) between ? and ? order by effective_date,t.start_time,t.id limit 2001",credential.owner(),date.minusDays(1),date.plusDays(1));
            for(var t:tasks.stream().limit(2000).toList()) {
                LocalDate day=LocalDate.parse(t.get("effective_date").toString().substring(0,10));
                LocalTime time=LocalTime.parse(t.get("start_time").toString());
                Instant start=day.atTime(time).atZone(zone).toInstant();
                String path="/plans/"+t.get("plan_id")+"?date="+day;
                if(p.advanceEnabled()&&start.isAfter(now))events.add(new Event("ADVANCE:"+t.get("id")+":"+start,start.minusSeconds(p.advanceMinutes()*60L),"周期任务即将开始",p.hideDetails()?"请打开计划舱查看安排。":t.get("title")+"：计划于"+time+"开始。",path));
                if(p.startEnabled())events.add(new Event("START:"+t.get("id")+":"+start,start,"到计划开始时间了，是否开始？",p.hideDetails()?"请打开计划舱核对并开始。":String.valueOf(t.get("title")),path));
            }
        }
        if(p.todoEnabled()) {
            for(int offset=0;offset<=1;offset++) {
                LocalDate day=date.plusDays(offset);Instant at=day.atTime(LocalTime.parse(p.todoTime())).atZone(zone).toInstant();
                if(at.isAfter(now))events.add(new Event("TODO:"+day,at,"今日待办收尾","请打开计划舱查看。","/todos?date="+day));
                else if(at.isAfter(now.minusSeconds(300))) {
                    Long count=db.queryForObject("select count(*) from todos where owner_id=? and deleted=false and done=false and due_date=?",Long.class,credential.owner(),day);
                    if(count>0)events.add(new Event("TODO:"+day,at,"今日待办收尾",p.hideDetails()?"今天还有待办，请打开计划舱查看。":"今天还有"+count+"项待办，按自己的节奏收尾。","/todos?date="+day));
                }
            }
        }
        var reviewPrefs=reflections.preferences(credential.owner());LocalDate reviewDate=now.atZone(ReflectionService.ZONE).toLocalDate();
        for(int offset=0;offset<=1;offset++){
            LocalDate day=reviewDate.plusDays(offset);
            if(reviewPrefs.dailyEnabled())reviewEvent(events,credential.owner(),Kind.DAY,day,day,reviewPrefs.dailyTime(),now);
            if(reviewPrefs.weeklyEnabled()&&day.getDayOfWeek().getValue()==reviewPrefs.weekDay())reviewEvent(events,credential.owner(),Kind.WEEK,day.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),day,reviewPrefs.weeklyTime(),now);
        }
        // One stable event per instant, even when one of the eligible evening reasons changes later.
        Map<Instant,List<Event>> evening=new LinkedHashMap<>();List<Event> combined=new ArrayList<>();
        for(Event e:events){if(e.path().startsWith("/todos?")||e.path().startsWith("/reflection?"))evening.computeIfAbsent(e.at(),k->new ArrayList<>()).add(e);else combined.add(e);}
        for(var group:evening.entrySet()){
            var reasons=group.getValue();var destination=reasons.stream().filter(e->e.path().contains("kind=WEEK")).findFirst().orElseGet(()->reasons.stream().filter(e->e.path().startsWith("/reflection?")).findFirst().orElse(reasons.getFirst()));
            combined.add(new Event("EVENING:"+group.getKey(),group.getKey(),reasons.size()>1?"今日收尾与复盘":destination.title(),reasons.size()>1?"有待办收尾或复盘可回顾；打开计划舱查看，不会自动修改计划。":destination.body(),destination.path()));
        }
        events=combined;events.sort(Comparator.comparing(Event::at));
        long next=now.plusSeconds(900).toEpochMilli();List<Notice> notices=new ArrayList<>();List<String> active=new ArrayList<>();
        for(Event event:events) {
            if(event.at().isAfter(now)) { next=Math.min(next,event.at().toEpochMilli());continue; }
            if(event.at().isBefore(now.minusSeconds(300))||quiet(p,now.atZone(zone).toLocalTime())||quiet(p,event.at().atZone(zone).toLocalTime()))continue;
            String key=hash(event.key());
            if(active.size()<20)active.add(key);
            next=Math.min(next,now.plusSeconds(15).toEpochMilli());
            if(db.queryForObject("select count(*) from native_reminder_deliveries where device_id=? and event_key=?",Long.class,parts[0],key)>0)continue;
            if(notices.size()>=20)break;
            db.update("insert into native_reminder_deliveries(device_id,event_key,claimed_at) values(?,?,?)",parts[0],key,Timestamp.from(now));
            notices.add(new Notice(key,event.title(),event.body(),event.path(),now.plusSeconds(60).toEpochMilli()));
        }
        return new Poll(now.toEpochMilli(),Math.max(next,now.plusSeconds(15).toEpochMilli()),List.copyOf(notices),List.copyOf(active),tasks.size()>2000);
    }
    private record Event(String key,Instant at,String title,String body,String path) {}
    private void reviewEvent(List<Event> events,String owner,Kind kind,LocalDate period,LocalDate day,String time,Instant now){
        if(reflections.reviewed(owner,kind,period))return;
        Instant at=day.atTime(LocalTime.parse(time)).atZone(ReflectionService.ZONE).toInstant();
        if(at.isAfter(now)||at.isAfter(now.minusSeconds(300))&&reflections.applicable(owner,kind,period))events.add(new Event("REVIEW:"+kind+":"+period,at,kind==Kind.DAY?"花一分钟回顾今天？":"回顾本周，按自己的节奏前进","仅含已同步记录，是否分析或调整由你决定。",ReflectionService.path(kind,period)));
    }
    private boolean quiet(Preferences p,LocalTime at) {
        if(!p.quietEnabled())return false;LocalTime from=LocalTime.parse(p.quietFrom()),until=LocalTime.parse(p.quietUntil());
        return from.isBefore(until)?!at.isBefore(from)&&at.isBefore(until):!at.isBefore(from)||at.isBefore(until);
    }
    private UnauthorizedException denied() { return new UnauthorizedException("提醒设备授权失效，请在应用中重新启用。"); }
}
