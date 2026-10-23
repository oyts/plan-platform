package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.*;
import com.oyts.planplatform.PlanPlatformApplication;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Test classpath only. Synthetic responses; cannot send to any AI provider. */
@TestConfiguration
public class AiReliabilityFixture {
    @Bean org.springframework.boot.ApplicationRunner syntheticQuota(org.springframework.jdbc.core.JdbcTemplate db){return args->db.update("UPDATE ai_quota_policy SET daily_limit=100 WHERE id=1");}
    @Bean @Primary AiProvider syntheticProvider(ObjectMapper json){return request->{
        try{
            JsonNode context=json.readTree(request.message());boolean repair="OUTPUT_REPAIR".equals(context.path("intent").asText());
            if(repair)context=json.readTree(context.path("originalContext").asText());
            String selected=context.path("selectedMode").asText("ROUTE"),intent=context.path("requestIntent").asText();
            if("DIALOGUE".equals(intent)||context.path("selectedMode").isNull())return new AiProvider.Result(json.writeValueAsString(Map.of("message","已记住你的目标与限制。可以继续补充；选好模式后点击生成规划草稿。","recommendedMode",selected,"draft",json.nullNode())),100,100);
            if(!repair || context.path("message").asText().contains("[invalid-always]"))return new AiProvider.Result("{\"message\":\"正在准备草稿，请稍候核对。\",\"recommendedMode\":\""+selected+"\",\"draft\":{}}",100,100);
            var mode=AiDrafts.Mode.valueOf(selected);String day=context.path("today").asText();
            var draft=new AiDrafts.Draft(1,mode,0,"合成Java规划",mode==AiDrafts.Mode.PLAN?java.time.LocalDate.parse(day):null,mode==AiDrafts.Mode.PLAN?java.time.LocalDate.parse(day).plusMonths(2):null,
                mode==AiDrafts.Mode.PLAN?List.of(7):List.of(),List.of(),List.of(new AiDrafts.Item("one","合成第一步","仅本机假上游验收",mode==AiDrafts.Mode.ROUTE?null:java.time.LocalDate.parse(day),30,null,List.of())));
            // Keep Sunday as rest day in the synthetic PLAN fixture.
            if(mode==AiDrafts.Mode.PLAN && java.time.LocalDate.parse(day).getDayOfWeek().getValue()==7)draft=new AiDrafts.Draft(1,mode,0,draft.title(),draft.startDate(),draft.endDate(),draft.restDays(),draft.stages(),List.of(new AiDrafts.Item("one","合成第一步","仅本机假上游验收",draft.startDate().plusDays(1),30,null,List.of())));
            Thread.sleep(2500);return new AiProvider.Result(json.writeValueAsString(Map.of("message","修复后的合成草稿已准备，请预览。尚未创建真实计划。","recommendedMode",selected,"draft",draft)),150,250);
        }catch(Exception invalid){throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,false);}
    };}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("One isolated output directory required");
        Path output=Path.of(args[0]).toRealPath();Path allowed=Path.of("acceptance-results/ai-reliability").toRealPath();
        if(!output.startsWith(allowed)||output.equals(allowed))throw new IllegalArgumentException("Synthetic directory required");
        new SpringApplicationBuilder(PlanPlatformApplication.class,AiReliabilityFixture.class).run(
            "--spring.profiles.active=acceptance","--server.address=127.0.0.1","--server.port=43171",
            "--app.cors-allowed-origins=http://127.0.0.1:43130,http://localhost:43130,tauri://localhost,http://tauri.localhost",
            "--spring.datasource.url=jdbc:h2:file:"+output.resolve("synthetic-db").toString().replace('\\','/')+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0",
            "--spring.datasource.driver-class-name=org.h2.Driver","--spring.datasource.username=sa","--spring.datasource.password=",
            "--app.security.registration-enabled=true","--app.security.email-verification-enabled=false","--app.security.refresh-cookie-secure=false",
            "--app.security.refresh-cookie-name=plan_refresh","--app.security.refresh-cookie-same-site=Strict","--app.security.bootstrap-admin-email=",
            "--app.jwt.secret=synthetic-reliability-only-at-least-32-bytes","--app.jwt.issuer=synthetic-reliability",
            "--app.ai.enabled=true","--app.ai.api-key-file=/synthetic-unused","--app.ai.repair-enabled=true","--app.ai.worker-enabled=true",
            "--app.operations.enabled=false","--app.governance.enabled=false","--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration");
    }
}
