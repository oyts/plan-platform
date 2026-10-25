package com.oyts.planplatform.metrics;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrowthMetrics {
    private final JdbcTemplate db;GrowthMetrics(JdbcTemplate db){this.db=db;}
    public record Day(String date,long registered){}
    public record Report(String from,String to,String timezone,Instant observedAt,long totalRegistered,long disabled,long registeredInRange,List<Day> days,String meaning){}
    @Transactional(readOnly=true) public Report report(LocalDate from,LocalDate to){
        WebMetricsService.range(from,to);
        String expression=db.execute((java.sql.Connection c)->c.getMetaData().getDatabaseProductName()).equalsIgnoreCase("MySQL")?"DATE(DATE_ADD(created_at, INTERVAL 8 HOUR))":"CAST(DATEADD('HOUR',8,created_at) AS DATE)";
        var start=LocalDateTime.ofInstant(from.atStartOfDay(WebMetricsService.ZONE).toInstant(),ZoneOffset.UTC);var end=LocalDateTime.ofInstant(to.plusDays(1).atStartOfDay(WebMetricsService.ZONE).toInstant(),ZoneOffset.UTC);
        var actual=db.query("select "+expression+" as metric_date,count(*) from users where created_at>=? and created_at<? group by "+expression+" order by metric_date",(r,n)->new Day(r.getString(1),r.getLong(2)),start,end);
        Map<String,Long> counts=new HashMap<>();actual.forEach(d->counts.put(d.date(),d.registered()));List<Day> days=new ArrayList<>();for(var day=from;!day.isAfter(to);day=day.plusDays(1))days.add(new Day(day.toString(),counts.getOrDefault(day.toString(),0L)));
        return new Report(from.toString(),to.toString(),"Asia/Shanghai",Instant.now(),db.queryForObject("select count(*) from users",Long.class),db.queryForObject("select count(*) from users where enabled=false",Long.class),days.stream().mapToLong(Day::registered).sum(),days,"按账号created_at统计实际创建；停用另列。不是邀请发送数、登录次数或活跃人数。历史只使用已有可靠创建时间。");
    }
}
