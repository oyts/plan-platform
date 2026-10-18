package com.oyts.planplatform;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.security.registration-enabled=true","app.governance.enabled=true","app.operations.enabled=true","app.operations.scheduler-enabled=false"}) @AutoConfigureMockMvc
class GovernanceOperationsIntegrationTest {
    static final Path TEMP=temporary();static final Path FILE=TEMP.resolve("status.json");
    static Path temporary(){try{return Files.createTempDirectory("plan-governance-test-");}catch(Exception error){throw new IllegalStateException(error);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties){properties.add("app.operations.status-file",()->FILE.toString());}
    @AfterAll static void cleanup()throws Exception{Files.deleteIfExists(FILE);Files.deleteIfExists(TEMP);}
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate db;
    @BeforeEach void defaultRules(){db.update("update operations_policy set keep_newest=7,retention_days=30,backup_max_hours=26,disk_min_percent=15,version=0 where id=1");}
    record Account(String id,String email,String token,Cookie refresh){}
    private Account account(boolean admin)throws Exception{String email="governance-"+UUID.randomUUID()+"@example.test";var response=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email",email,"password","GovernanceTest123!","displayName","合成治理验收")))).andExpect(status().isCreated()).andReturn().getResponse();var row=mapper.readTree(response.getContentAsString());String id=row.path("user").path("id").asText();String token=row.path("accessToken").asText();if(admin){db.update("update users set role='ADMIN' where id=?",id);token=send(post("/api/auth/login"),null,Map.of("email",email,"password","GovernanceTest123!"),200).path("accessToken").asText();}return new Account(id,email,token,response.getCookie("plan_refresh"));}
    private JsonNode send(MockHttpServletRequestBuilder r,Account a,Object body,int code)throws Exception{if(a!=null)r.header("Authorization","Bearer "+a.token());if(body!=null)r.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));var response=mvc.perform(r).andExpect(status().is(code)).andReturn().getResponse();return response.getContentAsString().isBlank()?mapper.nullNode():mapper.readTree(response.getContentAsString());}
    private JsonNode read(Account a,String path)throws Exception{return send(get("/api"+path),a,null,200);}
    private JsonNode change(Account admin,Account user,boolean enabled,long version,int code)throws Exception{return send(put("/api/admin/governance/members/"+user.id()),admin,Map.of("enabled",enabled,"version",version,"reason","合成验收状态变更"),code);}
    @Test void disabledMemberCannotUseAccessOrRefreshAndRestoreNeverRevivesOldCredentials()throws Exception{
        var admin=account(true);var user=account(false);var todo=send(post("/api/todos"),user,Map.of("title","不能由管理员读取的私有事项","dueDate","2026-10-18"),201);
        var disabled=change(admin,user,false,0,200);assertThat(disabled.path("enabled").asBoolean()).isFalse();send(get("/api/todos/page?from=2026-10-18&to=2026-10-18"),user,null,401);send(post("/api/auth/refresh").cookie(user.refresh()),null,null,401);send(post("/api/auth/login"),null,Map.of("email",user.email(),"password","GovernanceTest123!"),401);
        change(admin,user,true,disabled.path("version").asLong(),200);send(get("/api/plans"),user,null,401);send(post("/api/auth/refresh").cookie(user.refresh()),null,null,401);
        var fresh=send(post("/api/auth/login"),null,Map.of("email",user.email(),"password","GovernanceTest123!"),200);var restored=new Account(user.id(),user.email(),fresh.path("accessToken").asText(),null);assertThat(read(restored,"/todos?from=2026-10-18&to=2026-10-18").get(0).path("id")).isEqualTo(todo.path("id"));
        send(get("/api/sync/record").param("path","/todos/"+todo.path("id").asText()),admin,null,404);assertThat(read(admin,"/admin/governance/audit").toString()).doesNotContain("不能由管理员读取的私有事项","accessToken","password");
    }
    @Test void governanceRejectsMemberPrivilegeEscalationAdministratorTargetsAndStaleWrites()throws Exception{
        var admin=account(true);var user=account(false);send(get("/api/admin/governance/members"),user,null,403);send(get("/api/admin/operations/status"),user,null,403);change(user,admin,false,0,403);change(admin,admin,false,0,409);
        var disabled=change(admin,user,false,0,200);change(admin,user,true,0,409);assertThat(db.queryForObject("select count(*) from member_governance_audit where target_id=?",Long.class,user.id())).isEqualTo(1);change(admin,user,true,disabled.path("version").asLong(),200);
        assertThat(read(admin,"/admin/governance/members?search="+user.email()).path("totalElements").asInt()).isEqualTo(1);
    }
    private Map<String,Object> healthy(){List<Map<String,Object>> backups=new ArrayList<>();for(int i=0;i<10;i++){Instant when=Instant.now().minus(Duration.ofDays(i==9?50:i));String name="plan_platform-mysql-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(when)+".sql.gz";backups.add(Map.of("name",name,"completedAt",when.toString(),"bytes",128,"sha256",String.format("%064x",i+1),"localVerified",true));}
        return new HashMap<>(Map.of("schemaVersion",1,"observedAt",Instant.now().toString(),"backups",backups,"catalogComplete",true,"oss",Map.of("sha256",String.format("%064x",1),"verifiedAt",Instant.now().toString(),"verified",true),"drill",Map.of("sha256",String.format("%064x",1),"completedAt",Instant.now().toString(),"matched",true),"disk",Map.of("freeBytes",50,"totalBytes",100),"failures",List.of()));}
    @Test void metadataIsWhitelistOnlyAndCleanupPreservesVerifiedRecoveryPointsWithoutDeletingAnything()throws Exception{
        var admin=account(true);var input=healthy();input.put("privateKey","synthetic-do-not-return");Files.writeString(FILE,mapper.writeValueAsString(input));
        var report=read(admin,"/admin/operations/status");assertThat(report.path("dataAvailable").asBoolean()).isTrue();assertThat(report.toString()).doesNotContain("synthetic-do-not-return",TEMP.toString());assertThat(report.path("mailAlertsEnabled").asBoolean()).isFalse();
        assertThat(java.util.stream.StreamSupport.stream(report.path("cleanupPreview").spliterator(),false).filter(row->row.path("candidate").asBoolean()).count()).isEqualTo(1);assertThat(Files.exists(FILE)).isTrue();
        input.put("drill",Map.of());Files.writeString(FILE,mapper.writeValueAsString(input));report=read(admin,"/admin/operations/status");for(var row:report.path("cleanupPreview"))assertThat(row.path("candidate").asBoolean()).isFalse();
        input.put("observedAt",Instant.now().minus(Duration.ofHours(25)).toString());Files.writeString(FILE,mapper.writeValueAsString(input));assertThat(read(admin,"/admin/operations/status").path("dataAvailable").asBoolean()).isFalse();
    }
    @Test void repeatedFailuresShareOneIncidentAndRecoveryClosesIt()throws Exception{
        var admin=account(true);Files.writeString(FILE,"{\"schemaVersion\":999}");send(post("/api/admin/operations/evaluate"),admin,null,200);String incident=db.queryForObject("select incident_id from operations_alerts where code='OPS_DATA_UNAVAILABLE'",String.class);send(post("/api/admin/operations/evaluate"),admin,null,200);assertThat(db.queryForObject("select incident_id from operations_alerts where code='OPS_DATA_UNAVAILABLE'",String.class)).isEqualTo(incident);
        Files.writeString(FILE,mapper.writeValueAsString(healthy()));send(post("/api/admin/operations/evaluate"),admin,null,200);assertThat(db.queryForObject("select active from operations_alerts where code='OPS_DATA_UNAVAILABLE'",Boolean.class)).isFalse();
    }
    @Test void previewPolicyUsesVersionChecksAndRecordsOnlyNumericRulesInAudit()throws Exception{
        var admin=account(true);var before=read(admin,"/admin/operations/status").path("policy");var request=Map.of("keepNewest",2,"retentionDays",7,"backupMaxHours",30,"diskMinPercent",20,"version",before.path("version").asLong());
        send(put("/api/admin/operations/policy"),admin,request,200);send(put("/api/admin/operations/policy"),admin,request,409);assertThat(read(admin,"/admin/operations/policy-audit").path("totalElements").asInt()).isPositive();
        var bad=new HashMap<>(request);bad.put("keepNewest",1);send(put("/api/admin/operations/policy"),admin,bad,400);
    }
}
