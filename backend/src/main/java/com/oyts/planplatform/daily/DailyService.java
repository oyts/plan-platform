package com.oyts.planplatform.daily;

import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.common.ConflictException;
import com.oyts.planplatform.common.NotFoundException;
import com.oyts.planplatform.plan.PlanDtos.PageResponse;
import jakarta.persistence.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class DailyService {
    private final EntityManager em;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String PLAN = " from plan_tasks t join plans p on p.id=t.plan_id left join task_progress g on g.task_id=t.id"
        + " where p.owner_id=:owner and p.deleted_at is null and p.archived=false and t.deleted_at is null";
    private static final String PLAN_OPEN = PLAN + " and coalesce(g.status,'PENDING') not in ('COMPLETED','SKIPPED')";
    private static final String EFFECTIVE = "coalesce(g.defer_to,t.task_date)";
    private static final String TODO = " from todos t where t.owner_id=:owner and t.deleted=false";
    private static final String TODO_OPEN = TODO + " and t.done=false";
    private static final String ROUTES = " from mainlines r where r.owner_id=:owner and r.deleted_at is null and r.archived=false";
    DailyService(EntityManager em) { this.em = em; }

    public DailyDtos.Summary summary(String owner, LocalDate date, int days) {
        dateRange(date, days);
        return new DailyDtos.Summary(date, date.plusDays(days), counts(owner, date, days, PLAN_OPEN, EFFECTIVE),
            counts(owner, date, days, TODO_OPEN, "t.due_date"), count("select count(*)" + ROUTES, owner, Map.of()));
    }
    private DailyDtos.Counts counts(String owner, LocalDate date, int days, String base, String field) {
        return new DailyDtos.Counts(count("select count(*)" + base + " and " + field + "=:date", owner, Map.of("date",date)),
            count("select count(*)" + base + " and " + field + "<:date", owner, Map.of("date",date)),
            count("select count(*)" + base + " and " + field + ">:date and " + field + "<=:through", owner, Map.of("date",date,"through",date.plusDays(days))));
    }
    public PageResponse<DailyDtos.Item> items(String owner, LocalDate date, int days, String bucket, String mode, int page, int size) {
        dateRange(date, days); pagination(page, size);
        if (!Set.of("ALL","PLAN","TODO").contains(mode)) throw new IllegalArgumentException("请选择全部、周期计划或今日待办。");
        String dateFilter = switch (bucket) {
            case "TODAY" -> "= :date";
            case "OVERDUE" -> "< :date";
            case "UPCOMING" -> "> :date and %s <= :through";
            default -> throw new IllegalArgumentException("请选择今日、逾期或即将到期。");
        };
        String plans = "select 'PLAN' as kind,t.id as id,p.id as parent_id,t.title as title,p.name as parent_name," + EFFECTIVE
            + " as due_date,coalesce(g.status,'PENDING') as state" + PLAN_OPEN + " and " + EFFECTIVE + " " + dateFilter.formatted(EFFECTIVE);
        String todos = "select 'TODO' as kind,t.id as id,t.id as parent_id,t.title as title,'' as parent_name,t.due_date as due_date,'PENDING' as state"
            + TODO_OPEN + " and t.due_date " + dateFilter.formatted("t.due_date");
        String combined = mode.equals("PLAN") ? plans : mode.equals("TODO") ? todos : plans + " union all " + todos;
        Map<String,Object> params = new HashMap<>(); params.put("date",date);
        if (bucket.equals("UPCOMING")) params.put("through",date.plusDays(days));
        long total = count("select count(*) from (" + combined + ") daily_items", owner, params);
        var query = query("select * from (" + combined + ") daily_items order by due_date,kind,id", owner, params)
            .setFirstResult(Math.multiplyExact(page,size)).setMaxResults(size);
        List<DailyDtos.Item> rows = rows(query).stream().map(r -> new DailyDtos.Item(text(r[0]),text(r[1]),text(r[2]),text(r[3]),text(r[4]),date(r[5]),text(r[6]))).toList();
        return paged(rows,page,size,total);
    }
    public PageResponse<DailyDtos.Route> routes(String owner, int page, int size) {
        pagination(page,size);
        String sql = "select r.id,r.name,count(i.id),coalesce(sum(case when i.status='COMPLETED' then 1 else 0 end),0)"
            + " from mainlines r left join mainline_items i on i.mainline_id=r.id and i.deleted_at is null"
            + " where r.owner_id=:owner and r.deleted_at is null and r.archived=false group by r.id,r.name,r.created_at order by r.created_at,r.id";
        var values = rows(query(sql,owner,Map.of()).setFirstResult(Math.multiplyExact(page,size)).setMaxResults(size)).stream()
            .map(r -> new DailyDtos.Route(text(r[0]),text(r[1]),number(r[2]),number(r[3]))).toList();
        return paged(values,page,size,count("select count(*)" + ROUTES,owner,Map.of()));
    }
    public DailyDtos.Reminders reminders(String owner) {
        var values = em.createQuery("from ReminderPreference p where p.owner.id=:owner", ReminderPreference.class).setParameter("owner",owner).getResultList();
        return values.isEmpty() ? new DailyDtos.Reminders(false,true,true,true,3,null) : values.getFirst().response();
    }
    @Transactional public DailyDtos.Reminders saveReminders(String owner, DailyDtos.ReminderRequest r) {
        if (r.enabled() && !r.includePlans() && !r.includeTodos()) throw new IllegalArgumentException("启用提醒时请至少选择一种事项。");
        UserAccount user = lockOwner(owner);
        var values = em.createQuery("from ReminderPreference p where p.owner.id=:owner", ReminderPreference.class).setParameter("owner",owner).getResultList();
        var item = values.isEmpty() ? null : values.getFirst();
        checkVersion(item == null ? null : item.getVersion(),r.version());
        boolean creating = item == null;
        if (creating) item = new ReminderPreference(user);
        item.update(r); if (creating) em.persist(item); em.flush(); return item.response();
    }
    public DailyDtos.Week week(String owner, LocalDate start) {
        monday(start);
        LocalDate end = start.plusDays(6); Instant from = start.atStartOfDay(ZONE).toInstant(), until = start.plusDays(7).atStartOfDay(ZONE).toInstant();
        var dates = Map.<String,Object>of("start",start,"end",end);
        var instants = Map.<String,Object>of("from",from,"until",until);
        String weeklyPlans = PLAN.replace(" and p.archived=false", "");
        long planned = count("select count(*)" + weeklyPlans + " and " + EFFECTIVE + " between :start and :end", owner, dates);
        long planDone = count("select count(*)" + weeklyPlans + " and g.status='COMPLETED' and g.completed_at>=:from and g.completed_at<:until",owner,instants);
        long todoPlanned = count("select count(*)" + TODO + " and t.due_date between :start and :end",owner,dates);
        long todoDone = count("select count(*)" + TODO + " and t.done=true and t.completed_at>=:from and t.completed_at<:until",owner,instants);
        long routeDone = count("select count(*) from mainline_items i join mainlines r on r.id=i.mainline_id where r.owner_id=:owner"
            + " and r.deleted_at is null and i.deleted_at is null and i.status='COMPLETED' and i.completed_at>=:from and i.completed_at<:until",owner,instants);
        // Same attribution as the existing ledger: start time for timers, entry time for manual corrections.
        long seconds = count("select coalesce(sum(e.seconds),0) from time_entries e join task_progress g on g.id=e.progress_id join plan_tasks t on t.id=g.task_id join plans p on p.id=t.plan_id"
            + " where p.owner_id=:owner and e.started_at>=:from and e.started_at<:until",owner,instants);
        return new DailyDtos.Week(start,end,Instant.now(),planned,planDone,todoPlanned,todoDone,routeDone,seconds,
            count("select count(*)" + PLAN_OPEN + " and " + EFFECTIVE + "<=:end",owner,Map.of("end",end)),
            count("select count(*)" + TODO_OPEN + " and t.due_date<=:end",owner,Map.of("end",end)),
            count("select count(*)" + TODO + " and t.done=true and t.completed_at is null",owner,Map.of()));
    }
    public DailyDtos.Review review(String owner, LocalDate start) {
        monday(start);
        var item = findReview(owner,start);
        return item == null ? new DailyDtos.Review(start,"","","",null,null) : item.response();
    }
    @Transactional public DailyDtos.Review saveReview(String owner, LocalDate start, DailyDtos.ReviewRequest r) {
        monday(start); UserAccount user = lockOwner(owner); var item = findReview(owner,start);
        checkVersion(item == null ? null : item.getVersion(),r.version());
        boolean creating = item == null;
        if (creating) item = new WeeklyReview(user,start);
        item.update(r); if (creating) em.persist(item); em.flush(); return item.response();
    }
    public PageResponse<DailyDtos.Review> history(String owner, int page, int size) {
        pagination(page,size);
        var values = em.createQuery("from WeeklyReview r where r.owner.id=:owner order by r.weekStart desc",WeeklyReview.class)
            .setParameter("owner",owner).setFirstResult(Math.multiplyExact(page,size)).setMaxResults(size).getResultList();
        long total = em.createQuery("select count(r) from WeeklyReview r where r.owner.id=:owner",Long.class).setParameter("owner",owner).getSingleResult();
        return paged(values.stream().map(WeeklyReview::response).toList(),page,size,total);
    }
    private WeeklyReview findReview(String owner, LocalDate start) {
        var values = em.createQuery("from WeeklyReview r where r.owner.id=:owner and r.weekStart=:start",WeeklyReview.class)
            .setParameter("owner",owner).setParameter("start",start).getResultList();
        return values.isEmpty() ? null : values.getFirst();
    }
    private UserAccount lockOwner(String owner) {
        var user = em.find(UserAccount.class,owner,LockModeType.PESSIMISTIC_WRITE);
        if (user == null) throw new NotFoundException("账号不存在。");
        return user;
    }
    private void checkVersion(Long actual, Long expected) {
        if (!Objects.equals(actual,expected)) throw new ConflictException("此记录已在其他设备更新。当前输入已保留，请读取最新版本后再决定如何修改。");
    }
    private void dateRange(LocalDate date, int days) {
        if (date == null || date.getYear()<1 || date.getYear()>9998 || days<1 || days>14) throw new IllegalArgumentException("日期须为 1—9998 年，提前查看天数须为 1—14 天。");
    }
    private void monday(LocalDate start) {
        dateRange(start,1);
        if (!start.equals(start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))) throw new IllegalArgumentException("请选择周一作为一周的开始日期。");
    }
    private void pagination(int page, int size) {
        if (page<0 || page>100000 || size<1 || size>50) throw new IllegalArgumentException("分页参数不正确，每页最多 50 项。");
    }
    private Query query(String sql, String owner, Map<String,Object> params) {
        Query q = em.createNativeQuery(sql).setParameter("owner",owner); params.forEach(q::setParameter); return q;
    }
    private long count(String sql, String owner, Map<String,Object> params) { return number(query(sql,owner,params).getSingleResult()); }
    @SuppressWarnings("unchecked") private List<Object[]> rows(Query q) { return q.getResultList(); }
    private long number(Object value) { return value == null ? 0 : ((Number)value).longValue(); }
    private String text(Object value) { return value == null ? "" : value.toString(); }
    private LocalDate date(Object value) { return value instanceof LocalDate d ? d : LocalDate.parse(value.toString().substring(0,10)); }
    private <T> PageResponse<T> paged(List<T> rows,int page,int size,long total) { return new PageResponse<>(rows,page,size,total,(int)Math.min(Integer.MAX_VALUE,(total+size-1)/size)); }
}
