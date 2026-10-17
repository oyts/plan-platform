package com.oyts.planplatform;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.security.registration-enabled=true") @AutoConfigureMockMvc
class FeedbackIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate jdbc;
    record Account(String token,String id,String email){}
    private Account account(boolean admin)throws Exception{
        String email="feedback-"+UUID.randomUUID()+"@example.test";
        var result=send(post("/api/auth/register"),null,Map.of("email",email,"password","FeedbackTest123!","displayName","反馈验收"),201);
        String id=result.path("user").path("id").asText();
        if(admin){jdbc.update("update users set role='ADMIN' where id=?",id);result=send(post("/api/auth/login"),null,Map.of("email",email,"password","FeedbackTest123!"),200);}
        return new Account(result.path("accessToken").asText(),id,email);
    }
    private JsonNode send(MockHttpServletRequestBuilder request,Account account,Object body,int code)throws Exception{
        if(account!=null)request.header("Authorization","Bearer "+account.token());
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        var response=mvc.perform(request).andExpect(status().is(code)).andReturn().getResponse();
        return response.getContentAsString().isBlank()?mapper.nullNode():mapper.readTree(response.getContentAsString());
    }
    private Map<String,Object> body(){return new HashMap<>(Map.of("operationKey",UUID.randomUUID().toString(),"category","USABILITY","title","教学提示建议","steps","打开使用教学，选择日期。","expectedResult","显示明确的天数。","actualResult","不清楚是否包含首日。"));}
    private Map<String,Object> diagnostic(){return new HashMap<>(Map.of("schemaVersion",1,"clientVersion","0.1.14","platform","ANDROID","occurredAt","2026-10-18T00:00:00Z","errorKind","CONFLICT","requestId",UUID.randomUUID().toString(),"queueCount",2,"conflictCount",1));}
    @Test void ownerIsolationAdminPermissionAndPrivateResponse()throws Exception{
        var a=account(false);var b=account(false);var admin=account(true);
        var request=body();request.put("diagnostic",diagnostic());var created=send(post("/api/feedback"),a,request,201);String id=created.path("ticket").path("id").asText();
        assertThat(created.path("diagnostic").path("queueCount").asInt()).isEqualTo(2);
        send(get("/api/feedback/"+id),b,null,404);send(post("/api/feedback/"+id+"/replies"),b,Map.of("body","不能读取","version",0),404);
        assertThat(send(get("/api/feedback"),b,null,200).path("totalElements").asInt()).isZero();
        send(get("/api/admin/feedback"),a,null,403);send(put("/api/admin/feedback/"+id),a,Map.of("status","CLOSED","body","无权限","version",0),403);
        assertThat(send(get("/api/admin/feedback/"+id),admin,null,200).path("ticket").path("id").asText()).isEqualTo(id);
        mvc.perform(get("/api/feedback/"+id).header("Authorization","Bearer "+a.token())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void durableCreationReplayAndChangedPayloadConflict()throws Exception{
        var a=account(false);var body=body();var first=send(post("/api/feedback"),a,body,201);
        assertThat(send(post("/api/feedback"),a,body,201).path("ticket").path("id")).isEqualTo(first.path("ticket").path("id"));
        body.put("title","不同内容");send(post("/api/feedback"),a,body,409);
        assertThat(send(get("/api/feedback"),a,null,200).path("totalElements").asInt()).isEqualTo(1);
    }
    @Test void repliesAdvanceOneVersionProtectConflictsAndReopen()throws Exception{
        var a=account(false);var admin=account(true);var created=send(post("/api/feedback"),a,body(),201);String id=created.path("ticket").path("id").asText();
        var handled=send(put("/api/admin/feedback/"+id),admin,Map.of("status","NEEDS_INFO","body","请补充日期范围。","version",0),200);
        assertThat(handled.path("ticket").path("version").asLong()).isEqualTo(1);
        send(post("/api/feedback/"+id+"/replies"),a,Map.of("body","过期输入","version",0),409);
        var replied=send(post("/api/feedback/"+id+"/replies"),a,Map.of("body","已补充合成日期。","version",1),200);
        assertThat(replied.path("ticket").path("status").asText()).isEqualTo("OPEN");assertThat(replied.path("ticket").path("version").asLong()).isEqualTo(2);
        // Same status must still increment once when only the message changes.
        var more=send(post("/api/feedback/"+id+"/replies"),a,Map.of("body","进一步说明。","version",2),200);
        assertThat(more.path("ticket").path("version").asLong()).isEqualTo(3);assertThat(more.path("messages").size()).isEqualTo(3);
        send(put("/api/admin/feedback/"+id),admin,Map.of("status","RESOLVED","body","已处理。","version",3),200);
        assertThat(send(post("/api/feedback/"+id+"/replies"),a,Map.of("body","仍有问题。","version",4),200).path("ticket").path("status").asText()).isEqualTo("OPEN");
    }
    @Test void diagnosticWhitelistAndBoundedInputs()throws Exception{
        var a=account(false);var b=body();var d=diagnostic();d.put("token","never-store-this");b.put("diagnostic",d);send(post("/api/feedback"),a,b,400);
        d.remove("token");d.put("requestId","http://192.168.1.1/private");send(post("/api/feedback"),a,b,400);
        d.remove("requestId");d.put("conflictCount",3);send(post("/api/feedback"),a,b,400);
        d.put("conflictCount",1);d.put("queueCount",2.5);send(post("/api/feedback"),a,b,400);
        b.remove("diagnostic");b.put("title","x".repeat(121));send(post("/api/feedback"),a,b,400);
        assertThat(jdbc.queryForObject("select count(*) from feedback_tickets where owner_id=?",Long.class,a.id())).isZero();
    }
    @Test void dailyLimitPaginationAndReplayBeforeLimit()throws Exception{
        var a=account(false);Map<String,Object> first=null;
        for(int i=0;i<10;i++){var data=body();if(i==0)first=data;send(post("/api/feedback"),a,data,201);}
        send(post("/api/feedback"),a,body(),429);send(post("/api/feedback"),a,first,201);
        var page=send(get("/api/feedback?size=3&page=1"),a,null,200);assertThat(page.path("content").size()).isEqualTo(3);assertThat(page.path("totalPages").asInt()).isEqualTo(4);
        send(get("/api/feedback?size=51"),a,null,400);send(get("/api/feedback?status=INVALID"),a,null,400);
    }
    @Test void explanationsRequiredAndNoPartialMessageOnStaleVersion()throws Exception{
        var a=account(false);var admin=account(true);String id=send(post("/api/feedback"),a,body(),201).path("ticket").path("id").asText();
        send(put("/api/admin/feedback/"+id),admin,Map.of("status","CLOSED","body"," ","version",0),400);
        send(put("/api/admin/feedback/"+id),admin,Map.of("status","IN_PROGRESS","body","已查看。","version",1),409);
        var result=send(get("/api/feedback/"+id),a,null,200);assertThat(result.path("messages").size()).isZero();assertThat(result.path("ticket").path("version").asLong()).isZero();
    }
    @Test void opaqueRequestIdsAndCorsAreAvailableWithoutEchoingPrivateHeader()throws Exception{
        String id=UUID.randomUUID().toString();mvc.perform(get("/api/health").header("X-Request-ID",id)).andExpect(status().isOk()).andExpect(header().string("X-Request-ID",id));
        var response=mvc.perform(get("/api/health").header("X-Request-ID","secret-private-url")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("X-Request-ID")).matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");
        mvc.perform(options("/api/feedback").header("Origin","http://localhost:43128").header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","content-type,x-request-id"))
            .andExpect(status().isOk()).andExpect(header().exists("Access-Control-Allow-Headers"));
        send(get("/api/feedback"),null,null,401);
    }
}
