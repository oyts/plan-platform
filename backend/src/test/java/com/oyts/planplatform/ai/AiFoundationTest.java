package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AiFoundationTest {
    final ObjectMapper mapper=new ObjectMapper().registerModule(new JavaTimeModule());
    final Validator validator=Validation.buildDefaultValidatorFactory().getValidator();
    final String todo="""
        {"schemaVersion":1,"mode":"TODO","revision":0,"title":"案例","startDate":null,"endDate":null,"restDays":[],"stages":[],
        "items":[{"localId":"t1","title":"买琴弦","note":"","dueDate":"2026-10-15","plannedMinutes":15,"stageId":null,"prerequisites":[]}]}
        """;
    @Test void validatesThreeModesWithoutWritingAnyBusinessData() throws Exception {
        assertEquals(AiDrafts.Mode.TODO,AiDrafts.parseDraft(todo,mapper,validator).mode());
        String plan=todo.replace("\"TODO\"","\"PLAN\"").replace("\"startDate\":null","\"startDate\":\"2026-10-15\"").replace("\"endDate\":null","\"endDate\":\"2026-11-15\"").replace("\"restDays\":[]","\"restDays\":[7]");
        assertEquals(AiDrafts.Mode.PLAN,AiDrafts.parseDraft(plan,mapper,validator).mode());
        var route=mapper.readTree(todo); ((com.fasterxml.jackson.databind.node.ObjectNode)route).put("mode","ROUTE");
        ((com.fasterxml.jackson.databind.node.ObjectNode)route.get("items").get(0)).putNull("dueDate");
        assertEquals(AiDrafts.Mode.ROUTE,AiDrafts.parseDraft(mapper.writeValueAsString(route),mapper,validator).mode());
        for(String invalid:new String[]{todo.replace("\"schemaVersion\":1","\"schemaVersion\":2"),todo.replace("\"plannedMinutes\":15","\"plannedMinutes\":1.5"),todo.replace("\"dueDate\":\"2026-10-15\"","\"dueDate\":null"),todo.replace("\"revision\":0,",""),todo.replace("\"title\":\"案例\"","\"title\":\"案例\",\"unexpected\":true"),todo+" {}",todo.replace("\"revision\":0","\"revision\":0,\"revision\":1"),plan.replace("2026-10-15\",\"plannedMinutes","2026-01-01\",\"plannedMinutes")}) assertEquals(AiFailure.Code.INVALID_OUTPUT,assertThrows(AiFailure.class,()->AiDrafts.parseDraft(invalid,mapper,validator)).code());
    }
    @Test void rejectsCyclesDuplicateIdsAndMissingPatchVersions() {
        String route=todo.replace("\"TODO\"","\"ROUTE\"").replace("\"dueDate\":\"2026-10-15\"","\"dueDate\":null").replace("\"prerequisites\":[]","\"prerequisites\":[\"t1\"]");
        assertThrows(AiFailure.class,()->AiDrafts.parseDraft(route,mapper,validator));
        String duplicate=todo.replace("}]}","},"+todo.substring(todo.indexOf("{\"localId\""),todo.lastIndexOf("]}"))+ "]}");
        assertThrows(AiFailure.class,()->AiDrafts.parseDraft(duplicate,mapper,validator));
        var item=AiDrafts.parseDraft(todo,mapper,validator).items().getFirst();
        try {
            String patch=mapper.writeValueAsString(new AiDrafts.Patch(1,0,java.util.List.of(new AiDrafts.Change(AiDrafts.ChangeType.MODIFY,"task-id",item)),Map.of("task-id",2L)));
            assertEquals(2,AiDrafts.parsePatch(patch,mapper,validator).baseVersions().get("task-id"));
            assertThrows(AiFailure.class,()->AiDrafts.parsePatch(patch.replace("\"task-id\":2","\"other\":2"),mapper,validator));
        } catch(java.io.IOException failure) { fail(failure); }
    }
    @Test void readsBoundedFileAndNeverLeaksContent(@TempDir Path temp) throws Exception {
        var disabled=new AiProperties(false,"deepseek-flash","",8000,2048,2,4,60);
        assertEquals(AiFailure.Code.DISABLED,assertThrows(AiFailure.class,()->AiSecretReader.read(disabled)).code());
        Path fake=temp.resolve("fake-key");Files.writeString(fake,"sk-fake-testing-only-12345\n");
        var config=new AiProperties(true,"deepseek-flash",fake.toString(),8000,2048,2,4,60);
        assertEquals("sk-fake-testing-only-12345",AiSecretReader.read(config));
        Files.writeString(fake,"invalid key with spaces");
        var failure=assertThrows(AiFailure.class,()->AiSecretReader.read(config));assertFalse(failure.toString().contains("invalid key"));assertNull(failure.getCause());
        assertFalse(new AiProvider.Request("private message",2048).toString().contains("private message"));
        assertFalse(new AiProvider.Result("private reply",1,2).toString().contains("private reply"));
        assertThrows(IllegalArgumentException.class,()->new AiProperties(true,"client-chosen-model","",8000,2048,2,4,60));
    }
    @Test void isolatesTimeoutAndOpensCircuitWithoutRetries() {
        try(var guard=new AiExecutionGuard(1,1)) {
            var request=new AiProvider.Request("fixture",128);var calls=new java.util.concurrent.atomic.AtomicInteger();
            AiProvider failed=r->{calls.incrementAndGet();throw new IllegalStateException("private upstream detail");};
            for(int i=0;i<3;i++) assertEquals(AiFailure.Code.UPSTREAM_UNAVAILABLE,assertThrows(AiFailure.class,()->guard.call(failed,request,Duration.ofSeconds(1))).code());
            assertThrows(AiFailure.class,()->guard.call(failed,request,Duration.ofSeconds(1)));assertEquals(3,calls.get());
        }
        try(var guard=new AiExecutionGuard(1,0)) {
            AiProvider slow=r->{try{Thread.sleep(1000);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}return new AiProvider.Result("fixture",1,1);};
            assertEquals(AiFailure.Code.TIMEOUT,assertThrows(AiFailure.class,()->guard.call(slow,new AiProvider.Request("fixture",128),Duration.ofMillis(30))).code());
        }
    }
    @Test void boundsConcurrencyAndRejectsInvalidProviderOutput() throws Exception {
        try(var guard=new AiExecutionGuard(1,0);var executor=Executors.newSingleThreadExecutor()) {
            var started=new CountDownLatch(1);var release=new CountDownLatch(1);
            var request=new AiProvider.Request("fixture",128);
            var first=executor.submit(()->guard.call(r->{started.countDown();try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}return new AiProvider.Result("fixture",1,1);},request,Duration.ofSeconds(3)));
            assertTrue(started.await(2,TimeUnit.SECONDS));
            assertEquals(AiFailure.Code.BUSY,assertThrows(AiFailure.class,()->guard.call(r->null,request,Duration.ofSeconds(1))).code());
            release.countDown();assertEquals("fixture",first.get().text());
        }
        try(var guard=new AiExecutionGuard(1,1)) { assertEquals(AiFailure.Code.INVALID_OUTPUT,assertThrows(AiFailure.class,()->guard.call(r->new AiProvider.Result("",0,0),new AiProvider.Request("fixture",128),Duration.ofSeconds(1))).code()); }
    }
    @Test void enablingConfigWithoutKeyNeverMakesPaidCallsAvailable() {
        var config=new AiProperties(true,"deepseek-flash","",8000,2048,2,4,60);
        var sessions=org.mockito.Mockito.mock(AiConversationService.class);
        org.mockito.Mockito.when(sessions.runtime()).thenReturn(new AiRuntimePolicy.Policy(new AiRuntimePolicy.Limits(2,4,8000,2048,4096,60),0,null));
        var controller=new AiController(config,null,sessions);
        var capabilities=(Map<?,?>)controller.capabilities().getBody();
        assertEquals(false,capabilities.get("available")); assertEquals("NOT_CONFIGURED",capabilities.get("status"));
        assertEquals(503,controller.unavailable().getStatusCode().value());
        assertThrows(IllegalArgumentException.class,()->new AiProvider.Request("x".repeat(32001),128));
    }
    @Test void migrationFromPreviousSchemaPreservesExistingPlanAndRestDays() throws Exception {
        String url="jdbc:h2:mem:ai_upgrade_"+java.util.UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var config=org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration","classpath:db/vendor/h2");
        config.target("13").load().migrate();
        try(var connection=java.sql.DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
            sql.executeUpdate("INSERT INTO users(id,email,password_hash,display_name,role,created_at,updated_at) VALUES ('fixture-owner','upgrade@example.test','unused','迁移测试','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            sql.executeUpdate("INSERT INTO plans(id,owner_id,name,start_date,end_date,rest_days_mask,created_at,updated_at) VALUES ('fixture-plan','fixture-owner','保留的周期计划','2026-10-01','2026-10-31',64,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        }
        config.target("latest").load().migrate();
        try(var connection=java.sql.DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
            try(var result=sql.executeQuery("SELECT name,rest_days_mask FROM plans WHERE id='fixture-plan'")) {assertTrue(result.next());assertEquals("保留的周期计划",result.getString(1));assertEquals(64,result.getInt(2));}
            try(var result=sql.executeQuery("SELECT daily_limit FROM ai_quota_policy WHERE id=1")) {assertTrue(result.next());assertEquals(0,result.getInt(1));}
        }
    }
}
