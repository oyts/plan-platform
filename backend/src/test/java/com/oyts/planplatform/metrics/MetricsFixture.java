package com.oyts.planplatform.metrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.ai.AdminCenterFixture;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
/** Isolated H2, fake AI and no SMTP. Never production records or raw logs. */
public class MetricsFixture {
    public static void main(String[] args)throws Exception{
        // Test-only parent lifetime: Windows may terminate the Node wrapper
        // without a signal. Pipe EOF still closes this isolated JVM safely.
        Thread parent=new Thread(()->{try{while(System.in.read()!=-1){}System.exit(0);}catch(java.io.IOException ended){System.exit(0);}},"synthetic-parent-lifetime");parent.setDaemon(true);parent.start();
        if(args.length!=1)throw new IllegalArgumentException();
        Path out=Path.of(args[0]).toRealPath(),allowed=Path.of("acceptance-results/admin-center").toRealPath();if(!out.startsWith(allowed)||out.equals(allowed))throw new IllegalArgumentException();
        snapshot(out);var sampler=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"synthetic-metrics-samples");t.setDaemon(true);return t;});sampler.scheduleAtFixedRate(()->{try{snapshot(out);}catch(Exception ignored){}},60,60,java.util.concurrent.TimeUnit.SECONDS);
        System.setProperty("app.metrics.enabled","true");System.setProperty("app.public-url","http://127.0.0.1:43130");System.setProperty("app.telemetry.enabled","true");System.setProperty("app.telemetry.status-file",out.resolve("runtime.json").toString());System.setProperty("app.telemetry.mail-alerts-enabled","false");
        AdminCenterFixture.main(args);
    }
    private static void snapshot(Path out)throws Exception{
        long now=Instant.now().getEpochSecond()/60;var rows=new ArrayList<Map<String,Object>>();var samples=new ArrayList<Map<String,Object>>();
        for(long m=now-6;m<=now;m++){var row=new LinkedHashMap<String,Object>();for(String field:List.of("requests","errors","authFailures","limited","scans","bots","durationMs","slow","htmlRequests","apiRequests","apiErrors","apiDurationMs"))row.put(field,0);row.put("minute",m);row.put("requests",20);row.put("htmlRequests",5);row.put("apiRequests",15);row.put("durationMs",200);row.put("apiDurationMs",150);rows.add(row);samples.add(Map.of("minute",m,"cpuPercent",25,"memoryUsedBytes",512000000,"memoryTotalBytes",2000000000,"healthy",true));}
        Path file=out.resolve("runtime.json");Files.writeString(file,new ObjectMapper().writeValueAsString(Map.of("schemaVersion",1,"observedAt",Instant.now().toString(),"logComplete",true,"minutes",rows,"samples",samples)));
    }
}
