package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.oyts.planplatform.auth.*;
import com.oyts.planplatform.common.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.ai.enabled=true","app.ai.api-key-file=/synthetic","app.ai.worker-enabled=false"})
@AutoConfigureMockMvc
class AiCollaborationIntegrationTest {
    @Autowired AiConversationService ai;@Autowired AiCollaborationService service;@Autowired AiQuotaService quotas;@Autowired AiWorkbenchService workbench;
    @Autowired UserAccountRepository users;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired MockMvc mvc;
    @MockBean AiProvider provider;String owner,other;
    String user(){return users.saveAndFlush(new UserAccount("collaboration-"+UUID.randomUUID()+"@example.test","unused-hash","协作合成验收")).getId();}
    @BeforeEach void setup(){db.update("DELETE FROM ai_sessions");owner=user();other=user();reset(provider);}
    @AfterEach void clean(){db.update("DELETE FROM ai_sessions");}
    AiDrafts.Draft draft(AiDrafts.Mode mode){var day=LocalDate.of(2026,11,2);return new AiDrafts.Draft(1,mode,0,"合成规划",mode==AiDrafts.Mode.PLAN?day:null,mode==AiDrafts.Mode.PLAN?day.plusDays(7):null,List.of(),mode==AiDrafts.Mode.ROUTE?List.of(new AiDrafts.Stage("s1","基础")):List.of(),List.of(new AiDrafts.Item("one","合成第一步","原说明",mode==AiDrafts.Mode.ROUTE?null:day,30,mode==AiDrafts.Mode.ROUTE?"s1":null,List.of()),new AiDrafts.Item("two","合成第二步","未修改项",mode==AiDrafts.Mode.ROUTE?null:day.plusDays(1),30,mode==AiDrafts.Mode.ROUTE?"s1":null,mode==AiDrafts.Mode.ROUTE?List.of("one"):List.of())));}
    AiConversationService.Session saved(AiDrafts.Mode mode)throws Exception{var s=ai.create(owner,mode,true);return ai.saveDraft(owner,s.id(),s.version(),json.writeValueAsString(draft(mode)));}
    ObjectNode material(AiConversationService.Session s){return json.valueToTree(service.export(owner,s.id(),s.version(),"仅增加第一项说明，其他保持",true));}
    ObjectNode first(ObjectNode material){return (ObjectNode)material.path("draft").path("items").get(0);}
    String createOriginal(AiDrafts.Mode mode)throws Exception{var s=saved(mode);var result=ai.apply(owner,s.id(),s.version(),true);if(mode==AiDrafts.Mode.TODO)return db.queryForObject("SELECT id FROM todos WHERE owner_id=? AND title='合成第一步'",String.class,owner);String path=result.path("path").asText();return mode==AiDrafts.Mode.PLAN?path.substring("/plans/".length()):path.substring("/mainlines?line=".length());}
    @ParameterizedTest @EnumSource(AiDrafts.Mode.class)
    void selectedDraftRoundTripOnlyPreviewsThenSavesAndAppliesOnce(AiDrafts.Mode mode)throws Exception{
        var s=saved(mode);var material=material(s);first(material).put("note","外部商量后的具体练习");String source=material.toString();
        assertFalse(source.contains(owner));assertFalse(source.contains(s.id()));assertFalse(source.contains("example.test"));assertFalse(source.contains("sourceSessionId"));assertFalse(source.contains("accessToken"));
        var before=ai.read(owner,s.id());var preview=service.preview(owner,s.id(),s.version(),source);assertEquals("BOUND",preview.binding());assertEquals(before,ai.read(owner,s.id()));assertNull(before.applied());
        assertThrows(IllegalArgumentException.class,()->service.accept(owner,s.id(),s.version(),source,preview.fingerprint(),false));
        var accepted=service.accept(owner,s.id(),s.version(),source,preview.fingerprint(),true);assertNull(accepted.applied());assertEquals("外部商量后的具体练习",accepted.draft().items().getFirst().note());assertEquals(s.draft().items().getLast(),accepted.draft().items().getLast());
        assertEquals(accepted.version(),service.accept(owner,s.id(),s.version(),source,preview.fingerprint(),true).version());
        var result=ai.apply(owner,s.id(),accepted.version(),true);assertEquals(result,ai.apply(owner,s.id(),accepted.version(),true));verifyNoInteractions(provider);
    }
    @Test void versionConflictPreservesMaterialAndForeignBindingNeverGrantsAdjustmentPermission()throws Exception{
        String original=createOriginal(AiDrafts.Mode.TODO);var s=ai.createAdjustment(owner,AiDrafts.Mode.TODO,original,true);var m=material(s);first(m).put("note","尚待确认");String source=m.toString();var p=service.preview(owner,s.id(),s.version(),source);
        var newer=ai.saveRequirements(owner,s.id(),s.version(),"新的需求");assertThrows(ConflictException.class,()->service.accept(owner,s.id(),s.version(),source,p.fingerprint(),true));
        var detached=service.preview(owner,s.id(),newer.version(),source);assertEquals("REFERENCE_ONLY",detached.binding());assertFalse(detached.canSave());assertThrows(ConflictException.class,()->service.accept(owner,s.id(),newer.version(),source,detached.fingerprint(),true));
        var foreign=ai.create(other,AiDrafts.Mode.TODO,true);var reference=service.preview(other,foreign.id(),0,source);assertEquals("REFERENCE_ONLY",reference.binding());assertTrue(reference.canSave());assertThrows(NotFoundException.class,()->service.preview(other,s.id(),newer.version(),source));
    }
    @Test void currentBusinessChangesRejectBoundPreviewAndCannotDeleteProtectedHistory()throws Exception{
        String id=createOriginal(AiDrafts.Mode.TODO);db.update("UPDATE todos SET done=true,version=version+1 WHERE id=?",id);var s=ai.createAdjustment(owner,AiDrafts.Mode.TODO,id,true);var m=material(s);assertEquals(1,m.path("protectedItemIds").size());assertFalse(m.toString().contains(id));first(m).put("note","不允许改写已完成内容");assertThrows(IllegalArgumentException.class,()->service.preview(owner,s.id(),s.version(),m.toString()));
        var valid=material(s);db.update("UPDATE todos SET note='另一设备更新',version=version+1 WHERE id=?",id);assertThrows(ConflictException.class,()->service.preview(owner,s.id(),s.version(),valid.toString()));assertTrue(db.queryForObject("SELECT done FROM todos WHERE id=?",Boolean.class,id));assertEquals("另一设备更新",db.queryForObject("SELECT note FROM todos WHERE id=?",String.class,id));
    }
    @Test void removingOldItemsOrIntroducingCyclesRollsBackBeforeSaving()throws Exception{
        var s=ai.createAdjustment(owner,AiDrafts.Mode.ROUTE,createOriginal(AiDrafts.Mode.ROUTE),true);var m=material(s);((ArrayNode)m.path("draft").path("items")).remove(0);assertThrows(RuntimeException.class,()->service.preview(owner,s.id(),s.version(),m.toString()));
        var cycle=material(s);first(cycle).putArray("prerequisites").add(s.draft().items().getLast().localId());assertThrows(AiFailure.class,()->service.preview(owner,s.id(),s.version(),cycle.toString()));assertEquals(s.draft(),ai.read(owner,s.id()).draft());
    }
    @Test void expiredAndUnrecognizedReferenceCanOnlyBeUsedAsNewDraftReference()throws Exception{
        var s=saved(AiDrafts.Mode.TODO);var m=material(s);db.update("UPDATE ai_collaborations SET expires_at=? WHERE local_ref=?",Timestamp.from(Instant.now().minusSeconds(1)),m.path("reference").asText());assertEquals("REFERENCE_ONLY",service.preview(owner,s.id(),s.version(),m.toString()).binding());
        m.put("reference",UUID.randomUUID().toString());assertEquals("REFERENCE_ONLY",service.preview(owner,s.id(),s.version(),m.toString()).binding());
    }
    @Test void wrongWrapperDamagedUnknownVersionDuplicateAndUnsupportedSourceStayReadOnly()throws Exception{
        var s=saved(AiDrafts.Mode.TODO);for(String source:List.of("请帮我规划", "{}", "{\"format\":\"plan-cabin-ai-draft\",\"content\":{}}", "{\"format\":\"personal-backup\"}","{\"format\":\"x\",\"format\":\"y\"}"))assertThrows(IllegalArgumentException.class,()->service.preview(owner,s.id(),s.version(),source));
        var m=material(s);m.put("schemaVersion",2);assertThrows(IllegalArgumentException.class,()->service.preview(owner,s.id(),s.version(),m.toString()));m.put("schemaVersion",1);m.remove("draft");assertThrows(IllegalArgumentException.class,()->service.preview(owner,s.id(),s.version(),m.toString()));assertEquals(s.draft(),ai.read(owner,s.id()).draft());verifyNoInteractions(provider);
    }
    @Test void tamperedPreviewOrTextCannotBeAcceptedAndExportRequiresExplicitConsent()throws Exception{
        var s=saved(AiDrafts.Mode.PLAN);assertThrows(IllegalArgumentException.class,()->service.export(owner,s.id(),s.version(),"改变说明",false));var m=material(s);var p=service.preview(owner,s.id(),s.version(),m.toString());first(m).put("note","预检后偷偷改的内容");assertThrows(ConflictException.class,()->service.accept(owner,s.id(),s.version(),m.toString(),p.fingerprint(),true));assertEquals(s.draft(),ai.read(owner,s.id()).draft());
    }
    @Test void sourceAndPrivateWorkspaceCollaborationEditsSurviveSaveWithoutAuthorityFlags()throws Exception{
        var s=saved(AiDrafts.Mode.TODO);var c=json.createObjectNode().put("schemaVersion",1).put("mode","TODO").put("message","").put("importJson","").put("sourceSessionId",s.id()).put("sourceVersion",s.version());c.set("draft",json.valueToTree(s.draft()));c.putObject("collaboration").put("instructions","外部修改要求").put("source","{未完成JSON");
        var saved=workbench.save(owner,UUID.randomUUID().toString(),"协作编辑",-1,c.toString());assertEquals("{未完成JSON",workbench.read(owner,saved.id()).content().path("collaboration").path("source").asText());((ObjectNode)c.path("collaboration")).put("confirmed",true);assertThrows(IllegalArgumentException.class,()->workbench.save(owner,saved.id(),"拒绝执行标记",saved.version(),c.toString()));
    }
    @Test void chineseDraftAndCollaborationFitCharacterLimitsWithoutOpeningChatBodyLimit()throws Exception{
        var s=ai.create(owner,AiDrafts.Mode.ROUTE,true);var d=(ObjectNode)json.valueToTree(draft(AiDrafts.Mode.ROUTE));var items=json.createArrayNode();
        for(int i=0;i<50;i++){var item=(ObjectNode)d.path("items").get(0).deepCopy();item.put("localId","large"+i).put("note","练".repeat(1800));items.add(item);}d.set("items",items);
        String body=json.writeValueAsString(Map.of("version",s.version(),"draftJson",d.toString()));assertTrue(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>256000);
        mvc.perform(put("/api/ai/sessions/"+s.id()+"/draft").with(jwt().jwt(j->j.subject(owner))).contentType("application/json").content(body)).andExpect(status().isOk());
        var saved=ai.read(owner,s.id());var m=material(saved);String request=json.writeValueAsString(Map.of("version",saved.version(),"source",m.toString()));
        mvc.perform(post("/api/ai/sessions/"+s.id()+"/collaboration/preview").with(jwt().jwt(j->j.subject(owner))).contentType("application/json").content(request)).andExpect(status().isOk());
        mvc.perform(post("/api/ai/sessions/"+s.id()+"/jobs").with(jwt().jwt(j->j.subject(owner))).contentType("application/json").content(" ".repeat(256001))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/ai/sessions/"+s.id()+"/collaboration/preview").with(jwt().jwt(j->j.subject(owner))).contentType("application/json").content(" ".repeat(1048577))).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(provider);
    }
    @Test void privateHttpRepliesAndUnauthorizedCrossOwnerAccessAreGuarded()throws Exception{
        var s=saved(AiDrafts.Mode.TODO);String endpoint="/api/ai/sessions/"+s.id()+"/collaboration/export";String body=json.writeValueAsString(Map.of("version",s.version(),"instructions","只改说明","consent",true));
        mvc.perform(post(endpoint).with(jwt().jwt(j->j.subject(owner))).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("private"),org.hamcrest.Matchers.containsString("no-store")))).andExpect(jsonPath("$.format").value("plan-cabin-ai-collaboration"));
        mvc.perform(post(endpoint).with(jwt().jwt(j->j.subject(other))).contentType("application/json").content(body)).andExpect(status().isNotFound());
        mvc.perform(post(endpoint).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
    }
}
