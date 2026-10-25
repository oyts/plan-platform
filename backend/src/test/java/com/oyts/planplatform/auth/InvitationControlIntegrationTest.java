package com.oyts.planplatform.auth;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"app.mail-from=synthetic-sender@example.test"})
class InvitationControlIntegrationTest {
    @Autowired AdminService admin;@Autowired UserAccountRepository users;@Autowired JdbcTemplate db;
    @SpyBean InvitationRepository invitations;@MockBean JavaMailSender mail;String actor,other,email;
    @BeforeEach void setup(){reset(mail,invitations);actor=user();other=user();email="invite-control-"+UUID.randomUUID()+"@example.test";}
    String user(){var u=new UserAccount("invite-admin-"+UUID.randomUUID()+"@example.test","unused-hash","邀请合成管理员");u.makeAdmin();return users.saveAndFlush(u).getId();}
    AuthDtos.InvitationRequest request(String key){return new AuthDtos.InvitationRequest(email,24,key);}
    void old(String id){db.update("UPDATE invitations SET created_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(61)),id);}
    @Test void durableReplayDoesNotSendTwiceOrDiscloseRawLink(){String key=UUID.randomUUID().toString();var first=admin.create(actor,request(key));var repeated=admin.create(actor,request(key));assertEquals(first.id(),repeated.id());assertTrue(repeated.replayed());assertNull(repeated.registrationUrl());assertEquals("SENT",repeated.deliveryStatus());verify(mail,times(1)).send(any(SimpleMailMessage.class));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM admin_invitation_operations WHERE actor_id=?",Integer.class,actor));}
    @Test void operationKeyCannotChangeEmailOrDuration(){String key=UUID.randomUUID().toString();var first=admin.create(actor,request(key));assertThrows(com.oyts.planplatform.common.ConflictException.class,()->admin.create(actor,new AuthDtos.InvitationRequest(email,25,key)));assertNull(invitations.findById(first.id()).orElseThrow().getRevokedAt());verify(mail,times(1)).send(any(SimpleMailMessage.class));}
    @Test void cooldownIsSharedBetweenAdministratorsAndSuccessfulRenewalInvalidatesOnlyAfterCreation(){var first=admin.create(actor,request(UUID.randomUUID().toString()));assertThrows(com.oyts.planplatform.common.TooManyRequestsException.class,()->admin.create(other,request(UUID.randomUUID().toString())));assertNull(invitations.findById(first.id()).orElseThrow().getRevokedAt());old(first.id());var next=admin.create(other,request(UUID.randomUUID().toString()));assertNotEquals(first.id(),next.id());assertNotNull(invitations.findById(first.id()).orElseThrow().getRevokedAt());assertNull(invitations.findById(next.id()).orElseThrow().getRevokedAt());verify(mail,times(2)).send(any(SimpleMailMessage.class));}
    @Test void mailFailureIsCommittedCreationWithOneTimeFallbackNotAutomaticResend(){doThrow(new MailSendException("synthetic offline SMTP")).when(mail).send(any(SimpleMailMessage.class));String key=UUID.randomUUID().toString();var first=admin.create(actor,request(key));assertEquals("FALLBACK",first.deliveryStatus());assertTrue(first.registrationUrl().contains("/register?"));assertNull(admin.create(actor,request(key)).registrationUrl());verify(mail,times(1)).send(any(SimpleMailMessage.class));var stored=db.queryForMap("SELECT fingerprint,delivery_status FROM admin_invitation_operations WHERE actor_id=?",actor);assertEquals("FALLBACK",stored.get("delivery_status"));assertTrue(stored.get("fingerprint").toString().matches("[a-f0-9]{64}"));assertFalse(admin.invitations().stream().anyMatch(i->i.registrationUrl()!=null));}
    @Test void failedTransactionPreservesPreviousLinkAndDoesNotSendMail(){var first=admin.create(actor,request(UUID.randomUUID().toString()));old(first.id());doThrow(new org.springframework.dao.DataIntegrityViolationException("synthetic insert failure")).when(invitations).saveAndFlush(any(Invitation.class));assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->admin.create(actor,request(UUID.randomUUID().toString())));assertNull(invitations.findById(first.id()).orElseThrow().getRevokedAt());verify(mail,times(1)).send(any(SimpleMailMessage.class));}
    @Test void parallelNewRequestsToSameEmailProduceOneInvitationAndOneMail()throws Exception{var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var results=new ArrayList<Future<Boolean>>();for(String who:List.of(actor,other))results.add(pool.submit(()->{start.await();try{admin.create(who,request(UUID.randomUUID().toString()));return true;}catch(com.oyts.planplatform.common.TooManyRequestsException limited){return false;}}));start.countDown();int success=0;for(var f:results)if(f.get(10,TimeUnit.SECONDS))success++;assertEquals(1,success);}assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM invitations WHERE email=?",Integer.class,email));verify(mail,times(1)).send(any(SimpleMailMessage.class));}
}
