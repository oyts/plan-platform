package com.oyts.planplatform.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.mail-from=sender@example.test","app.security.registration-enabled=false"})
@AutoConfigureMockMvc
class InvitationMailCopyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserAccountRepository users;
    @Autowired PasswordResetMailService mail;
    @MockBean JavaMailSender sender;

    UserAccount administrator() {
        var user=new UserAccount("copy-admin-"+UUID.randomUUID()+"@example.test","not-used-for-login","邀请抄送合成管理员");
        user.makeAdmin();return users.saveAndFlush(user);
    }

    @Test void invitationSendsOnlyToRecipientAndIgnoresClientCopyFields() throws Exception {
        var admin=administrator();String email="copy-invited-"+UUID.randomUUID()+"@example.test";
        var response=mvc.perform(post("/api/admin/invitations")
            .with(jwt().jwt(j->j.subject(admin.getId())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email",email,"expiresInHours",24,"cc","untrusted@example.test"))))
            .andExpect(status().isCreated()).andReturn();
        var captured=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(captured.capture());
        var message=captured.getValue();
        assertArrayEquals(new String[]{email},message.getTo());
        assertNull(message.getCc());assertNull(message.getBcc());
        assertEquals("sender@example.test",message.getFrom());assertEquals("计划平台：邀请注册",message.getSubject());
        assertTrue(message.getText().contains("请勿转发此链接"));assertFalse(message.getText().contains("管理员抄送"));
        assertFalse(response.getResponse().getContentAsString().contains("invite="));
        String link=Arrays.stream(message.getText().split("\n")).filter(s->s.contains("/register?")).findFirst().orElseThrow();
        assertTrue(link.contains("email="+java.net.URLEncoder.encode(email,StandardCharsets.UTF_8)));
        String token=URLDecoder.decode(link.substring(link.indexOf("&invite=")+8),StandardCharsets.UTF_8);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email","different-"+UUID.randomUUID()+"@example.test","password","Synthetic12345","displayName","合成账号","invitationToken",token))))
            .andExpect(status().isForbidden());
    }

    @Test void transportFailurePreservesTheExistingOneTimeManualFallback() throws Exception {
        var admin=administrator();String email="copy-failure-"+UUID.randomUUID()+"@example.test";
        doThrow(new MailSendException("Synthetic SMTP rejection")).when(sender).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/admin/invitations")
            .with(jwt().jwt(j->j.subject(admin.getId())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email",email,"expiresInHours",24))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.registrationUrl").isString());
        mvc.perform(get("/api/admin/invitations")
            .with(jwt().jwt(j->j.subject(admin.getId())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("invite="))));
    }

    @Test void verificationMailAndTestMailAreNeverCopied() {
        var user=new UserAccount("recipient@example.test","unused","合成收件人");
        assertTrue(mail.sendEmailVerification(user,"synthetic-verification-token"));
        assertTrue(mail.sendTest("administrator@example.test"));
        var captured=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender,times(2)).send(captured.capture());
        for(var message:captured.getAllValues()){assertNull(message.getCc());assertNull(message.getBcc());}
    }

    @Test void sameRecipientCopyIsNotDuplicatedAndMembersCannotSendInvitations() throws Exception {
        assertTrue(mail.sendInvitation("same@example.test","synthetic-invitation-token"));
        var captured=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(captured.capture());assertNull(captured.getValue().getCc());
        var member=users.saveAndFlush(new UserAccount("no-invite-"+UUID.randomUUID()+"@example.test","unused","合成普通成员"));
        mvc.perform(post("/api/admin/invitations").with(jwt().jwt(j->j.subject(member.getId())).authorities(new SimpleGrantedAuthority("ROLE_USER")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@example.test\",\"expiresInHours\":24}"))
            .andExpect(status().isForbidden());
        verify(sender,times(1)).send(any(SimpleMailMessage.class));
    }
}
