package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.PlanPlatformApplication;
import com.oyts.planplatform.auth.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;

/** Test classpath only. Loopback H2, fake AI, no mail, graceful parent EOF shutdown. */
@TestConfiguration
public class CoreFreezeFixture {
    @Bean @Primary AiProvider syntheticProvider(ObjectMapper json){return request->{
        try{
            var context=json.readTree(request.message());String mode=context.path("selectedMode").asText("TODO");
            if(context.path("selectedMode").isNull()||"DIALOGUE".equals(context.path("requestIntent").asText()))
                return new AiProvider.Result("{\"message\":\"这是本机合成规划讨论，未调用真实模型。\",\"recommendedMode\":\"TODO\",\"draft\":null}",100,100);
            var type=AiDrafts.Mode.valueOf(mode);LocalDate day=LocalDate.parse(context.path("today").asText());
            var draft=new AiDrafts.Draft(1,type,0,"核心冻结合成规划",type==AiDrafts.Mode.PLAN?day:null,type==AiDrafts.Mode.PLAN?day.plusDays(6):null,List.of(),List.of(),
                List.of(new AiDrafts.Item("synthetic_one","仅用于验收的合成步骤","本地假上游",type==AiDrafts.Mode.ROUTE?null:day,type==AiDrafts.Mode.TODO?0:20,null,List.of())));
            return new AiProvider.Result(json.writeValueAsString(Map.of("message","合成草稿已准备，仍需明确确认。","recommendedMode",mode,"draft",draft)),100,200);
        }catch(Exception invalid){throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,false);}
    };}
    @Bean org.springframework.boot.ApplicationRunner seed(UserAccountRepository users,org.springframework.security.crypto.password.PasswordEncoder encoder,ObjectMapper json){return args->{
        Path out=Path.of(System.getProperty("plan.core.fixture.directory"));var values=new LinkedHashMap<String,Object>();
        if(Boolean.getBoolean("plan.core.fixture.resume")){verifyResume(users,encoder,json,out);return;}
        for(String role:List.of("admin","member")){String email="core-freeze-"+role+"-"+UUID.randomUUID()+"@example.test",password="Synthetic-"+UUID.randomUUID()+"!";var u=new UserAccount(email,encoder.encode(password),"核心冻结合成"+role);if(role.equals("admin"))u.makeAdmin();users.saveAndFlush(u);values.put(role,Map.of("email",email,"password",password,"id",u.getId()));}
        Files.writeString(out.resolve("synthetic-login.json"),json.writeValueAsString(values),StandardOpenOption.CREATE_NEW);
    };}
    static void verifyResume(UserAccountRepository users,org.springframework.security.crypto.password.PasswordEncoder encoder,ObjectMapper json,Path out)throws Exception{
        var original=json.readTree(Files.readString(out.resolve("synthetic-login.json")));
        for(String role:List.of("admin","member")){
            var row=original.path(role);String email=row.path("email").asText();
            if(!email.matches("core-freeze-"+role+"-[a-f0-9-]+@example\\.test"))throw new IllegalStateException("Retained synthetic identity required");
            var user=users.findById(row.path("id").asText()).orElseThrow(()->new IllegalStateException("Retained synthetic user missing"));
            if(!email.equals(user.getEmail())||!(role.equals("admin")?"ADMIN":"USER").equals(user.getRole())||!encoder.matches(row.path("password").asText(),user.getPasswordHash()))throw new IllegalStateException("Retained synthetic identity mismatch");
        }
    }
    public static void main(String[]args)throws Exception{
        boolean resume=args.length==3&&args[2].equals("--resume");
        if((args.length!=2&&!resume)||!args[1].matches("\\d+\\.\\d+\\.\\d+"))throw new IllegalArgumentException("Isolated directory and version required");
        Path out=Path.of(args[0]).toRealPath(),base=Path.of("acceptance-results/core-freeze").toRealPath();if(!out.startsWith(base)||out.equals(base))throw new IllegalArgumentException("Core-freeze directory required");
        boolean credentials=Files.isRegularFile(out.resolve("synthetic-login.json")),database=Files.isRegularFile(out.resolve("synthetic-db.mv.db"));
        if(resume?!(credentials&&database):(credentials||database))throw new IllegalArgumentException("Explicit resume of a retained complete fixture required");
        System.setProperty("plan.core.fixture.resume",Boolean.toString(resume));
        System.setProperty("plan.core.fixture.directory",out.toString());var context=new AtomicReference<ConfigurableApplicationContext>();
        Thread parent=new Thread(()->{try{while(System.in.read()!=-1){}var c=context.get();if(c!=null)c.close();else System.exit(0);}catch(java.io.IOException ended){System.exit(0);}},"synthetic-parent-lifetime");parent.setDaemon(true);parent.start();
        context.set(new SpringApplicationBuilder(PlanPlatformApplication.class,CoreFreezeFixture.class).run(
            "--server.address=127.0.0.1","--server.port=43131","--spring.profiles.active=acceptance","--app.version="+args[1],
            "--spring.datasource.url=jdbc:h2:file:"+out.resolve("synthetic-db").toString().replace('\\','/')+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0",
            "--spring.datasource.driver-class-name=org.h2.Driver","--spring.datasource.username=sa","--spring.datasource.password=",
            "--app.security.registration-enabled=true","--app.security.email-verification-enabled=false","--app.security.refresh-cookie-secure=false","--app.security.refresh-cookie-name=plan_refresh","--app.security.max-sessions=20","--app.security.bootstrap-admin-email=",
            "--app.jwt.secret=synthetic-core-freeze-only-at-least-32-bytes","--app.jwt.issuer=synthetic-core-freeze",
            "--app.cors-allowed-origins=http://127.0.0.1:43130,http://localhost:43130,tauri://localhost,http://tauri.localhost,http://localhost,https://localhost,capacitor://localhost",
            "--app.ai.enabled=true","--app.ai.api-key-file=/synthetic-unused","--app.ai.worker-enabled=true","--app.operations.enabled=false","--app.governance.enabled=true",
            "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration"));
    }
}
