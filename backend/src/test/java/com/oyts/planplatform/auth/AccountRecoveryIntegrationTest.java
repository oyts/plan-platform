package com.oyts.planplatform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.security.password-reset-enabled=true","app.mail-from=sender@example.test"})
@AutoConfigureMockMvc
class AccountRecoveryIntegrationTest {
    @Autowired MockMvc mvc;@Autowired UserAccountRepository users;@Autowired PasswordEncoder encoder;@Autowired ObjectMapper mapper;
    @Autowired AuthService auth;@Autowired PasswordResetTokenRepository resetTokens;@Autowired AdminService admins;@Autowired InvitationRepository invitations;
    @MockBean JavaMailSender sender;
    UserAccount user(){return users.saveAndFlush(new UserAccount("recover-"+UUID.randomUUID()+"@example.test",encoder.encode("Original12345"),"合成恢复账号"));}
    String json(Object value)throws Exception{return mapper.writeValueAsString(value);}
    @Test void resetIsOneTimeRateLimitedAndRevokesOldRefresh()throws Exception{
        var user=user();
        var login=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email",user.getEmail(),"password","Original12345")))).andExpect(status().isOk()).andReturn();
        var cookie=login.getResponse().getCookie("plan_refresh");
        mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email",user.getEmail())))).andExpect(status().isNoContent());
        var captured=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(captured.capture());
        String link=Arrays.stream(captured.getValue().getText().split("\n")).filter(s->s.contains("/reset-password?")).findFirst().orElseThrow();
        String token=URLDecoder.decode(link.substring(link.indexOf("&token=")+7),StandardCharsets.UTF_8);
        mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email",user.getEmail())))).andExpect(status().isNoContent());verify(sender,times(1)).send(any(SimpleMailMessage.class));
        var body=json(Map.of("email",user.getEmail(),"token",token,"newPassword","NewPassword12345"));
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email",user.getEmail(),"password","NewPassword12345")))).andExpect(status().isOk());
    }
    @Test void unknownEmailAndTransportFailureDoNotExposeAccountsOrTokens()throws Exception{
        var user=user();doThrow(new org.springframework.mail.MailSendException("synthetic failure")).when(sender).send(any(SimpleMailMessage.class));
        for(String email:List.of(user.getEmail(),"unknown-"+UUID.randomUUID()+"@example.test"))mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email",email)))).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email",user.getEmail(),"token","missing","newPassword","")))).andExpect(status().isBadRequest());
    }
    @Test void administratorMailTestIsPrivateAndCannotChooseAnotherRecipient()throws Exception{
        var user=user();user.makeAdmin();users.saveAndFlush(user);
        var admin=jwt().jwt(j->j.subject(user.getId())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(get("/api/admin/mail")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/mail").with(jwt().jwt(j->j.subject(user.getId())).authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/mail").with(admin)).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(post("/api/admin/mail/test").with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"recipient\":\"other@example.test\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));
        var mail=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(mail.capture());assertArrayEquals(new String[]{user.getEmail()},mail.getValue().getTo());assertFalse(mail.getValue().getText().contains("token="));
        mvc.perform(post("/api/admin/mail/test").with(admin)).andExpect(status().isTooManyRequests());
    }
    @Test void limiterAlsoBoundsUnknownAddressesAndNormalizesEmail(){
        var base=Instant.parse("2026-10-16T00:00:00Z");var limiter=new MailRequestLimiter(Clock.fixed(base,ZoneOffset.UTC));
        assertTrue(limiter.acquire("reset","one@example.test","127.0.0.1"));assertFalse(limiter.acquire("reset","ONE@example.test","another"));
        for(int i=1;i<10;i++)assertTrue(limiter.acquire("reset","unknown"+i+"@example.test","127.0.0.1"));
        assertFalse(limiter.acquire("reset","eleven@example.test","127.0.0.1"));
    }
    @Test void concurrentResetConsumesOneTokenExactlyOnce()throws Exception{
        var user=user();String raw=UUID.randomUUID().toString();
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        resetTokens.saveAndFlush(new PasswordResetToken(user,hash,Instant.now().plusSeconds(300)));
        var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var calls=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int i=0;i<2;i++)calls.add(executor.submit(()->{ready.countDown();start.await();try{auth.confirmPasswordReset(new AuthDtos.PasswordResetConfirmRequest(user.getEmail(),raw,"Concurrent12345"));return true;}catch(com.oyts.planplatform.common.UnauthorizedException used){return false;}}));
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));start.countDown();
            int success=0;for(var call:calls)if(call.get(10,java.util.concurrent.TimeUnit.SECONDS))success++;
            assertEquals(1,success);
        }
    }
    @Test void newInvitationRevokesOldLinkButRejectedRenewalPreservesIt(){
        var admin=user();admin.makeAdmin();users.saveAndFlush(admin);String email="invited-"+UUID.randomUUID()+"@example.test";
        var first=admins.create(admin.getId(),new AuthDtos.InvitationRequest(email,24));
        assertThrows(IllegalArgumentException.class,()->admins.create(admin.getId(),new AuthDtos.InvitationRequest(email,721)));
        assertNull(invitations.findById(first.id()).orElseThrow().getRevokedAt());
        var next=admins.create(admin.getId(),new AuthDtos.InvitationRequest(email,24));
        assertNotNull(invitations.findById(first.id()).orElseThrow().getRevokedAt());assertNull(invitations.findById(next.id()).orElseThrow().getRevokedAt());
    }
}
