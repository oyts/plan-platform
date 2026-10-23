package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.auth.*;
import com.oyts.planplatform.common.*;
import com.oyts.planplatform.reflection.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"app.ai.enabled=true","app.ai.api-key-file=/fake-test-key","app.ai.worker-enabled=false"})
class ReflectionAiIntegrationTest {
 @Autowired AiConversationService ai;@Autowired AiQuotaService quotas;@Autowired ReflectionService reviews;@Autowired UserAccountRepository users;@Autowired JdbcTemplate db;@Autowired AiProperties config;@Autowired ObjectMapper json;
 @MockBean AiProvider provider;String owner,other;
 @Autowired AiAttempts attempts;
 String user(){return users.saveAndFlush(new UserAccount("review-ai-"+UUID.randomUUID()+"@example.test","unused-synthetic-hash","合成AI复盘")).getId();}
 @BeforeEach void setup(){db.update("delete from ai_sessions");db.update("delete from ai_quota_audit");db.update("delete from ai_quota_requests");db.update("delete from ai_user_quota");db.update("update ai_quota_policy set daily_limit=0,version=0 where id=1");owner=user();other=user();quotas.changePolicy(owner,10,0);reset(provider);}
 @AfterEach void cleanup(){db.update("delete from ai_sessions");}
 ReflectionDtos.Analyse request(boolean consent){var p=reviews.preview(owner,ReflectionDtos.Kind.DAY,LocalDate.now(),true,null,null);return new ReflectionDtos.Analyse(p.kind(),p.start(),true,p.fingerprint(),consent,null,null);}
 void run(){var w=ai.claim();assertThat(w).isNotNull();try(var guard=new AiExecutionGuard(2,0)){var runner=new AiJobRunner(ai,provider,guard,config,attempts,false,false);try{runner.run(w);}finally{runner.close();}}}
 @Test void consentAndFreshPreviewRequiredBeforeAnyPaidAdmission(){assertThatThrownBy(()->ai.createReview(owner,request(false))).isInstanceOf(IllegalArgumentException.class);var stale=request(true);reviews.save(owner,LocalDate.now(),new ReflectionDtos.Save("新增文字","","","LOW","HEAVY",true,null));assertThatThrownBy(()->ai.createReview(owner,stale)).isInstanceOf(ConflictException.class);assertThat(quotas.usage(owner).used()).isZero();verifyNoInteractions(provider);}
 @Test void analysisCannotChooseSaveApplyOrAccessAnotherUsersSession()throws Exception{
  var s=ai.createReview(owner,request(true));assertThat(s.review()).isNotNull();assertThat(sessionsCount(owner)).isEqualTo(1);assertThat(ai.list(owner)).isEmpty();assertThat(ai.reviewList(owner)).hasSize(1);assertThat(ai.reviewList(other)).isEmpty();assertThatThrownBy(()->ai.read(other,s.id())).isInstanceOf(NotFoundException.class);assertThatThrownBy(()->ai.choose(owner,s.id(),s.version(),AiDrafts.Mode.PLAN)).isInstanceOf(ConflictException.class);assertThatThrownBy(()->ai.saveDraft(owner,s.id(),s.version(),"{}")).isInstanceOf(ConflictException.class);assertThatThrownBy(()->ai.apply(owner,s.id(),s.version(),true)).isInstanceOf(ConflictException.class);
  when(provider.generate(any())).thenReturn(new AiProvider.Result("{\"message\":\"记录了阻塞，可以先尝试小步行动。\",\"recommendedMode\":\"TODO\",\"draft\":null}",20,30));
  var job=ai.enqueue(owner,s.id(),s.version(),"review-once-001","只做日分析");assertThat(ai.enqueue(owner,s.id(),s.version(),"review-once-001","只做日分析").id()).isEqualTo(job.id());run();var result=ai.read(owner,s.id());assertThat(result.draft()).isNull();assertThat(result.applied()).isNull();assertThat(quotas.usage(owner).used()).isEqualTo(1);assertThat(db.queryForObject("select count(*) from plans where owner_id=?",Long.class,owner)).isZero();verify(provider,times(1)).generate(any());
 }
 @Test void providerGetsOnlyConsentedSnapshotAndAnalysisIntent()throws Exception{
  reviews.save(owner,LocalDate.now(),new ReflectionDtos.Save("选择的文字","","","LOW","HEAVY",true,null));var p=reviews.preview(owner,ReflectionDtos.Kind.DAY,LocalDate.now(),false,null,null);var r=new ReflectionDtos.Analyse(p.kind(),p.start(),false,p.fingerprint(),true,null,null);var s=ai.createReview(owner,r);ai.enqueue(owner,s.id(),s.version(),"review-context-001","请分析");var work=ai.claim();assertThat(work.context()).contains("REVIEW_ANALYSIS").doesNotContain("选择的文字",owner,"unused-synthetic-hash");ai.fail(work,new AiFailure(AiFailure.Code.DISABLED,false));
 }
 @Test void uncertainResultKeepsReservationAndDoesNotRetryAutomatically(){var s=ai.createReview(owner,request(true));when(provider.generate(any())).thenThrow(new AiFailure(AiFailure.Code.TIMEOUT,true));ai.enqueue(owner,s.id(),s.version(),"review-unknown-001","分析这个范围");run();assertThat(ai.read(owner,s.id()).turns().getFirst().state()).isEqualTo("UNKNOWN");assertThat(quotas.usage(owner).remaining()).isEqualTo(9);verify(provider,times(1)).generate(any());}
 @Test void draftFromAnalysisIsRejectedAndReleased()throws Exception{
  var s=ai.createReview(owner,request(true));String draft=json.writeValueAsString(new AiDrafts.Draft(1,AiDrafts.Mode.TODO,0,"不应生成",null,null,List.of(),List.of(),List.of(new AiDrafts.Item("a","未经确认的事项","",LocalDate.now(),0,null,List.of()))));when(provider.generate(any())).thenReturn(new AiProvider.Result("{\"message\":\"越界草稿\",\"recommendedMode\":\"TODO\",\"draft\":"+draft+"}",1,1));ai.enqueue(owner,s.id(),s.version(),"review-invalid-001","只分析");run();assertThat(ai.read(owner,s.id()).turns().getFirst().state()).isEqualTo("FAILED");assertThat(quotas.usage(owner).remaining()).isEqualTo(10);assertThat(db.queryForObject("select count(*) from todos where owner_id=?",Long.class,owner)).isZero();
 }
 @Test void duplicateConcurrentSubmissionHasOneQuotaReservation()throws Exception{
  var s=ai.createReview(owner,request(true));try(var pool=Executors.newFixedThreadPool(3)){var jobs=pool.invokeAll(List.of(()->ai.enqueue(owner,s.id(),s.version(),"review-parallel-001","并发同一分析").id(),()->ai.enqueue(owner,s.id(),s.version(),"review-parallel-001","并发同一分析").id(),()->ai.enqueue(owner,s.id(),s.version(),"review-parallel-001","并发同一分析").id()));Set<Object> ids=new HashSet<>();for(var f:jobs)ids.add(f.get());assertThat(ids).hasSize(1);assertThat(quotas.usage(owner).remaining()).isEqualTo(9);}verifyNoInteractions(provider);
 }
 private long sessionsCount(String owner){return db.queryForObject("select count(*) from ai_sessions where owner_id=?",Long.class,owner);}
}
