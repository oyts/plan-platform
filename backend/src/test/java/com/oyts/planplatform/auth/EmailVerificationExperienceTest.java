package com.oyts.planplatform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest(properties={"app.mail-from=sender@example.test","app.security.email-verification-enabled=true","app.security.registration-enabled=false"})
@AutoConfigureMockMvc
class EmailVerificationExperienceTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired UserAccountRepository users;@Autowired PasswordEncoder passwords;@Autowired JdbcTemplate db;
    @MockBean JavaMailSender sender;
    UserAccount user(){return users.saveAndFlush(new UserAccount("verify-"+UUID.randomUUID()+"@example.test",passwords.encode("Synthetic12345"),"合成邮箱验证"));}
    String request(UserAccount user)throws Exception{
        mvc.perform(post("/api/auth/email-verification/request").with(jwt().jwt(j->j.subject(user.getId())))).andExpect(status().isNoContent());
        var capture=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(capture.capture());var mail=capture.getValue();assertThat(mail.getCc()).isNull();assertThat(mail.getBcc()).isNull();assertThat(mail.getTo()).containsExactly(user.getEmail());
        return Arrays.stream(mail.getText().split("\n")).filter(line->line.contains("/verify-email?token=")).findFirst().orElseThrow().split("token=")[1];
    }
    int confirm(String token)throws Exception{return mvc.perform(post("/api/auth/email-verification/confirm").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token",token)))).andReturn().getResponse().getStatus();}
    @Test void statusCooldownAndOneTimeConfirmationNeverLockOutAnExistingAccount()throws Exception{
        var user=user();mvc.perform(get("/api/auth/email-verification/status").with(jwt().jwt(j->j.subject(user.getId())))).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.verified").value(false));
        String token=request(user);
        mvc.perform(post("/api/auth/email-verification/request").with(jwt().jwt(j->j.subject(user.getId())))).andExpect(status().isTooManyRequests());
        var response=mvc.perform(get("/api/auth/email-verification/status").with(jwt().jwt(j->j.subject(user.getId())))).andReturn().getResponse();assertThat(json.readTree(response.getContentAsString()).path("retryAfterSeconds").asInt()).isBetween(1,60);
        assertThat(confirm(token)).isEqualTo(204);assertThat(confirm(token)).isEqualTo(401);
        mvc.perform(get("/api/auth/email-verification/status").with(jwt().jwt(j->j.subject(user.getId())))).andExpect(jsonPath("$.verified").value(true));
        mvc.perform(post("/api/auth/email-verification/request").with(jwt().jwt(j->j.subject(user.getId())))).andExpect(status().isNoContent());verify(sender,times(1)).send(any(SimpleMailMessage.class));
    }
    @Test void smtpFailureIsVisibleAndPreservesPreviouslyValidLink()throws Exception{
        var user=user();String token="synthetic-old-valid-token";
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        tokens.saveAndFlush(new EmailVerificationToken(user,hash,java.time.Instant.now().plusSeconds(3600)));
        db.update("update email_verification_tokens set created_at=DATEADD('MINUTE',-2,CURRENT_TIMESTAMP) where user_id=?",user.getId());
        doThrow(new MailSendException("synthetic-private-smtp-detail")).when(sender).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/auth/email-verification/request").with(jwt().jwt(j->j.subject(user.getId())))).andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-private-smtp-detail"))));
        assertThat(db.queryForObject("select count(*) from email_verification_tokens where user_id=? and used_at is null",Long.class,user.getId())).isEqualTo(1);assertThat(confirm(token)).isEqualTo(204);
    }
    @Autowired EmailVerificationTokenRepository tokens;
    @Test void invalidAndExpiredLinksDoNotVerifyUsers()throws Exception{
        var user=user();String token=request(user);db.update("update email_verification_tokens set expires_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP) where user_id=?",user.getId());
        assertThat(confirm(token)).isEqualTo(401);assertThat(confirm("synthetic-invalid")).isEqualTo(401);assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
    }
    @Test void verificationDoesNotOpenRegistrationOrRequireUnverifiedMembersToReauthenticate()throws Exception{
        var user=user();mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",user.getEmail(),"password","Synthetic12345")))).andExpect(status().isOk()).andExpect(jsonPath("$.user.emailVerified").value(false));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email","uninvited-"+UUID.randomUUID()+"@example.test","password","Synthetic12345","displayName","合成未邀请")))).andExpect(status().isForbidden());
    }
    @Test void concurrentConfirmationsConsumeExactlyOnce()throws Exception{
        var user=user();String token=request(user);var pool=Executors.newFixedThreadPool(2);try{var jobs=pool.invokeAll(List.<Callable<Integer>>of(()->confirm(token),()->confirm(token)));var results=List.of(jobs.get(0).get(),jobs.get(1).get());assertThat(results.stream().filter(code->code==204).count()).isEqualTo(1);assertThat(results.stream().filter(code->code!=204).findFirst().orElseThrow()).isIn(401,409);assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();assertThat(db.queryForObject("select count(*) from email_verification_tokens where user_id=? and used_at is not null",Long.class,user.getId())).isEqualTo(1);}finally{pool.shutdownNow();}
    }
    @Test void disabledAccountCannotBeVerifiedAndAnonymousCannotRequestMail()throws Exception{
        var user=user();String token=request(user);db.update("update users set enabled=false where id=?",user.getId());assertThat(confirm(token)).isEqualTo(401);
        mvc.perform(post("/api/auth/email-verification/request")).andExpect(status().isUnauthorized());verify(sender,times(1)).send(any(SimpleMailMessage.class));
    }
}
