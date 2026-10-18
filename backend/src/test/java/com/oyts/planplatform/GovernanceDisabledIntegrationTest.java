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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"app.security.registration-enabled=true","app.governance.enabled=false","app.operations.enabled=false"}) @AutoConfigureMockMvc
class GovernanceDisabledIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate db;
    @Test void defaultSwitchIsClosedAndTurningItOffDoesNotRevivePreviouslyGovernedTokens()throws Exception{
        String email="closed-governance-"+UUID.randomUUID()+"@example.test",password="GovernanceTest123!";
        var response=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email",email,"password",password,"displayName","关闭开关验收")))).andExpect(status().isCreated()).andReturn().getResponse();var account=mapper.readTree(response.getContentAsString());String id=account.path("user").path("id").asText(),old=account.path("accessToken").asText();
        db.update("update users set role='ADMIN' where id=?",id);var login=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email",email,"password",password)))).andExpect(status().isOk()).andReturn().getResponse();String admin=mapper.readTree(login.getContentAsString()).path("accessToken").asText();
        mvc.perform(get("/api/admin/governance/config").header("Authorization","Bearer "+admin)).andExpect(jsonPath("$.enabled").value(false));mvc.perform(get("/api/admin/operations/status").header("Authorization","Bearer "+admin)).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.dataAvailable").value(false));
        mvc.perform(put("/api/admin/governance/members/"+id).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"version\":0,\"reason\":\"合成关闭测试\"}")).andExpect(status().isConflict());
        db.update("update users set enabled=false,auth_generation=1 where id=?",id);mvc.perform(get("/api/plans").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());db.update("update users set enabled=true,auth_generation=2 where id=?",id);mvc.perform(get("/api/plans").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());assertThat(db.queryForObject("select count(*) from member_governance_audit where target_id=?",Long.class,id)).isZero();
    }
}
