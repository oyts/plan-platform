package com.oyts.planplatform.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.oyts.planplatform.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** First-party, opt-in, unlinked daily estimates. No IP/header/account stored. */
@Service
public class WebMetricsService {
    static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate db;private final boolean enabled;private final String origin;
    private final byte[] ticketKey=new byte[32];private long minute=-1;private int tickets,events;
    public WebMetricsService(JdbcTemplate db,@Value("${app.metrics.enabled:false}") boolean enabled,@Value("${app.public-url:http://localhost:43128}") String origin){this.db=db;this.enabled=enabled;this.origin=origin;new SecureRandom().nextBytes(ticketKey);}
    public record Config(boolean enabled,String page,int dedupRetentionHours,int aggregateRetentionDays,String note){}
    public record Ticket(String ticket,Instant expiresAt){}
    public record Day(String date,long pv,long dailyVisitors,long withoutVisitorEstimate){}
    public record Report(boolean enabled,String timezone,String from,String to,Instant observedAt,List<Day> days,long pv,long dailyVisitorSum,long withoutVisitorEstimate,String visitorMeaning){}
    public Config config(){return new Config(enabled,"WELCOME",48,90,"可选分析默认关闭；不参与不影响使用。PV仅含参与者的有效浏览，UV为按日去重估算，非精确人数。");}
    private void boundary(HttpServletRequest r){
        if(!enabled)throw new ConflictException("官网可选分析未启用。");
        if(!origin.equals(r.getHeader("Origin"))||r.getHeader("Authorization")!=null||r.getHeader("Cookie")!=null||r.getQueryString()!=null
            ||r.getContentLengthLong()>1024||!Set.of("same-origin","same-site","none").contains(Optional.ofNullable(r.getHeader("Sec-Fetch-Site")).orElse("none")))throw new ForbiddenException("分析请求必须来自本站无凭据通道。");
    }
    private static void fields(JsonNode body,Set<String> names){if(body==null||!body.isObject()||body.size()!=names.size())throw new IllegalArgumentException("分析字段无效。");body.fieldNames().forEachRemaining(k->{if(!names.contains(k))throw new IllegalArgumentException("分析字段无效。");});}
    private synchronized void rate(boolean ticket){long now=Instant.now().getEpochSecond()/60;if(now!=minute){minute=now;tickets=events=0;}if(ticket?++tickets>240:++events>240)throw new TooManyRequestsException("分析请求过于频繁。");}
    public Ticket ticket(HttpServletRequest request,JsonNode body){boundary(request);fields(body,Set.of("page"));if(!body.path("page").isTextual()||!body.path("page").asText().equals("WELCOME"))throw new IllegalArgumentException("仅允许官网介绍页。");rate(true);Instant expiry=Instant.now().plusSeconds(300);String payload=expiry.getEpochSecond()+"."+UUID.randomUUID();return new Ticket(payload+"."+mac(payload),expiry);}
    @Transactional public void event(HttpServletRequest request,JsonNode body){
        boundary(request);fields(body,Set.of("page","ticket","visitorId"));rate(false);
        if(!body.path("page").isTextual()||!body.path("page").asText().equals("WELCOME")||!body.path("ticket").isTextual())throw new IllegalArgumentException("分析事件无效。");
        String ticket=body.path("ticket").asText();if(!ticket.matches("[0-9]{10}\\.[a-f0-9-]{36}\\.[a-f0-9]{64}"))throw new IllegalArgumentException("分析票据无效。");
        int end=ticket.lastIndexOf('.');long expiry=Long.parseLong(ticket.substring(0,ticket.indexOf('.')));long now=Instant.now().getEpochSecond();
        if(expiry<now||expiry>now+300||!MessageDigest.isEqual(mac(ticket.substring(0,end)).getBytes(StandardCharsets.US_ASCII),ticket.substring(end+1).getBytes(StandardCharsets.US_ASCII)))throw new ForbiddenException("分析票据已过期或无效。");
        JsonNode visitor=body.get("visitorId");if(!visitor.isNull()&&(!visitor.isTextual()||!visitor.asText().matches("[a-f0-9-]{36}")))throw new IllegalArgumentException("访客标识无效。");
        LocalDate day=LocalDate.now(ZONE);
        // Singleton day row serializes counts/insert-if-absent on both H2/MySQL.
        db.queryForList("select id from operations_policy where id=1 for update");
        if(db.queryForObject("select count(*) from web_metric_days where metric_day=?",Long.class,day)==0)db.update("insert into web_metric_days(metric_day,salt) values (?,?)",day,hash(UUID.randomUUID().toString()));
        var row=db.queryForMap("select salt,pv,uv from web_metric_days where metric_day=? for update",day);
        if(db.queryForObject("select count(*) from web_metric_events where ticket_hash=?",Long.class,hash(ticket))>0)return;
        if(((Number)row.get("pv")).longValue()>=20000)throw new TooManyRequestsException("今日分析容量已达上限，业务不受影响。");
        db.update("insert into web_metric_events(ticket_hash,created_at) values (?,?)",hash(ticket),LocalDateTime.now(ZoneOffset.UTC));
        int added=0,unknown=visitor.isNull()?1:0;
        if(!visitor.isNull()){
            String digest=hash(row.get("salt")+":"+visitor.asText());
            if(db.queryForObject("select count(*) from web_metric_visitors where metric_day=? and visitor_hash=?",Long.class,day,digest)==0){
                if(((Number)row.get("uv")).longValue()>=10000)unknown=1;
                else{db.update("insert into web_metric_visitors(metric_day,visitor_hash,created_at) values (?,?,?)",day,digest,LocalDateTime.now(ZoneOffset.UTC));added=1;}
            }
        }
        db.update("update web_metric_days set pv=pv+1,uv=uv+?,uv_unavailable=uv_unavailable+? where metric_day=?",added,unknown,day);
    }
    @Transactional(readOnly=true) public Report report(LocalDate from,LocalDate to){
        range(from,to);List<Day> days=db.query("select metric_day,pv,uv,uv_unavailable from web_metric_days where metric_day>=? and metric_day<=? order by metric_day",(r,n)->new Day(r.getDate(1).toLocalDate().toString(),r.getLong(2),r.getLong(3),r.getLong(4)),from,to);
        return new Report(enabled,"Asia/Shanghai",from.toString(),to.toString(),Instant.now(),days,days.stream().mapToLong(Day::pv).sum(),days.stream().mapToLong(Day::dailyVisitors).sum(),days.stream().mapToLong(Day::withoutVisitorEstimate).sum(),"跨日仅为日去重访客合计，不是跨日唯一人数；未采集日期不补零。换设备／清缓存可重复，同设备多人可合并。");
    }
    public static void range(LocalDate from,LocalDate to){LocalDate today=LocalDate.now(ZONE);if(from==null||to==null||from.isBefore(today.minusDays(89))||to.isAfter(today)||from.isAfter(to))throw new IllegalArgumentException("请选择北京时间最近90天内的有效日期。");}
    @Scheduled(fixedDelay=60000,initialDelay=120000) @Transactional public void retain(){
        // Indexed expiration only on this module's tables; no user/business deletion.
        var cutoff=LocalDateTime.now(ZoneOffset.UTC).minusHours(48);
        for(String table:List.of("web_metric_events","web_metric_visitors"))db.update("delete from "+table+" where created_at<?",cutoff);
        db.update("delete from web_metric_days where metric_day<?",LocalDate.now(ZONE).minusDays(89));
        // Salts need not survive the daily dedup window; old hashes cannot be linked later.
        db.update("update web_metric_days set salt='' where metric_day<? and salt<>''",LocalDate.now(ZONE).minusDays(2));
    }
    private String mac(String text){try{var m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(ticketKey,"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal(text.getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException("分析票据不可用。");}}
    static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException();}}
}
