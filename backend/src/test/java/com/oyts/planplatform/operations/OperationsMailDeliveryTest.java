package com.oyts.planplatform.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"app.operations.enabled=true","app.operations.mail-alerts-enabled=true","app.operations.mail-to=operations@example.test","app.mail-from=sender@example.test","app.operations.scheduler-enabled=false"})
class OperationsMailDeliveryTest {
    static final Path DIRECTORY=temporary(),FILE=DIRECTORY.resolve("status.json");
    static Path temporary(){try{return Files.createTempDirectory("plan-mail-events-");}catch(Exception e){throw new IllegalStateException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("app.operations.status-file",()->FILE.toString());}
    @Autowired OperationsService operations;@Autowired OperationsMailDelivery delivery;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
    @Autowired org.springframework.beans.factory.ObjectProvider<JavaMailSender> providers;@Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockBean JavaMailSender sender;
    @BeforeEach void resetEvents()throws Exception{db.update("delete from operations_mail_delivery");db.update("delete from operations_alerts");reset(sender);Files.writeString(FILE,"{}");}
    @AfterAll static void cleanup()throws Exception{Files.deleteIfExists(FILE);Files.deleteIfExists(DIRECTORY);}
    void healthy()throws Exception{
        String now=Instant.now().toString(),sha="a".repeat(64);
        Files.writeString(FILE,json.writeValueAsString(Map.of("schemaVersion",1,"observedAt",now,"backups",List.of(Map.of("name","plan_platform-mysql-20261019-000000.sql.gz","completedAt",now,"bytes",100,"sha256",sha,"localVerified",true)),"catalogComplete",true,"oss",Map.of("sha256",sha,"verifiedAt",now,"verified",true),"drill",Map.of("sha256",sha,"completedAt",now,"matched",true),"disk",Map.of("freeBytes",80,"totalBytes",100),"failures",List.of())));
    }
    @Test void repeatedIncidentSendsOneWhitelistOnlyBatchAndRecoverySendsOnce()throws Exception{
        operations.evaluate();operations.evaluate();assertThat(delivery.status().deliveries().get("PENDING")).isEqualTo(1);
        delivery.dispatch();delivery.dispatch();assertThat(delivery.status().deliveries().get("ACCEPTED")).isEqualTo(1);
        healthy();operations.evaluate();operations.evaluate();delivery.dispatch();delivery.dispatch();
        var capture=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender,times(2)).send(capture.capture());
        for(var mail:capture.getAllValues()){assertThat(mail.getTo()).containsExactly("operations@example.test");assertThat(mail.getCc()).isNull();assertThat(mail.getBcc()).isNull();assertThat(mail.getText()).doesNotContain(FILE.toString(),"password","token=","accessToken");}
        assertThat(capture.getAllValues().get(1).getText()).contains("已恢复：OPS_DATA_UNAVAILABLE");
    }
    @Test void unresolvedNewCodesAreCombinedIntoOneMessage()throws Exception{
        healthy();var root=json.readTree(Files.readString(FILE));((com.fasterxml.jackson.databind.node.ObjectNode)root).set("failures",json.valueToTree(List.of("BACKUP_FAILED","OSS_FAILED")));Files.writeString(FILE,json.writeValueAsString(root));
        operations.evaluate();delivery.dispatch();var capture=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender,times(1)).send(capture.capture());assertThat(capture.getValue().getText()).contains("BACKUP_FAILED","OSS_FAILED");
    }
    @Test void ambiguousSmtpFailureIsNotRetriedAndContainsNoRawFailureDetails()throws Exception{
        doThrow(new MailSendException("synthetic-secret-transport-detail")).when(sender).send(any(SimpleMailMessage.class));operations.evaluate();delivery.dispatch();operations.evaluate();delivery.dispatch();
        assertThat(delivery.status().deliveries().get("UNKNOWN")).isEqualTo(1);assertThat(delivery.status().toString()).doesNotContain("synthetic-secret-transport-detail");verify(sender,times(1)).send(any(SimpleMailMessage.class));
    }
    @Test void recoveryBeforeSendingSupersedesTheUnsentFault()throws Exception{
        operations.evaluate();healthy();operations.evaluate();delivery.dispatch();assertThat(delivery.status().deliveries().get("SUPERSEDED")).isEqualTo(1);verifyNoInteractions(sender);
    }
    @Test void statusReadsNeverSendOrCreateAlerts()throws Exception{
        operations.status();delivery.status();operations.status();assertThat(db.queryForObject("select count(*) from operations_mail_delivery",Long.class)).isZero();verifyNoInteractions(sender);
    }
    @Test void abandonedSendingIsMarkedUnknownInsteadOfBeingReplayed()throws Exception{
        operations.evaluate();db.update("update operations_mail_delivery set state='SENDING',updated_at=DATEADD('MINUTE',-11,CURRENT_TIMESTAMP)");delivery.dispatch();assertThat(delivery.status().deliveries().get("UNKNOWN")).isEqualTo(1);verifyNoInteractions(sender);
    }
    @Test void telemetryMailRequiresOwnGateAndHasDurableCooldown(){
        delivery.opened(UUID.randomUUID().toString(),"HTTP_5XX","窗口2分钟，请求100，5xx20。");assertThat(db.queryForObject("select count(*) from operations_mail_delivery",Long.class)).isZero();
        org.springframework.test.util.ReflectionTestUtils.setField(delivery,"metricMail",true);
        try{delivery.opened(UUID.randomUUID().toString(),"HTTP_5XX","窗口2分钟，请求100，5xx20。");delivery.opened(UUID.randomUUID().toString(),"HTTP_5XX","窗口2分钟，请求200，5xx40。");delivery.dispatch();var capture=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender,times(1)).send(capture.capture());assertThat(capture.getValue().getText()).contains("窗口2分钟，请求100").doesNotContain("请求200");}finally{org.springframework.test.util.ReflectionTestUtils.setField(delivery,"metricMail",false);}
    }
    @Test void telemetryDisabledPendingDoesNotBlockOriginalBackupMail(){
        org.springframework.test.util.ReflectionTestUtils.setField(delivery,"metricMail",true);delivery.opened(UUID.randomUUID().toString(),"CPU_PRESSURE","3次采样");org.springframework.test.util.ReflectionTestUtils.setField(delivery,"metricMail",false);
        delivery.opened(UUID.randomUUID().toString(),"BACKUP_FAILED");delivery.dispatch();assertThat(delivery.status().deliveries().get("SUPERSEDED")).isEqualTo(1);assertThat(delivery.status().deliveries().get("ACCEPTED")).isEqualTo(1);var capture=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(capture.capture());assertThat(capture.getValue().getText()).contains("BACKUP_FAILED").doesNotContain("CPU_PRESSURE");
    }
    @Test void disabledOrMissingRecipientNeverQueuesOrSends(){
        for(var gated:List.of(new OperationsMailDelivery(db,providers,transactions,true,false,false,"operations@example.test","sender@example.test"),new OperationsMailDelivery(db,providers,transactions,true,true,false,"","sender@example.test"))){
            gated.opened(UUID.randomUUID().toString(),"BACKUP_STALE");gated.dispatch();assertThat(gated.enabled()).isFalse();
        }
        assertThat(db.queryForObject("select count(*) from operations_mail_delivery",Long.class)).isZero();verifyNoInteractions(sender);
    }
}
