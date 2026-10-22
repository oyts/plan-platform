package com.oyts.planplatform.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRecyclingTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static ObjectNode fixture() throws Exception {
        return (ObjectNode)JSON.readTree("""
          {"schemaVersion":6,"planVersion":"1.10.1","progress":{},"reflections":{},
           "workspace":{"customPlans":[{"id":"live","name":"合成在用计划","startDate":"2026-10-01","endDate":"2026-10-31",
             "tags":[{"id":"keep","name":"保留标签","color":"#002FA7"}],
             "tasks":[{"id":"live-task","title":"合成在用任务","date":"2026-10-02","tagId":"keep","plannedMinutes":30}]}],
             "customMainlines":[{"id":"live-route","name":"合成路线","items":[{"id":"live-step","title":"在用步骤","plannedMinutes":20}]}],
             "trash":[
              {"id":"trash:plan:removed","type":"plan","deletedAt":"2026-10-20T01:00:00Z","payload":{
                "plan":{"id":"removed","name":"合成已删除计划","startDate":"2026-10-01","endDate":"2026-10-31","tags":[],
                  "tasks":[{"id":"removed-task","title":"历史任务","date":"2026-10-02","plannedMinutes":30}]},
                "progress":{"removed-task":{"status":"completed","accumulatedSeconds":120,"completedAt":"2026-10-02T09:00:00Z"}},"reflections":{}}},
              {"id":"trash:mainline:removed-route","type":"mainline","deletedAt":"2026-10-20T02:00:00Z","payload":{
                "track":{"id":"removed-route","name":"合成已删除路线","items":[{"id":"removed-route-step","title":"历史路线步骤","plannedMinutes":20}]},"progress":{}}},
              {"id":"trash:mainline-item:removed-step","type":"mainline-item","deletedAt":"2026-10-20T03:00:00Z","payload":{
                "mainlineId":"live-route","index":0,"task":{"id":"removed-step","title":"删除步骤","plannedMinutes":10},
                "progress":{"status":"running","accumulatedSeconds":60,"startedAt":"2026-10-19T00:00:00Z"}}},
              {"id":"trash:tag:removed-tag","type":"tag","deletedAt":"2026-10-20T04:00:00Z","payload":{
                "planId":"live","index":0,"tag":{"id":"removed-tag","name":"已删除标签","color":"#FF0000"},"reassignedTaskIds":["live-task"],"replacementTagId":"keep"}}
             ],"deletionMarkers":[],"taskTemplates":[]}}
          """);
    }
    @Test void convertsAllFourSnapshotTypesWithoutMutatingOriginal() throws Exception {
        ObjectNode original=fixture();String before=original.toString();var value=LegacyRecycling.convert(original);
        assertEquals(before,original.toString());assertEquals(4,value.deleted().size());assertEquals(0,value.markersOnly());
        assertEquals(2,value.payload().path("workspace").path("customPlans").size());
        assertEquals(2,value.payload().path("workspace").path("customMainlines").size());
        assertEquals(2,value.payload().at("/workspace/customMainlines/0/items").size());
        assertEquals("removed-step",value.payload().at("/workspace/customMainlines/0/items/0/id").asText());
        assertEquals("completed",value.payload().at("/progress/removed-task/status").asText());
        assertEquals(Instant.parse("2026-10-20T04:00:00Z"),value.deletion("tag","live","removed-tag"));
    }
    @Test void markerWithoutSnapshotSuppressesStaleActiveCopyInsteadOfInventingBody() throws Exception {
        ObjectNode root=fixture();((ArrayNode)root.at("/workspace/trash")).removeAll();
        ((ArrayNode)root.at("/workspace/deletionMarkers")).add(JSON.readTree("""
          {"id":"trash:plan:live","type":"plan","entityId":"live","parentId":null,"replacementId":null,"deletedAt":"2026-10-20T00:00:00Z"}
          """));
        var result=LegacyRecycling.convert(root);assertEquals(0,result.payload().at("/workspace/customPlans").size());
        assertEquals(1,result.markersOnly());assertTrue(result.deleted().isEmpty());
    }
    @Test void permanentTagUsesExplicitReplacementAndNeverDropsRelationshipSilently() throws Exception {
        ObjectNode root=fixture();((ArrayNode)root.at("/workspace/trash")).removeAll();
        ((ObjectNode)root.at("/workspace/customPlans/0/tasks/0")).put("tagId","gone");
        ((ArrayNode)root.at("/workspace/deletionMarkers")).add(JSON.readTree("""
          {"id":"trash:tag:gone","type":"tag","entityId":"gone","parentId":"live","replacementId":"keep","deletedAt":"2026-10-20T00:00:00Z"}
          """));
        assertEquals("keep",LegacyRecycling.convert(root).payload().at("/workspace/customPlans/0/tasks/0/tagId").asText());
        ((ObjectNode)root.at("/workspace/deletionMarkers/0")).putNull("replacementId");
        assertThrows(IllegalArgumentException.class,()->LegacyRecycling.convert(root));
    }
    @Test void refusesUnknownTypeDuplicateIdWrongParentAndMissingParent() throws Exception {
        for(String mutation:new String[]{"type","duplicate","parent","missing"}){
            ObjectNode root=fixture();ArrayNode entries=(ArrayNode)root.at("/workspace/trash");
            switch(mutation){
                case "type" -> ((ObjectNode)entries.get(0)).put("type","other");
                case "duplicate" -> entries.add(entries.get(0).deepCopy());
                case "parent" -> ((ArrayNode)root.at("/workspace/deletionMarkers")).add(JSON.readTree("""
                  {"id":"trash:mainline-item:removed-step","type":"mainline-item","entityId":"removed-step","parentId":"other","deletedAt":"2026-10-20T00:00:00Z"}
                  """));
                default -> ((ObjectNode)entries.get(2).path("payload")).put("mainlineId","missing");
            }
            assertThrows(IllegalArgumentException.class,()->LegacyRecycling.convert(root),mutation);
        }
    }
    @Test void refusesDuplicateTaskIdentifiersAcrossSnapshotsAndInvalidDates() throws Exception {
        ObjectNode root=fixture();((ObjectNode)root.at("/workspace/trash/0/payload/plan/tasks/0")).put("id","live-step");
        assertThrows(IllegalArgumentException.class,()->LegacyRecycling.convert(root));
        ObjectNode other=fixture();((ObjectNode)other.at("/workspace/trash/0")).put("deletedAt","not-a-date");
        assertThrows(IllegalArgumentException.class,()->LegacyRecycling.convert(other));
        ((ObjectNode)other.at("/workspace/trash/0")).put("deletedAt","2040-01-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class,()->LegacyRecycling.convert(other));
    }
    @Test void boundsRecycleProcessing() throws Exception {
        ObjectNode root=fixture();ArrayNode entries=(ArrayNode)root.at("/workspace/trash");JsonNodeFactory factory=JsonNodeFactory.instance;
        while(entries.size()<1001)entries.add(factory.objectNode());
        assertThrows(IllegalArgumentException.class,()->LegacyRecycling.convert(root));
    }
    @Test void pairedMarkerDoesNotDuplicateSnapshotAndRejectsContradictoryDeletionTime() throws Exception {
        ObjectNode root=fixture();ArrayNode markers=(ArrayNode)root.at("/workspace/deletionMarkers");
        markers.add(JSON.readTree("""
          {"id":"trash:plan:removed","type":"plan","entityId":"removed","parentId":null,"replacementId":null,"deletedAt":"2026-10-20T01:00:00Z"}
          """));
        var result=LegacyRecycling.convert(root);assertEquals(4,result.deleted().size());assertEquals(0,result.markersOnly());
        assertEquals(2,result.payload().at("/workspace/customPlans").size());
        ((ObjectNode)markers.get(0)).put("deletedAt","2026-10-21T01:00:00Z");
        assertThrows(IllegalArgumentException.class,()->LegacyRecycling.convert(root));
    }
}
