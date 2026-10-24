package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oyts.planplatform.PlanPlatformApplication;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Only on the test classpath. No credentials and no path to a network provider. */
@TestConfiguration
public class AiLongPlanningFixture {
    @Bean org.springframework.boot.ApplicationRunner syntheticQuota(org.springframework.jdbc.core.JdbcTemplate db){return args->db.update("UPDATE ai_quota_policy SET daily_limit=100 WHERE id=1");}
    @Bean @Primary AiProvider syntheticProvider(ObjectMapper json){return request->{
        try{
            JsonNode c=json.readTree(request.message());boolean repair="OUTPUT_REPAIR".equals(c.path("intent").asText());if(repair)c=json.readTree(c.path("originalContext").asText());
            String selected=c.path("selectedMode").asText("ROUTE"),intent=c.path("requestIntent").asText(),message=c.path("message").asText();
            if(message.contains("[unknown]"))throw new AiFailure(AiFailure.Code.TIMEOUT,true);
            if(message.contains("[bad-always]")||message.contains("[bad-first]")&&!repair)return new AiProvider.Result("{\"message\":\"本批合成失败，内容保留。\",\"recommendedMode\":null,\"draft\":{}}",100,100);
            Object draft=json.nullNode();
            if(!"DIALOGUE".equals(intent)&&!c.path("selectedMode").isNull()){
                if("BATCH".equals(intent)){var partial=c.path("currentDraft").deepCopy();for(var item:partial.path("items"))((ObjectNode)item).put("note","本批细化：动手练习、检查结果并记录回顾。");draft=partial;}
                else {
                    var mode=AiDrafts.Mode.valueOf(selected);LocalDate day=LocalDate.of(2026,11,2);var items=new ArrayList<AiDrafts.Item>();
                    for(int i=0;i<12;i++)items.add(new AiDrafts.Item("t"+i,"合成学习事项"+(i+1),"OUTLINE".equals(intent)?"待细化方向":"合成执行说明",mode==AiDrafts.Mode.ROUTE?null:day.plusDays(i),30,mode==AiDrafts.Mode.ROUTE?"s1":null,mode==AiDrafts.Mode.ROUTE&&i>0?List.of("t"+(i-1)):List.of()));
                    draft=new AiDrafts.Draft(1,mode,0,"合成完整规划",mode==AiDrafts.Mode.PLAN?day:null,mode==AiDrafts.Mode.PLAN?day.plusDays(13):null,List.of(),mode==AiDrafts.Mode.ROUTE?List.of(new AiDrafts.Stage("s1","基础阶段")):List.of(),items);
                }
            }
            return new AiProvider.Result(json.writeValueAsString(Map.of("message","合成建议已准备，尚未创建或修改真实计划。","recommendedMode",selected,"draft",draft)),100,250);
        }catch(AiFailure safe){throw safe;}catch(Exception invalid){throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,false);}
    };}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("One isolated directory required");Path out=Path.of(args[0]).toRealPath(),allowed=Path.of("acceptance-results/ai-long").toRealPath();if(!out.startsWith(allowed)||out.equals(allowed))throw new IllegalArgumentException("Synthetic directory required");
        new SpringApplicationBuilder(PlanPlatformApplication.class,AiLongPlanningFixture.class).run(
            "--spring.profiles.active=acceptance","--server.address=127.0.0.1","--server.port=43174",
            "--app.cors-allowed-origins=http://127.0.0.1:43130,http://localhost:43130,tauri://localhost,http://tauri.localhost",
            "--spring.datasource.url=jdbc:h2:file:"+out.resolve("synthetic-db").toString().replace('\\','/')+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0",
            "--spring.datasource.driver-class-name=org.h2.Driver","--spring.datasource.username=sa","--spring.datasource.password=",
            "--app.security.registration-enabled=true","--app.security.email-verification-enabled=false","--app.security.refresh-cookie-secure=false","--app.security.refresh-cookie-name=plan_refresh","--app.security.refresh-cookie-same-site=Strict","--app.security.bootstrap-admin-email=",
            "--app.jwt.secret=synthetic-long-planning-only-at-least-32-bytes","--app.jwt.issuer=synthetic-long-planning",
            "--app.ai.enabled=true","--app.ai.api-key-file=/synthetic-unused","--app.ai.repair-enabled=true","--app.ai.worker-enabled=true","--app.operations.enabled=false","--app.governance.enabled=false","--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration");
    }
}
