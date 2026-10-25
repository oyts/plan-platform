package com.oyts.planplatform.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.PlanPlatformApplication;
import com.oyts.planplatform.auth.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Test classpath only, loopback data only; explicitly no SMTP or network AI. */
@TestConfiguration
public class AdminCenterFixture {
    @Bean @Primary AiProvider syntheticProvider(){return request->new AiProvider.Result("{\"message\":\"合成讨论，不创建业务\",\"recommendedMode\":null,\"draft\":null}",100,128);}
    @Bean org.springframework.boot.ApplicationRunner seed(UserAccountRepository users,org.springframework.security.crypto.password.PasswordEncoder passwords,ObjectMapper json){return args->{
        var out=Path.of(System.getProperty("plan.admin.fixture.directory")).toRealPath();var credentials=out.resolve("synthetic-login.json");if(Files.exists(credentials))return;
        var entries=new LinkedHashMap<String,Object>();
        for(String role:List.of("admin","secondAdmin","member")){String email="admin-stage-"+role.toLowerCase()+"-"+UUID.randomUUID()+"@example.test",password="Synthetic-"+UUID.randomUUID()+"!";var u=new UserAccount(email,passwords.encode(password),"管理中心合成"+role);if(!role.equals("member"))u.makeAdmin();users.saveAndFlush(u);entries.put(role,Map.of("email",email,"password",password,"id",u.getId()));}
        Files.writeString(credentials,json.writeValueAsString(entries),StandardOpenOption.CREATE_NEW);
    };}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("An isolated directory required");Path out=Path.of(args[0]).toRealPath(),allowed=Path.of("acceptance-results/admin-center").toRealPath();if(!out.startsWith(allowed)||out.equals(allowed))throw new IllegalArgumentException("Expected isolated test directory");System.setProperty("plan.admin.fixture.directory",out.toString());
        new SpringApplicationBuilder(PlanPlatformApplication.class,AdminCenterFixture.class).run(
            "--server.address=127.0.0.1","--server.port=43180","--spring.profiles.active=acceptance",
            "--spring.datasource.url=jdbc:h2:file:"+out.resolve("synthetic-db").toString().replace('\\','/')+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0",
            "--spring.datasource.driver-class-name=org.h2.Driver","--spring.datasource.username=sa","--spring.datasource.password=",
            "--app.security.registration-enabled=true","--app.security.email-verification-enabled=false","--app.security.refresh-cookie-secure=false","--app.security.refresh-cookie-name=plan_refresh","--app.security.refresh-cookie-same-site=Strict","--app.security.max-sessions=20","--app.security.bootstrap-admin-email=",
            "--app.jwt.secret=synthetic-admin-center-only-at-least-32-bytes","--app.jwt.issuer=synthetic-admin-center",
            "--app.cors-allowed-origins=http://127.0.0.1:43130,http://localhost:43130,tauri://localhost,http://tauri.localhost,http://localhost,https://localhost,capacitor://localhost",
            "--app.ai.enabled=true","--app.ai.api-key-file=/synthetic-unused","--app.ai.worker-enabled=false","--app.ai.repair-enabled=true","--app.operations.enabled=false","--app.governance.enabled=true","--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration");
    }
}
