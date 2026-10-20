package com.oyts.planplatform.reflection;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oyts.planplatform.common.*;
import com.oyts.planplatform.daily.DailyService;
import com.oyts.planplatform.plan.PlanDtos.PageResponse;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.oyts.planplatform.reflection.ReflectionDtos.*;

@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ReflectionService {
 public static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 private final JdbcTemplate db;private final ObjectMapper json;private final DailyService daily;
 private static final String PLAN=" from plan_tasks t join plans p on p.id=t.plan_id left join task_progress g on g.task_id=t.id where p.owner_id=? and p.deleted_at is null and t.deleted_at is null";
 private static final String TODO=" from todos t where t.owner_id=? and t.deleted=false";
 public ReflectionService(JdbcTemplate db,ObjectMapper json,DailyService daily){this.db=db;this.json=json;this.daily=daily;}
 public static void period(Kind kind,LocalDate start){if(kind==null||start==null||start.getYear()<1000||start.getYear()>9998||kind==Kind.WEEK&&start.getDayOfWeek()!=DayOfWeek.MONDAY)throw new IllegalArgumentException("请选择有效日期；周复盘以周一开始。");}
 private static LocalDate end(Kind k,LocalDate d){return k==Kind.WEEK?d.plusDays(6):d;}
 private long count(String sql,Object...args){return db.queryForObject(sql,Long.class,args);}
 private void lock(String owner){if(db.query("select id from users where id=? and enabled=true for update",(r,n)->r.getString(1),owner).isEmpty())throw new NotFoundException("账号不存在。");}
 public Facts facts(String owner,Kind kind,LocalDate start){
  period(kind,start);LocalDate through=end(kind,start);Timestamp from=Timestamp.from(start.atStartOfDay(ZONE).toInstant()),until=Timestamp.from(through.plusDays(1).atStartOfDay(ZONE).toInstant());
  Mode plan=mode(owner,PLAN,"coalesce(g.defer_to,t.task_date)","g.status='COMPLETED'","coalesce(g.status,'PENDING')='SKIPPED'","g.completed_at",start,through,from,until);
  Mode todo=mode(owner,TODO,"t.due_date","t.done=true","1=0","t.completed_at",start,through,from,until);
  long routes=count("select count(*) from mainline_items i join mainlines r on r.id=i.mainline_id where r.owner_id=? and r.deleted_at is null and i.deleted_at is null and i.status='COMPLETED' and i.completed_at>=? and i.completed_at<?",owner,from,until);
  long seconds=count("select coalesce(sum(e.seconds),0) from time_entries e join task_progress g on g.id=e.progress_id join plan_tasks t on t.id=g.task_id join plans p on p.id=t.plan_id where p.owner_id=? and e.started_at>=? and e.started_at<?",owner,from,until);
  boolean has=plan.arranged()+todo.arranged()-plan.skipped()>0;
  return new Facts(kind,start,through,Instant.now(),ZONE.getId(),plan,todo,routes,seconds,has,has&&plan.open()+todo.open()==0);
 }
 private Mode mode(String owner,String base,String day,String done,String skipped,String completed,LocalDate from,LocalDate to,Timestamp at,Timestamp until){
  long arranged=count("select count(*)"+base+" and "+day+" between ? and ?",owner,from,to);
  long completedArranged=count("select count(*)"+base+" and "+day+" between ? and ? and "+done,owner,from,to);
  long skip=count("select count(*)"+base+" and "+day+" between ? and ? and "+skipped,owner,from,to);
  long actual=count("select count(*)"+base+" and "+done+" and "+completed+">=? and "+completed+"<?",owner,at,until);
  long unknown=count("select count(*)"+base+" and "+done+" and "+completed+" is null",owner);
  return new Mode(arranged,completedArranged,arranged-completedArranged-skip,skip,actual,unknown);
 }
 public PageResponse<Item> items(String owner,Kind kind,LocalDate start,String mode,String bucket,int page,int size){
  period(kind,start);if(!Set.of("PLAN","TODO","ROUTE").contains(mode)||!Set.of("ARRANGED","OPEN","ACTUAL").contains(bucket)||mode.equals("ROUTE")&&!bucket.equals("ACTUAL"))throw new IllegalArgumentException("路线只按实际完成步骤展示，不设每日欠账。");
  if(page<0||page>100000||size<1||size>50)throw new IllegalArgumentException("每页最多50项。");
  LocalDate through=end(kind,start);List<Object> args=new ArrayList<>();args.add(owner);
  String select,base,day,done,completed,open;
  if(mode.equals("PLAN")){select="select 'PLAN',t.id,p.id,t.title,coalesce(g.defer_to,t.task_date),coalesce(g.status,'PENDING'),g.completed_at";base=PLAN;day="coalesce(g.defer_to,t.task_date)";done="g.status='COMPLETED'";completed="g.completed_at";open="coalesce(g.status,'PENDING') not in ('COMPLETED','SKIPPED')";}
  else if(mode.equals("TODO")){select="select 'TODO',t.id,t.id,t.title,t.due_date,case when t.done=true then 'COMPLETED' else 'PENDING' end,t.completed_at";base=TODO;day="t.due_date";done="t.done=true";completed="t.completed_at";open="t.done=false";}
  else {select="select 'ROUTE',i.id,r.id,i.title,cast(null as date),i.status,i.completed_at";base=" from mainline_items i join mainlines r on r.id=i.mainline_id where r.owner_id=? and r.deleted_at is null and i.deleted_at is null";day="i.completed_at";done="i.status='COMPLETED'";completed="i.completed_at";open="1=0";}
  String filter;
  if(bucket.equals("ACTUAL")){filter=" and "+done+" and "+completed+">=? and "+completed+"<?";args.add(Timestamp.from(start.atStartOfDay(ZONE).toInstant()));args.add(Timestamp.from(through.plusDays(1).atStartOfDay(ZONE).toInstant()));}
  else {filter=" and "+day+" between ? and ?"+(bucket.equals("OPEN")?" and "+open:"");args.add(start);args.add(through);}
  long total=count("select count(*)"+base+filter,args.toArray());var queryArgs=new ArrayList<>(args);queryArgs.add(size);queryArgs.add((long)page*size);
  var rows=db.query(select+base+filter+" order by "+(bucket.equals("ACTUAL")?completed:day)+",2 limit ? offset ?",(r,n)->new Item(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getDate(5)==null?null:r.getDate(5).toLocalDate(),r.getString(6),r.getTimestamp(7)==null?null:r.getTimestamp(7).toInstant()),queryArgs.toArray());
  return new PageResponse<>(rows,page,size,total,(int)((total+size-1)/size));
 }
 public Notes notes(String owner,LocalDate date){period(Kind.DAY,date);var rows=db.query("select wins,blockers,next_focus,energy,burden,reviewed,version,updated_at from daily_reviews where owner_id=? and review_date=?",(r,n)->new Notes(date,r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getBoolean(6),r.getLong(7),r.getTimestamp(8).toInstant()),owner,date);return rows.isEmpty()?new Notes(date,"","","","UNSPECIFIED","UNSPECIFIED",false,null,null):rows.getFirst();}
 @Transactional public Notes save(String owner,LocalDate date,Save r){
  period(Kind.DAY,date);lock(owner);Notes old=notes(owner,date);if(!Objects.equals(old.version(),r.version()))throw new ConflictException("日复盘已在其他设备变化，当前输入已保留。请比较最新版本后再保存。");
  Timestamp now=Timestamp.from(Instant.now());if(old.version()==null)db.update("insert into daily_reviews(id,owner_id,review_date,wins,blockers,next_focus,energy,burden,reviewed,version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,0,?,?)",UUID.randomUUID().toString(),owner,date,clean(r.wins()),clean(r.blockers()),clean(r.nextFocus()),r.energy(),r.burden(),r.reviewed(),now,now);
  else db.update("update daily_reviews set wins=?,blockers=?,next_focus=?,energy=?,burden=?,reviewed=?,version=version+1,updated_at=? where owner_id=? and review_date=?",clean(r.wins()),clean(r.blockers()),clean(r.nextFocus()),r.energy(),r.burden(),r.reviewed(),now,owner,date);
  return notes(owner,date);
 }
 private String clean(String s){return s==null?"":s.trim();}
 public Preferences preferences(String owner){var rows=db.query("select config_json,version from reflection_preferences where owner_id=?",(r,n)->{try{return withVersion(json.readValue(r.getString(1),Preferences.class),r.getLong(2));}catch(Exception e){throw new IllegalStateException("复盘偏好无法读取。");}},owner);return rows.isEmpty()?Preferences.defaults():rows.getFirst();}
 private Preferences withVersion(Preferences p,Long v){return new Preferences(p.completionGuide(),p.dailyEnabled(),p.dailyTime(),p.weeklyEnabled(),p.weekDay(),p.weeklyTime(),p.trendEnabled(),v);}
 @Transactional public Preferences savePreferences(String owner,Preferences p){lock(owner);var prior=preferences(owner);if(!Objects.equals(prior.version(),p.version()))throw new ConflictException("复盘偏好已变化，请重新读取。");long v=prior.version()==null?0:prior.version()+1;var value=withVersion(p,v);if(prior.version()==null)db.update("insert into reflection_preferences(owner_id,config_json,version) values(?,?,?)",owner,encode(value),v);else db.update("update reflection_preferences set config_json=?,version=? where owner_id=?",encode(value),v,owner);return value;}
 public boolean reviewed(String owner,Kind kind,LocalDate start){return count("select count(*) from "+(kind==Kind.DAY?"daily_reviews":"weekly_reviews")+" where owner_id=? and "+(kind==Kind.DAY?"review_date":"week_start")+"=? and reviewed=true",owner,start)>0;}
 public boolean applicable(String owner,Kind kind,LocalDate start){return meaningful(facts(owner,kind,start));}
 @Transactional public List<Guide> claimGuide(String owner){
  lock(owner);LocalDate day=LocalDate.now(ZONE);if(!preferences(owner).completionGuide()||reviewed(owner,Kind.DAY,day))return List.of();Facts f=facts(owner,Kind.DAY,day);if(!f.allApplicableComplete())return List.of();
  if(count("select count(*) from reflection_guides where owner_id=? and guide_kind='COMPLETED_DAY' and period_start=?",owner,day)>0)return List.of();
  db.update("insert into reflection_guides(owner_id,guide_kind,period_start,claimed_at) values(?,'COMPLETED_DAY',?,?)",owner,day,Timestamp.from(Instant.now()));
  return List.of(new Guide("COMPLETED_DAY",day,"今天安排的周期任务与待办已收尾，要花一分钟回顾吗？",path(Kind.DAY,day)));
 }
 public static String path(Kind kind,LocalDate start){return "/reflection?kind="+kind+"&date="+start;}
 public Trend trend(String owner,LocalDate start){
  period(Kind.WEEK,start);var saved=db.query("select offered_at,decision from reflection_coaching where owner_id=?",(r,n)->Map.entry(r.getTimestamp(1).toInstant(),r.getString(2)),owner);
  Instant next=saved.isEmpty()?null:saved.getFirst().getKey().plus(Duration.ofDays(14));String decision=saved.isEmpty()?null:saved.getFirst().getValue();
  var current=daily.review(owner,start);boolean increase=current.openToIncrease();
  if(!preferences(owner).trendEnabled())return new Trend(false,"主动趋势提示已关闭；仍可手动分析。",next,increase,List.of(),decision);
  if(start.plusDays(7).isAfter(LocalDate.now(ZONE)))return new Trend(false,"完整两周记录形成后再讨论趋势，单日失误不需要重排。",next,increase,List.of(),decision);
  LocalDate previous=start.minusDays(7);if(!reviewed(owner,Kind.WEEK,start)||!reviewed(owner,Kind.WEEK,previous))return new Trend(false,"需要连续两周已完成的周复盘。",next,increase,List.of(),decision);
  Facts a=facts(owner,Kind.WEEK,previous),b=facts(owner,Kind.WEEK,start);
  if(!meaningful(a)||!meaningful(b))return new Trend(false,"两周都需要有实际安排或完成记录，空周不作为趋势。",next,increase,List.of(),decision);
  if(next!=null&&next.isAfter(Instant.now()))return new Trend(false,"上次节奏讨论后的14天冷却中，可先回顾调整感受。",next,increase,List.of(a,b),decision);
  return new Trend(true,"已有两周有效记录，可以先讨论阻塞与负担；完成率不等于还有精力。",next,increase,List.of(a,b),decision);
 }
 private boolean meaningful(Facts f){return f.plan().arranged()+f.todo().arranged()+f.routeCompleted()+f.plan().actuallyCompleted()+f.todo().actuallyCompleted()>0;}
 @Transactional public Trend decide(String owner,LocalDate start,String decision){lock(owner);Trend prior=trend(owner,start);if(!prior.eligible())throw new ConflictException(prior.reason());Timestamp now=Timestamp.from(Instant.now());if(count("select count(*) from reflection_coaching where owner_id=?",owner)==0)db.update("insert into reflection_coaching(owner_id,offered_at,week_start,decision) values(?,?,?,?)",owner,now,start,decision);else db.update("update reflection_coaching set offered_at=?,week_start=?,decision=? where owner_id=?",now,start,decision,owner);return trend(owner,start);}
 public Preview preview(String owner,Kind kind,LocalDate start,boolean includeNotes,String relatedMode,String relatedId){
  Facts f=facts(owner,kind,start);ObjectNode content=json.createObjectNode();content.put("intent","REVIEW_ANALYSIS");content.put("kind",kind.name());ObjectNode facts=json.valueToTree(f);facts.remove("calculatedAt");content.set("facts",facts);
  content.put("notesIncluded",includeNotes);if(includeNotes)content.set("notes",json.valueToTree(kind==Kind.DAY?notes(owner,start):daily.review(owner,start)));
  if(kind==Kind.WEEK){var t=trend(owner,start);content.put("increaseAllowed",t.increaseAllowed());content.put("automaticTrendEligible",t.eligible());content.put("trendReason",t.reason());}
  if(relatedMode!=null||relatedId!=null)content.set("selectedPlanning",json.valueToTree(related(owner,relatedMode,relatedId)));
  String encoded=encode(content);if(encoded.length()>12000)throw new IllegalArgumentException("本次复盘文字过长，请缩短已保存文字或仅发送汇总。");
  return new Preview(kind,start,hash(encoded),encoded);
 }
 public PageResponse<Map<String,String>> targets(String owner,String mode,int page){
  if(page<0||page>100000)throw new IllegalArgumentException("页码无效。");String table,column,filter;
  switch(mode){case"PLAN"->{table="plans";column="name";filter="deleted_at is null";}case"ROUTE"->{table="mainlines";column="name";filter="deleted_at is null";}case"TODO"->{table="todos";column="title";filter="deleted=false and done=false";}default->throw new IllegalArgumentException("请选择对象类型。");}
  String where=" from "+table+" where owner_id=? and "+filter;long total=count("select count(*)"+where,owner);
  var rows=db.query("select id,"+column+where+" order by created_at desc,id limit 20 offset ?",(r,n)->Map.of("id",r.getString(1),"name",r.getString(2)),owner,page*20);return new PageResponse<>(rows,page,20,total,(int)((total+19)/20));
 }
 private Map<String,Object> related(String owner,String mode,String id){
  if(mode==null||id==null)throw new IllegalArgumentException("请选择具体的本人对象，不能扫描全部规划。");
  List<Map<String,Object>> values;
  if(mode.equals("PLAN"))values=db.queryForList("select name,description,start_date,end_date,rest_days_mask from plans where id=? and owner_id=? and deleted_at is null",id,owner);
  else if(mode.equals("ROUTE"))values=db.queryForList("select name,description,execution_mode from mainlines where id=? and owner_id=? and deleted_at is null",id,owner);
  else if(mode.equals("TODO"))values=db.queryForList("select title,note,due_date from todos where id=? and owner_id=? and deleted=false",id,owner);
  else throw new IllegalArgumentException("对象类型无效。");
  if(values.isEmpty())throw new NotFoundException("所选对象不存在或不属于当前账号。");Map<String,Object> result=new LinkedHashMap<>();result.put("mode",mode);result.put("planning",values.getFirst());
  if(!mode.equals("TODO")){
   String sql=mode.equals("PLAN")?"select title,description,task_date,planned_minutes from plan_tasks where plan_id=? and deleted_at is null order by task_date,sort_order,id limit 101":"select title,description,planned_minutes from mainline_items where mainline_id=? and deleted_at is null order by sort_order,id limit 101";
   var items=db.queryForList(sql,id);if(items.size()>100)throw new IllegalArgumentException("所选对象超过100项，请取消附带规划后分析，或先在对象详情中单独讨论。");result.put("items",items);
  }
  return result;
 }
 public String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("复盘内容无法编码。");}}
 public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException();}}
}
