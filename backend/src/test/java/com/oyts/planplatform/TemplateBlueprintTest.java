package com.oyts.planplatform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.oyts.planplatform.organize.TemplateBlueprint;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TemplateBlueprintTest {
 private final ObjectMapper json=new ObjectMapper();
 private ObjectNode route(){
  ObjectNode root=json.createObjectNode().put("schemaVersion",1).put("kind","ROUTE");
  root.putObject("parent").put("id",UUID.randomUUID().toString()).put("name","合成方法").put("description","").put("color","#002FA7").put("execution_mode","FLEXIBLE");
  ObjectNode rows=root.putObject("rows");rows.putArray("mainline_stages");ArrayNode items=rows.putArray("mainline_items");
  for(int i=0;i<2;i++)items.addObject().put("id",UUID.randomUUID().toString()).put("title","步骤"+i).put("description","").put("acceptance_json","[]").put("planned_minutes",15).put("sort_order",i).putNull("stage_id").put("dependency_mode","EXPLICIT").put("prerequisite_ids_json","[]");
  return root;
 }
 private ObjectNode item(ObjectNode root,int i){return (ObjectNode)root.path("rows").path("mainline_items").get(i);}
 @Test void rejectsCyclesForeignReferencesAndStageReferences(){
  var r=route();String a=item(r,0).path("id").asText(),b=item(r,1).path("id").asText();
  item(r,0).put("prerequisite_ids_json","[\""+b+"\"]");item(r,1).put("prerequisite_ids_json","[\""+a+"\"]");
  assertThatIllegalArgumentException().isThrownBy(()->TemplateBlueprint.validate(r,"ROUTE"));
  item(r,0).put("prerequisite_ids_json","[]");item(r,1).put("prerequisite_ids_json","[\""+UUID.randomUUID()+"\"]");
  assertThatIllegalArgumentException().isThrownBy(()->TemplateBlueprint.validate(r,"ROUTE"));
  item(r,1).put("prerequisite_ids_json","[]").put("stage_id",UUID.randomUUID().toString());
  assertThatIllegalArgumentException().isThrownBy(()->TemplateBlueprint.validate(r,"ROUTE"));
 }
 @Test void rejectsPrivateFieldsFractionalMinutesAndRepeatedIds(){
  var r=route();assertThatCode(()->TemplateBlueprint.validate(r,"ROUTE")).doesNotThrowAnyException();
  item(r,0).put("note","私人执行记录");assertThatIllegalArgumentException().isThrownBy(()->TemplateBlueprint.validate(r,"ROUTE"));item(r,0).remove("note");
  item(r,0).put("planned_minutes",1.5);assertThatIllegalArgumentException().isThrownBy(()->TemplateBlueprint.validate(r,"ROUTE"));item(r,0).put("planned_minutes",15);
  item(r,1).put("id",item(r,0).path("id").asText());assertThatIllegalArgumentException().isThrownBy(()->TemplateBlueprint.validate(r,"ROUTE"));
 }
}
