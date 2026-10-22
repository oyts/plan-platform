package com.oyts.planplatform.auth;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.security.email-change-enabled=true","app.mail-from=sender@example.test","app.security.registration-enabled=false"})
@AutoConfigureMockMvc
class EmailChangeIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired UserAccountRepository users;@Autowired PasswordEncoder passwords;@Autowired JdbcTemplate db;
    @MockBean JavaMailSender sender;@MockBean MailRequestLimiter limiter;
    @BeforeEach void admission(){when(limiter.acquire(anyString(),anyString(),anyString())).thenReturn(true);when(limiter.retryAfterSeconds(anyString(),anyString(),anyString())).thenReturn(0);}
    private UserAccount user(){return users.saveAndFlush(new UserAccount("mail-change-"+UUID.randomUUID()+"@example.test",passwords.encode("Synthetic12345"),"合成邮箱变更"));}
    record Link(String id,String side,String token){}
    private List<Link> start(UserAccount user,String target) throws Exception {
        reset(sender);
        mvc.perform(post("/api/auth/email-change/request").with(jwt().jwt(j->j.subject(user.getId())))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("currentPassword","Synthetic12345","newEmail",target))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PENDING"));
        var capture=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender,times(2)).send(capture.capture());List<Link> result=new ArrayList<>();
        for(var mail:capture.getAllValues()){
            assertThat(mail.getCc()).isNull();assertThat(mail.getBcc()).isNull();assertThat(mail.getTo()).hasSize(1);
            String link=Arrays.stream(mail.getText().split("\n")).filter(l->l.contains("/change-email#")).findFirst().orElseThrow();
            assertThat(link).doesNotContain("?token=");Map<String,String> params=new HashMap<>();for(String item:link.substring(link.indexOf('#')+1).split("&")){var pair=item.split("=",2);params.put(pair[0],pair[1]);}
            result.add(new Link(params.get("request"),params.get("side"),params.get("token")));
            assertThat(mail.getTo()).containsExactly(params.get("side").equals("old")?user.getEmail():target);
            assertThat(db.queryForObject("select count(*) from email_change_requests where old_token_hash=? or new_token_hash=?",Integer.class,params.get("token"),params.get("token"))).isZero();
        }
        return result;
    }
    private int verifyLink(Link link)throws Exception{return mvc.perform(post("/api/auth/email-change/verify").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("requestId",link.id(),"side",link.side(),"token",link.token())))).andReturn().getResponse().getStatus();}
    private int finish(UserAccount user,String id)throws Exception{return mvc.perform(post("/api/auth/email-change/complete").with(jwt().jwt(j->j.subject(user.getId())))
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("requestId",id,"currentPassword","Synthetic12345","confirmed",true)))).andReturn().getResponse().getStatus();}

    @Test void requiresBothMailboxesThenFinalPasswordAndRevokesAllSessions()throws Exception {
        var user=user();String target="new-"+UUID.randomUUID()+"@example.test";
        String login=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",user.getEmail(),"password","Synthetic12345")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String oldAccess=json.readTree(login).path("accessToken").asText();
        List<Link> links=start(user,target);assertThat(finish(user,links.getFirst().id())).isEqualTo(409);
        assertThat(verifyLink(links.get(0))).isEqualTo(204);assertThat(finish(user,links.getFirst().id())).isEqualTo(409);
        assertThat(verifyLink(links.get(1))).isEqualTo(204);
        assertThat(users.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(user.getEmail());
        assertThat(finish(user,links.getFirst().id())).isEqualTo(204);
        var updated=users.findById(user.getId()).orElseThrow();assertThat(updated.getEmail()).isEqualTo(target);assertThat(updated.isEmailVerified()).isTrue();assertThat(updated.getAuthGeneration()).isEqualTo(1);
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+oldAccess)).andExpect(status().isUnauthorized());
        assertThat(db.queryForObject("select count(*) from refresh_tokens where user_id=? and revoked_at is null",Integer.class,user.getId())).isZero();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",target,"password","Synthetic12345")))).andExpect(status().isOk());
        assertThat(verifyLink(links.getFirst())).isEqualTo(401);
    }
    @Test void openingLinkOrWrongSideDoesNotConsumeVerification()throws Exception {
        var user=user();var links=start(user,"new-"+UUID.randomUUID()+"@example.test");Link link=links.getFirst();
        mvc.perform(get("/api/auth/email-change/verify")).andExpect(status().isUnauthorized());
        Link wrong=new Link(link.id(),link.side().equals("old")?"new":"old",link.token());assertThat(verifyLink(wrong)).isEqualTo(401);
        assertThat(verifyLink(link)).isEqualTo(204);assertThat(verifyLink(link)).isEqualTo(401);
    }
    @Test void expiryCancelAndOwnerIsolationProtectRequests()throws Exception {
        var user=user();var other=user();var links=start(user,"new-"+UUID.randomUUID()+"@example.test");String id=links.getFirst().id();
        mvc.perform(post("/api/auth/email-change/{id}/cancel",id).with(jwt().jwt(j->j.subject(other.getId())))).andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/email-change/{id}/cancel",id).with(jwt().jwt(j->j.subject(user.getId())))).andExpect(status().isNoContent());assertThat(verifyLink(links.getFirst())).isEqualTo(401);
        db.update("update email_change_requests set created_at=DATEADD('MINUTE',-2,CURRENT_TIMESTAMP) where id=?",id);
        links=start(user,"other-new-"+UUID.randomUUID()+"@example.test");id=links.getFirst().id();
        db.update("update email_change_requests set expires_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP) where id=?",id);assertThat(verifyLink(links.getFirst())).isEqualTo(401);
    }
    @Test void passwordOrGenerationChangeInvalidatesProofAndEmailOccupancyIsGeneric()throws Exception {
        var user=user();var occupied=user();
        mvc.perform(post("/api/auth/email-change/request").with(jwt().jwt(j->j.subject(user.getId()))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("currentPassword","Synthetic12345","newEmail",occupied.getEmail())))).andExpect(status().isConflict()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("已经注册"))));
        var links=start(user,"new-"+UUID.randomUUID()+"@example.test");db.update("update users set password_hash=? where id=?",passwords.encode("NewSynthetic12345"),user.getId());assertThat(verifyLink(links.getFirst())).isEqualTo(401);
        mvc.perform(get("/api/auth/email-change/status").with(jwt().jwt(j->j.subject(user.getId())))).andExpect(jsonPath("$.state").value("INVALIDATED"));
    }
    @Test void partialSmtpFailureDoesNotReplacePreviouslyValidRequest()throws Exception {
        var user=user();var links=start(user,"new-"+UUID.randomUUID()+"@example.test");String oldId=links.getFirst().id();
        db.update("update email_change_requests set created_at=DATEADD('MINUTE',-2,CURRENT_TIMESTAMP) where id=?",oldId);
        reset(sender);doNothing().doThrow(new MailSendException("synthetic-private-smtp-detail")).when(sender).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/auth/email-change/request").with(jwt().jwt(j->j.subject(user.getId()))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("currentPassword","Synthetic12345","newEmail","later-"+UUID.randomUUID()+"@example.test"))))
            .andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-private-smtp-detail"))));
        assertThat(db.queryForObject("select count(*) from email_change_requests where owner_id=?",Integer.class,user.getId())).isEqualTo(1);assertThat(verifyLink(links.getFirst())).isEqualTo(204);
    }
    @Test void concurrentSameMailboxProofIsConsumedOnce()throws Exception {
        var user=user();Link link=start(user,"new-"+UUID.randomUUID()+"@example.test").getFirst();
        try(var pool=Executors.newFixedThreadPool(2)) {var jobs=pool.invokeAll(List.<Callable<Integer>>of(()->verifyLink(link),()->verifyLink(link)));assertThat(List.of(jobs.get(0).get(),jobs.get(1).get())).containsExactlyInAnyOrder(204,401);}
    }
    @Test void wrongPasswordConfirmationAndAdmissionFailureNeverSendMail()throws Exception {
        var user=user();mvc.perform(post("/api/auth/email-change/request").with(jwt().jwt(j->j.subject(user.getId()))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("currentPassword","wrong","newEmail","new@example.test")))).andExpect(status().isBadRequest());verifyNoInteractions(sender);
        when(limiter.acquire(eq("email-change"),anyString(),anyString())).thenReturn(false);
        mvc.perform(post("/api/auth/email-change/request").with(jwt().jwt(j->j.subject(user.getId()))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("currentPassword","Synthetic12345","newEmail","new@example.test")))).andExpect(status().isTooManyRequests());verifyNoInteractions(sender);
    }
    @Test void concurrentFinalConfirmationCannotAssignOneMailboxToTwoAccounts()throws Exception {
        var a=user();var b=user();String target="racing-"+UUID.randomUUID()+"@example.test";
        var one=start(a,target);var two=start(b,target);
        for(Link link:one)assertThat(verifyLink(link)).isEqualTo(204);for(Link link:two)assertThat(verifyLink(link)).isEqualTo(204);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var first=pool.submit(()->{gate.await();return finish(a,one.getFirst().id());});
            var second=pool.submit(()->{gate.await();return finish(b,two.getFirst().id());});gate.countDown();
            assertThat(List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,409);
        }
        assertThat(db.queryForObject("select count(*) from users where email=?",Integer.class,target)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from email_change_requests where new_email=? and consumed_at is not null",Integer.class,target)).isEqualTo(1);
    }
    @Test void generationChangeAndPersistentCooldownCannotBeBypassed()throws Exception {
        var user=user();var links=start(user,"new-"+UUID.randomUUID()+"@example.test");
        mvc.perform(post("/api/auth/email-change/request").with(jwt().jwt(j->j.subject(user.getId()))).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("currentPassword","Synthetic12345","newEmail","later@example.test")))).andExpect(status().isTooManyRequests());
        db.update("update users set auth_generation=auth_generation+1 where id=?",user.getId());assertThat(verifyLink(links.getFirst())).isEqualTo(401);
        mvc.perform(post("/api/auth/email-change/complete").with(jwt().jwt(j->j.subject(user.getId()))).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("requestId",links.getFirst().id(),"currentPassword","Synthetic12345","confirmed",true)))).andExpect(status().isUnauthorized());
    }
    @Test void successfulChangeRevokesOldResetProofsAndReminderDevices()throws Exception {
        var user=user();var links=start(user,"new-"+UUID.randomUUID()+"@example.test");
        String reset="synthetic-old-reset",proof="synthetic-old-proof";
        resets.saveAndFlush(new PasswordResetToken(user,hash(reset),java.time.Instant.now().plusSeconds(3600)));
        proofs.saveAndFlush(new EmailVerificationToken(user,hash(proof),java.time.Instant.now().plusSeconds(3600)));
        String device=UUID.randomUUID().toString();
        db.update("insert into native_reminder_devices (id,owner_id,platform,label,token_hash,account_stamp,enabled,expires_at,created_at) values (?,?,?,?,?,?,true,DATEADD('HOUR',1,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP)",device,user.getId(),"WINDOWS","合成旧提醒",hash(device),"a".repeat(64));
        for(Link link:links)assertThat(verifyLink(link)).isEqualTo(204);assertThat(finish(user,links.getFirst().id())).isEqualTo(204);
        assertThat(db.queryForObject("select enabled from native_reminder_devices where id=?",Boolean.class,device)).isFalse();
        assertThat(db.queryForObject("select count(*) from password_reset_tokens where user_id=? and used_at is null",Integer.class,user.getId())).isZero();
        assertThat(db.queryForObject("select count(*) from email_verification_tokens where user_id=? and used_at is null",Integer.class,user.getId())).isZero();
    }
    @Autowired PasswordResetTokenRepository resets;@Autowired EmailVerificationTokenRepository proofs;
    private static String hash(String value)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
}
