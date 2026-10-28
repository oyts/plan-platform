package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.auth.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreFreezeFixtureResumeTest {
    @TempDir Path out;
    final ObjectMapper json=new ObjectMapper();
    final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
    final UserAccountRepository users=mock(UserAccountRepository.class);
    Map<String,Object> seed()throws Exception{
        var rows=new LinkedHashMap<String,Object>();
        for(String role:List.of("admin","member")){
            String email="core-freeze-"+role+"-"+UUID.randomUUID()+"@example.test";
            var user=new UserAccount(email,encoder.encode("synthetic-resume-only"),"合成");if(role.equals("admin"))user.makeAdmin();
            org.springframework.test.util.ReflectionTestUtils.setField(user,"id",UUID.randomUUID().toString());
            when(users.findById(user.getId())).thenReturn(Optional.of(user));
            rows.put(role,Map.of("email",email,"id",user.getId(),"password","synthetic-resume-only"));
        }
        Files.writeString(out.resolve("synthetic-login.json"),json.writeValueAsString(rows));return rows;
    }
    @Test void resumesExactOriginalIdentitiesWithoutChangingCredentialsOrUsers()throws Exception{
        seed();String before=Files.readString(out.resolve("synthetic-login.json"));
        CoreFreezeFixture.verifyResume(users,encoder,json,out);
        assertEquals(before,Files.readString(out.resolve("synthetic-login.json")));
        verify(users,never()).save(any());verify(users,never()).saveAndFlush(any());
    }
    @Test void refusesMissingUsersRatherThanCreatingReplacementAccounts()throws Exception{
        seed();when(users.findById(anyString())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,()->CoreFreezeFixture.verifyResume(users,encoder,json,out));
        verify(users,never()).saveAndFlush(any());
    }
    @Test void refusesChangedCredentialOrNonSyntheticEmail()throws Exception{
        seed();var original=json.readTree(Files.readString(out.resolve("synthetic-login.json")));
        ((com.fasterxml.jackson.databind.node.ObjectNode)original.path("member")).put("password","wrong");
        Files.writeString(out.resolve("synthetic-login.json"),json.writeValueAsString(original));
        assertThrows(IllegalStateException.class,()->CoreFreezeFixture.verifyResume(users,encoder,json,out));
        ((com.fasterxml.jackson.databind.node.ObjectNode)original.path("admin")).put("email","not-a-synthetic-account@example.test");
        Files.writeString(out.resolve("synthetic-login.json"),json.writeValueAsString(original));
        assertThrows(IllegalStateException.class,()->CoreFreezeFixture.verifyResume(users,encoder,json,out));
    }
}
