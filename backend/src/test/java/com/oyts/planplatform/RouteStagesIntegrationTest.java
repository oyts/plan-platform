package com.oyts.planplatform;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc
class RouteStagesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private String account() throws Exception {
        return call("", "POST", "/auth/register", Map.of("email", UUID.randomUUID()+"@example.test", "password", "RouteAcceptance123!", "displayName", "路线验收"), 201).path("accessToken").asText();
    }
    private JsonNode call(String token, String method, String path, Object data, int expected) throws Exception {
        var request = switch(method) { case "POST" -> post("/api"+path); case "PUT" -> put("/api"+path); case "DELETE" -> delete("/api"+path); default -> get("/api"+path); };
        if (!token.isBlank()) request.header("Authorization", "Bearer "+token);
        if (data != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data));
        String body = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? json.nullNode() : json.readTree(body);
    }
    private JsonNode line(String t, String mode) throws Exception {
        var body = new HashMap<String,Object>(Map.of("name", "吉他路线", "description", "", "color", "#002FA7")); if (mode != null) body.put("executionMode",mode);
        return call(t,"POST","/mainlines",body,201);
    }
    private JsonNode step(String t, JsonNode line, int order, Object routing) throws Exception {
        var body = new HashMap<String,Object>(Map.of("title","步骤"+order,"description","","acceptance",List.of("练习"),"plannedMinutes",20,"sortOrder",order));
        if (routing != null) body.put("routing",routing);
        return call(t,"POST","/mainlines/"+line.path("id").asText()+"/items",body,201);
    }
    private Object routing(String stage, String mode, String... ids) { var map = new HashMap<String,Object>(); map.put("stageId",stage); map.put("dependencyMode",mode); map.put("prerequisiteIds",List.of(ids)); return map; }
    private JsonNode update(String t, JsonNode item, Object routing, int expected) throws Exception {
        ObjectNode body = json.createObjectNode();
        for (String field : List.of("title","description","acceptance","plannedMinutes","sortOrder","version")) body.set(field,item.get(field));
        if (routing != null) body.set("routing",json.valueToTree(routing));
        return call(t,"PUT","/mainlines/items/"+item.path("id").asText(),body,expected);
    }
    @Test void legacyOrderingAndFlexibleRoutesAreIndependent() throws Exception {
        String t = account(); JsonNode old = line(t,null); assertEquals("SEQUENTIAL",old.path("executionMode").asText());
        JsonNode a = step(t,old,0,null), b = step(t,old,1,null); assertFalse(b.path("unlocked").asBoolean());
        call(t,"POST","/mainlines/items/"+b.path("id").asText()+"/complete",Map.of("version",0),409);
        call(t,"POST","/mainlines/items/"+a.path("id").asText()+"/complete",Map.of("version",0),200);
        call(t,"POST","/mainlines/items/"+b.path("id").asText()+"/complete",Map.of("version",0),200);
        JsonNode flexible = line(t,"FLEXIBLE"); step(t,flexible,0,null); JsonNode later = step(t,flexible,1,null);
        assertTrue(later.path("unlocked").asBoolean());
        call(t,"POST","/mainlines/items/"+later.path("id").asText()+"/complete",Map.of("version",0),200);
    }
    @Test void dependenciesValidateIdentityCyclesAndStaleVersions() throws Exception {
        String t = account(), stranger = account(); JsonNode l = line(t,"FLEXIBLE"), foreign = line(stranger,"FLEXIBLE");
        JsonNode a = step(t,l,0,null), b = step(t,l,1,null), outside = step(stranger,foreign,0,null);
        update(t,a,routing(null,"EXPLICIT",a.path("id").asText()),400);
        update(t,a,routing(null,"EXPLICIT",outside.path("id").asText()),404);
        JsonNode anotherLine = line(t,"FLEXIBLE"), cross = step(t,anotherLine,0,null);
        update(t,a,routing(null,"EXPLICIT",cross.path("id").asText()),404);
        JsonNode dependent = update(t,b,routing(null,"EXPLICIT",a.path("id").asText()),200); assertFalse(dependent.path("unlocked").asBoolean());
        update(t,a,routing(null,"EXPLICIT",b.path("id").asText()),400);
        update(t,b,routing(null,"NONE"),409);
        call(stranger,"PUT","/mainlines/items/"+a.path("id").asText(),a,404);
        call(t,"DELETE","/mainlines/items/"+a.path("id").asText(),null,409);
        // An old client updating the content cannot erase the explicit dependency.
        dependent = update(t,dependent,null,200); assertEquals("EXPLICIT",dependent.path("dependencyMode").asText());
        call(t,"POST","/mainlines/items/"+dependent.path("id").asText()+"/start?version="+dependent.path("version").asLong(),null,409);
        call(t,"POST","/mainlines/items/"+a.path("id").asText()+"/complete",Map.of("version",0),200);
        call(t,"POST","/mainlines/items/"+dependent.path("id").asText()+"/start?version="+dependent.path("version").asLong(),null,200);
    }
    @Test void stagesCanBeEditedAndRemovedWithoutDeletingSteps() throws Exception {
        String t = account(), stranger = account(); JsonNode l = line(t,"FLEXIBLE");
        JsonNode stage = call(t,"POST","/mainlines/"+l.path("id").asText()+"/stages",Map.of("name","认识吉他","description","基础","sortOrder",0),201);
        call(stranger,"PUT","/mainlines/stages/"+stage.path("id").asText(),Map.of("name","越权","sortOrder",0),404);
        JsonNode step = step(t,l,0,routing(stage.path("id").asText(),"INHERIT"));
        JsonNode renamed = call(t,"PUT","/mainlines/stages/"+stage.path("id").asText(),Map.of("name","基础知识","sortOrder",0,"version",0),200);
        assertEquals(1,renamed.path("version").asLong());
        call(t,"DELETE","/mainlines/stages/"+stage.path("id").asText()+"?version=0",null,409);
        call(t,"DELETE","/mainlines/items/"+step.path("id").asText(),null,204);
        call(t,"DELETE","/mainlines/stages/"+stage.path("id").asText()+"?version=1",null,204);
        call(t,"POST","/trash/MAINLINE_ITEM/"+step.path("id").asText()+"/restore",null,204);
        JsonNode refreshed = call(t,"GET","/mainlines",null,200).get(0);
        assertEquals(0,refreshed.path("stages").size()); assertEquals(1,refreshed.path("items").size()); assertTrue(refreshed.path("items").get(0).path("stageId").isNull());
        assertEquals(1,refreshed.path("nextSortOrder").asInt());
        step(t,l,1,null);
    }
    @Test void switchingToSequentialCannotIntroduceCyclesOrLockRunningSteps() throws Exception {
        String t = account(); JsonNode l = line(t,"FLEXIBLE"), a = step(t,l,0,null), b = step(t,l,1,null);
        a = update(t,a,routing(null,"EXPLICIT",b.path("id").asText()),200);
        call(t,"PUT","/mainlines/"+l.path("id").asText(),Map.of("name","路线","color","#002FA7","version",0,"executionMode","SEQUENTIAL"),400);
        a = update(t,a,routing(null,"NONE"),200);
        JsonNode running = call(t,"POST","/mainlines/items/"+b.path("id").asText()+"/start?version=0",null,200);
        call(t,"PUT","/mainlines/"+l.path("id").asText(),Map.of("name","路线","color","#002FA7","version",0,"executionMode","SEQUENTIAL"),409);
        call(t,"POST","/mainlines/items/"+b.path("id").asText()+"/start?version="+running.path("version").asLong(),null,409);
        call(t,"POST","/mainlines/items/"+b.path("id").asText()+"/pause?version="+running.path("version").asLong(),null,200);
        call(t,"PUT","/mainlines/"+l.path("id").asText(),Map.of("name","路线","color","#002FA7","version",0,"executionMode","SEQUENTIAL"),200);
    }
    @Test void simultaneousOppositeDependenciesCannotBothBeCommitted() throws Exception {
        String t = account(); JsonNode l = line(t,"FLEXIBLE"), a = step(t,l,0,null), b = step(t,l,1,null);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var go = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Future<Integer>> outcomes = new ArrayList<>();
            for (JsonNode item : List.of(a,b)) outcomes.add(pool.submit(() -> {
                ready.countDown(); go.await(); ObjectNode body = json.createObjectNode();
                for (String field : List.of("title","description","acceptance","plannedMinutes","sortOrder","version")) body.set(field,item.get(field));
                body.set("routing",json.valueToTree(routing(null,"EXPLICIT",item==a?b.path("id").asText():a.path("id").asText())));
                return mvc.perform(put("/api/mainlines/items/"+item.path("id").asText()).header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();
            }));
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)); go.countDown();
            var statuses = new ArrayList<Integer>(); for(var outcome:outcomes) statuses.add(outcome.get(15,java.util.concurrent.TimeUnit.SECONDS));
            Collections.sort(statuses); assertEquals(List.of(200,400),statuses);
        }
    }
}
