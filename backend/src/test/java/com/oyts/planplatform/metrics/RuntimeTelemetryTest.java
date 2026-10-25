package com.oyts.planplatform.metrics;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeTelemetryTest {
 @TempDir Path folder;ObjectMapper json=new ObjectMapper();
 Path file(){return folder.resolve("runtime.json");}
 Map<String,Object> sample(long minute,double cpu){return Map.of("minute",minute,"cpuPercent",cpu,"memoryUsedBytes",50,"memoryTotalBytes",100,"healthy",true);}
 Map<String,Object> minute(long minute,int errors){var row=new HashMap<String,Object>();for(String key:List.of("requests","errors","authFailures","limited","scans","bots","durationMs","slow","htmlRequests","apiRequests","apiErrors","apiDurationMs"))row.put(key,0);row.put("minute",minute);row.put("requests",20);row.put("errors",errors);return row;}
 RuntimeTelemetry fixture(double cpu,int errors)throws Exception{long now=Instant.now().getEpochSecond()/60;Files.writeString(file(),json.writeValueAsString(Map.of("schemaVersion",1,"observedAt",Instant.now().toString(),"logComplete",true,"samples",List.of(sample(now-2,cpu),sample(now-1,cpu),sample(now,cpu)),"minutes",List.of(minute(now-2,errors),minute(now-1,errors)))));return new RuntimeTelemetry(json,file().toString(),true,false);}
 @Test void disabledAndMissingAreUnknownNotHealthy(){assertFalse(new RuntimeTelemetry(json,"",false,false).report().available());assertFalse(new RuntimeTelemetry(json,file().toString(),true,false).report().available());}
 @Test void resourcesRequireSustainedSamplesAndHaveHysteresis()throws Exception{var t=fixture(90,0);assertTrue(t.checks(Set.of()).stream().anyMatch(c->c.code().equals("CPU_PRESSURE")&&!c.ok()));t=fixture(80,0);assertTrue(t.checks(Set.of()).stream().anyMatch(c->c.code().equals("CPU_PRESSURE")&&c.ok()));assertTrue(t.checks(Set.of("CPU_PRESSURE")).stream().anyMatch(c->c.code().equals("CPU_PRESSURE")&&!c.ok()));t=fixture(70,0);assertTrue(t.checks(Set.of("CPU_PRESSURE")).stream().anyMatch(c->c.code().equals("CPU_PRESSURE")&&c.ok()));}
 @Test void serverErrorsUseMinimumDenominatorAndRecoveryWindow()throws Exception{var t=fixture(20,3);assertTrue(t.checks(Set.of()).stream().anyMatch(c->c.code().equals("HTTP_5XX")&&!c.ok()));assertFalse(fixture(20,0).checks(Set.of("HTTP_5XX")).stream().anyMatch(c->c.code().equals("HTTP_5XX")&&!c.ok()));}
 @Test void gapsDoNotResolveActiveIncidents()throws Exception{var t=fixture(20,0);var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(Files.readString(file()));body.put("logComplete",false);Files.writeString(file(),body.toString());assertTrue(t.checks(Set.of("CPU_PRESSURE")).stream().anyMatch(c->c.code().equals("CPU_PRESSURE")&&!c.ok()));}
 @Test void staleFutureOversizedAndInjectedContentAreNeverEchoed()throws Exception{var t=fixture(20,0);var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(Files.readString(file()));body.put("observedAt",Instant.now().minusSeconds(181).toString());body.put("private","MUST_NOT_LEAK");Files.writeString(file(),body.toString());assertFalse(t.report().available());assertFalse(t.report().toString().contains("MUST_NOT_LEAK"));Files.writeString(file(),"x".repeat(65537));assertFalse(t.report().available());}
 @Test void duplicateOrNegativeSamplesAreRejected()throws Exception{var t=fixture(20,0);var root=json.readTree(Files.readString(file()));((com.fasterxml.jackson.databind.node.ObjectNode)root.path("samples").get(0)).put("cpuPercent",-1);Files.writeString(file(),root.toString());assertFalse(t.report().available());}
 @Test void readinessRecoveryNeedsTwoSuccesses()throws Exception{var t=fixture(20,0);var root=json.readTree(Files.readString(file()));((com.fasterxml.jackson.databind.node.ObjectNode)root.path("samples").get(1)).put("healthy",false);Files.writeString(file(),root.toString());assertTrue(t.checks(Set.of("SERVICE_UNAVAILABLE")).stream().anyMatch(c->c.code().equals("SERVICE_UNAVAILABLE")&&!c.ok()));fixture(20,0);assertTrue(t.checks(Set.of("SERVICE_UNAVAILABLE")).stream().anyMatch(c->c.code().equals("SERVICE_UNAVAILABLE")&&c.ok()));}
}
