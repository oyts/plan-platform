package com.oyts.planplatform.metrics;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RuntimeTelemetry {
    private final ObjectMapper mapper;private final String file;private final boolean enabled,mailEnabled;
    public RuntimeTelemetry(ObjectMapper mapper,@Value("${app.telemetry.status-file:}")String file,@Value("${app.telemetry.enabled:false}")boolean enabled,@Value("${app.telemetry.mail-alerts-enabled:false}")boolean mailEnabled){this.mapper=mapper;this.file=file;this.enabled=enabled;this.mailEnabled=mailEnabled;}
    public record Minute(long minute,long requests,long errors,long authFailures,long limited,long scans,long bots,long durationMs,long slow,long htmlRequests,long apiRequests,long apiErrors,long apiDurationMs){}
    public record Sample(long minute,Double cpuPercent,long memoryUsedBytes,long memoryTotalBytes,boolean healthy){}
    public record Report(boolean enabled,boolean available,boolean mailAlertsEnabled,Instant observedAt,boolean logComplete,String note,List<Minute> minutes,List<Sample> samples,String thresholds){}
    public record Check(String code,String message,boolean ok){}
    private static final String RULES="采集每分钟；超过180秒为未知。完整窗口内：5xx至少20请求且≥10%连续2分钟，≤2%连续2分钟恢复；登录失败≥10或429≥20或已识别扫描≥20连续2分钟；访问≥100且为前5分钟中位数5倍连续2分钟；CPU≥85%连续3次，≤75%连续3次恢复；内存≥90%连续3次，≤80%恢复；健康连续2次失败。不是入侵定论，不封IP。";
    private Report missing(String note){return new Report(enabled,false,mailEnabled,null,false,note,List.of(),List.of(),RULES);}
    public Report report(){
        if(!enabled||file.isBlank())return missing("轻量运行采集未启用；没有数据不代表正常。");
        try{
            Path path=Path.of(file);if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>65536)return missing("运行采集文件不可用。");
            JsonNode root=mapper.readTree(Files.readString(path));Instant observed=Instant.parse(root.path("observedAt").asText());
            if(root.path("schemaVersion").asInt()!=1||observed.isBefore(Instant.now().minusSeconds(180))||observed.isAfter(Instant.now().plusSeconds(30)))return missing("运行采集过期或格式无效，请检查采集器；按未知处理。");
            if(!root.path("minutes").isArray()||root.path("minutes").size()>61||!root.path("samples").isArray()||root.path("samples").size()>61)throw new IllegalArgumentException();
            List<Minute> minutes=new ArrayList<>();for(var r:root.path("minutes")){long t=r.path("minute").asLong(-1);validTime(t,observed);for(String f:List.of("requests","errors","authFailures","limited","scans","bots","durationMs","slow","htmlRequests","apiRequests","apiErrors","apiDurationMs"))if(!r.path(f).isIntegralNumber()||r.path(f).asLong()<0||r.path(f).asLong()>1000000000L)throw new IllegalArgumentException();minutes.add(new Minute(t,r.path("requests").asLong(),r.path("errors").asLong(),r.path("authFailures").asLong(),r.path("limited").asLong(),r.path("scans").asLong(),r.path("bots").asLong(),r.path("durationMs").asLong(),r.path("slow").asLong(),r.path("htmlRequests").asLong(),r.path("apiRequests").asLong(),r.path("apiErrors").asLong(),r.path("apiDurationMs").asLong()));}
            List<Sample> samples=new ArrayList<>();for(var r:root.path("samples")){long t=r.path("minute").asLong(-1);validTime(t,observed);Double cpu=r.path("cpuPercent").isNull()?null:r.path("cpuPercent").isNumber()?r.path("cpuPercent").asDouble():-1;long used=r.path("memoryUsedBytes").asLong(-1),total=r.path("memoryTotalBytes").asLong(-1);if(cpu!=null&&(!Double.isFinite(cpu)||cpu<0||cpu>100)||used<0||total<=0||used>total||!r.path("healthy").isBoolean())throw new IllegalArgumentException();samples.add(new Sample(t,cpu,used,total,r.path("healthy").asBoolean()));}
            minutes.sort(Comparator.comparingLong(Minute::minute));samples.sort(Comparator.comparingLong(Sample::minute));
            if(minutes.stream().map(Minute::minute).distinct().count()!=minutes.size()||samples.stream().map(Sample::minute).distinct().count()!=samples.size())throw new IllegalArgumentException();
            return new Report(true,true,mailEnabled,observed,root.path("logComplete").asBoolean(false),"主机CPU／内存采样，非容器专属用量；延迟为Nginx请求总耗时均值，不是P95。只保留最多60分钟类别聚合，无原始URL、IP或UA。",minutes,samples,RULES);
        }catch(Exception invalid){return missing("运行证据无法安全读取，按未知处理，不展示原始文件。");}
    }
    private static void validTime(long t,Instant now){long current=now.getEpochSecond()/60;if(t<current-60||t>current)throw new IllegalArgumentException();}
    public List<Check> checks(Set<String> active){
        var r=report();if(!enabled)return active.stream().filter(com.oyts.planplatform.operations.OperationsMailDelivery.METRIC_CODES::contains).map(code->new Check(code,"采集已停用，保留原事件；停用不代表故障恢复。",false)).toList();if(!r.available()||!r.logComplete()){var unknown=new ArrayList<Check>();unknown.add(new Check("RUNTIME_UNAVAILABLE","近3分钟运行采集缺失、过期或读取不完整，不能判为正常。",false));for(String code:active)if(com.oyts.planplatform.operations.OperationsMailDelivery.METRIC_CODES.contains(code)&&!code.equals("RUNTIME_UNAVAILABLE"))unknown.add(new Check(code,"采集未知，保留原事件，不冒充已恢复。",false));return unknown;}
        List<Check> out=new ArrayList<>();out.add(new Check("RUNTIME_UNAVAILABLE","运行元数据在3分钟新鲜度内且日志完整。",true));
        var samples=r.samples();if(samples.size()>=3){var last=samples.subList(samples.size()-3,samples.size());boolean continuous=last.getLast().minute()-last.getFirst().minute()==2&&last.getLast().minute()>=Instant.now().getEpochSecond()/60-1;
            if(continuous){boolean cpuKnown=last.stream().allMatch(s->s.cpuPercent()!=null);boolean cpuBad=active.contains("CPU_PRESSURE")?!last.stream().allMatch(s->s.cpuPercent()!=null&&s.cpuPercent()<=75):last.stream().allMatch(s->s.cpuPercent()!=null&&s.cpuPercent()>=85);if(cpuKnown)out.add(new Check("CPU_PRESSURE","3次主机CPU采样，触发85%／恢复75%；请核对资源。",!cpuBad));
                boolean memBad=active.contains("MEMORY_PRESSURE")?!last.stream().allMatch(s->s.memoryUsedBytes()*100.0/s.memoryTotalBytes()<=80):last.stream().allMatch(s->s.memoryUsedBytes()*100.0/s.memoryTotalBytes()>=90);out.add(new Check("MEMORY_PRESSURE","3次主机内存采样，触发90%／恢复80%；不是进程泄漏结论。",!memBad));
                boolean serviceBad=active.contains("SERVICE_UNAVAILABLE")?!(last.get(1).healthy()&&last.get(2).healthy()):!last.get(1).healthy()&&!last.get(2).healthy();out.add(new Check("SERVICE_UNAVAILABLE","最近2次本机就绪探测；失败不等于外网不可达。恢复需连续2次通过。",!serviceBad));}}
        var minutes=r.minutes().stream().filter(m->m.minute()<Instant.now().getEpochSecond()/60).toList();
        if(minutes.size()>=2){var a=minutes.get(minutes.size()-2);var b=minutes.getLast();if(b.minute()-a.minute()==1&&b.minute()>=Instant.now().getEpochSecond()/60-2){
            boolean five=active.contains("HTTP_5XX")?!(a.errors()*100.0/Math.max(1,a.requests())<=2&&b.errors()*100.0/Math.max(1,b.requests())<=2):(a.requests()>=20&&b.requests()>=20&&a.errors()*100.0/a.requests()>=10&&b.errors()*100.0/b.requests()>=10);
            String summary="窗口2分钟，请求"+(a.requests()+b.requests())+"，5xx "+(a.errors()+b.errors())+"，登录失败"+(a.authFailures()+b.authFailures())+"，限流"+(a.limited()+b.limited())+"，已识别扫描"+(a.scans()+b.scans())+"。";
            out.add(new Check("HTTP_5XX",summary,!five));out.add(new Check("AUTH_FAILURES",summary,!(a.authFailures()>=10&&b.authFailures()>=10)));out.add(new Check("RATE_SPIKE",summary,!(a.limited()>=20&&b.limited()>=20)));out.add(new Check("SCAN_SPIKE",summary,!(a.scans()>=20&&b.scans()>=20)));
            if(minutes.size()>=7){var previous=minutes.subList(minutes.size()-7,minutes.size()-2);boolean contiguous=previous.getLast().minute()-previous.getFirst().minute()==4&&a.minute()-previous.getLast().minute()==1;long median=previous.stream().mapToLong(Minute::requests).sorted().skip(2).findFirst().orElse(0);if(contiguous)out.add(new Check("TRAFFIC_SPIKE",summary+"相对前5分钟中位数的访问峰值，需人工核对。",!(a.requests()>=Math.max(100,median*5)&&b.requests()>=Math.max(100,median*5))));}
        }}for(String code:active)if(com.oyts.planplatform.operations.OperationsMailDelivery.METRIC_CODES.contains(code)&&out.stream().noneMatch(c->c.code().equals(code)))out.add(new Check(code,"有效连续窗口不足，保留原事件，尚不能确认恢复。",false));return out;
    }
}
