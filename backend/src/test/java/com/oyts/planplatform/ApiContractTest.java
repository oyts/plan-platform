package com.oyts.planplatform;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.validation.constraints.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.temporal.Temporal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;

/** Route/DTO and reviewed service-delegation drift gate; unresolved dynamic fields remain explicit. */
@SpringBootTest
class ApiContractTest {
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @Autowired ObjectMapper json;
    private final Map<String,JsonNode> schemas=new TreeMap<>();
    private static final Set<String> PUBLIC=Set.of("GET /health","GET /auth/config","POST /auth/register",
        "POST /auth/login","POST /auth/refresh","POST /auth/logout","POST /auth/password-reset/request",
        "POST /auth/password-reset/confirm","POST /auth/email-verification/confirm","POST /auth/email-change/verify","GET /public/analytics/config","POST /public/analytics/ticket","POST /public/analytics/event");

    @Test void springRegexQuantifiersBecomeValidOpenApiPathVariables(){
        assertThat(ApiContractServiceTypes.normalizePath("/ai/saved-drafts/{id:[a-f0-9-]{36}}"))
            .isEqualTo("/ai/saved-drafts/{id}");
        assertThat(ApiContractServiceTypes.normalizePath("/{id:[0-9]{1,5}}/{other}"))
            .isEqualTo("/{id}/{other}");
        assertThat(ApiContractServiceTypes.normalizePath("/plans/{id}" )).isEqualTo("/plans/{id}");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            ()->ApiContractServiceTypes.normalizePath("/{id:[0-9]{2}"));
    }

    @Test void artifactReferencesOperationsAndPathParametersAreConsistent() throws Exception {
        JsonNode artifact=json.readTree(Path.of("../docs/openapi-current.json").toFile());
        Set<String> ids=new HashSet<>();int count=0;
        for(var path:artifact.path("paths").properties())for(var operation:path.getValue().properties()){
            assertThat(path.getKey()).doesNotContain(":");
            count++;assertThat(ids.add(operation.getValue().path("operationId").asText())).as(path.getKey()).isTrue();
            Set<String> actual=new HashSet<>();for(JsonNode p:operation.getValue().path("parameters"))if(p.path("in").asText().equals("path")){
                assertThat(p.path("required").asBoolean()).isTrue();actual.add(p.path("name").asText());
            }
            Set<String> expected=new HashSet<>();var matcher=java.util.regex.Pattern.compile("\\{([^}:]+)(?::[^}]+)?}").matcher(path.getKey());
            while(matcher.find())expected.add(matcher.group(1));assertThat(actual).as(path.getKey()).isEqualTo(expected);
        }
        assertThat(count).isEqualTo(artifact.path("x-operation-count").asInt());checkReferences(artifact,artifact);
    }
    private void checkReferences(JsonNode node,JsonNode root){
        if(node.has("$ref")){String ref=node.path("$ref").asText();assertThat(ref).startsWith("#/");assertThat(root.at(ref.substring(1)).isMissingNode()).as(ref).isFalse();}
        if(node.isContainerNode())node.forEach(child->checkReferences(child,root));
    }

    @Test void normalResponseSchemasAndPrivateBackupVersionsHaveReviewedShapes()throws Exception{
        JsonNode artifact=json.readTree(Path.of("../docs/openapi-current.json").toFile());
        for(var path:artifact.path("paths").properties())for(var op:path.getValue().properties())
            assertReviewed(op.getValue().path("responses"),artifact,new HashSet<>());
        JsonNode input=artifact.path("paths").path("/data/preview").path("post").path("requestBody").path("content").path("application/json").path("schema").path("oneOf");
        assertThat(input.size()).isEqualTo(4);
        for(int v=1;v<=4;v++){
            JsonNode fields=input.get(v-1).path("properties").path("records").path("properties");
            assertThat(fields.has("personal_templates")).isEqualTo(v==4);
            assertThat(fields.has("daily_reviews")).isEqualTo(v>=3);
            assertThat(fields.path("task_progress").path("items").path("properties").has("ever_started")).isEqualTo(v>=2);
        }
        assertThat(artifact.path("paths").path("/ai/saved-drafts/{id}").path("put").path("x-spring-route").asText()).contains("{36}");
    }
    private void assertReviewed(JsonNode node,JsonNode artifact,Set<String> seen){
        assertThat(node.has("x-dynamic-schema")).as(node.toString()).isFalse();
        if(node.has("$ref")){String ref=node.path("$ref").asText();if(seen.add(ref))assertReviewed(artifact.at(ref.substring(1)),artifact,seen);}
        if(node.isContainerNode())node.forEach(child->assertReviewed(child,artifact,seen));
    }

    @Test void legacyRequestsDeclareIntentionalToleranceAndSixCanonicalVersions()throws Exception{
        JsonNode artifact=json.readTree(Path.of("../docs/openapi-current.json").toFile());
        for(String path:List.of("/migrations/legacy/preview","/migrations/legacy/import","/migrations/legacy-snapshot")){
            JsonNode shape=artifact.path("paths").path(path).path("post").path("requestBody").path("content").path("application/json").path("schema");
            assertReviewed(shape,artifact,new HashSet<>());
            assertThat(shape.path("x-intentionally-unconstrained-json").asBoolean()).isTrue();
            assertThat(shape.path("x-canonical-legacy-envelopes").path("$ref").asText()).isEqualTo("#/components/schemas/legacy.CanonicalEnvelopes");
            JsonNode canonical=artifact.at("/components/schemas/legacy.CanonicalEnvelopes");
            JsonNode versions=canonical.at("/allOf/1/oneOf");assertThat(versions.size()).isEqualTo(6);
            for(int v=1;v<=6;v++){
                assertThat(versions.get(v-1).at("/properties/schemaVersion/minimum").asInt()).isEqualTo(v);
                assertThat(versions.get(v-1).path("additionalProperties").asBoolean()).isTrue();
                assertThat(canonical.at("/allOf/0/properties/workspace/properties/trash/maxItems").asInt()).isEqualTo(1000);
            }
        }
    }

    @Test void currentRoutesAndDeclaredDtosMatchTheCheckedInContract() throws Exception {
        // The test profile deliberately has an older fixture version; document the real source metadata.
        var releaseVersion=java.util.regex.Pattern.compile("(?m)^  version: ([0-9]+\\.[0-9]+\\.[0-9]+)\\s*$")
            .matcher(Files.readString(Path.of("src/main/resources/application.yml")));
        assertThat(releaseVersion.find()).isTrue();String sourceVersion=releaseVersion.group(1);
        assertThat(releaseVersion.find()).isFalse();
        ObjectNode contract=json.createObjectNode();contract.put("openapi","3.0.3");
        contract.set("info",json.valueToTree(Map.of("title","计划舱当前源码路由与声明DTO契约","version",sourceVersion+"-source",
            "x-contract-coverage","all-mvc-api-routes-declared-types-and-reviewed-service-delegations; unresolved dynamic fields explicitly marked",
            "description","当前源码契约，实际发布状态见对应版本记录。路由、参数与声明DTO由Spring MVC及Java类型提取；已审查的包装响应从实际服务泛型提取。旧JSON按明确的宽容接收约定和标准字段指南描述，不新增严格验证器。default响应及基础设施错误不承诺穷尽结构或状态。业务限制、所有权、功能开关及事务语义见core-api.md。没有真实用户样本。")));
        contract.set("servers",json.valueToTree(List.of(Map.of("url","/api"))));
        contract.set("x-shared-failure-families",ApiContractErrors.catalog(json));
        contract.set("security",json.valueToTree(List.of(Map.of("bearerAuth",List.of()))));
        ObjectNode paths=contract.putObject("paths");List<String> operations=new ArrayList<>();
        ApiContractShapes reviewed=new ApiContractShapes(json,t->schema(t,new Annotation[0]));
        mappings.getHandlerMethods().entrySet().stream().sorted(Comparator.comparing(e->e.getKey().toString())).forEach(entry->{
            var handler=entry.getValue();Method method=handler.getMethod();
            if(!handler.getBeanType().getPackageName().startsWith("com.oyts.planplatform"))return;
            for(String full:entry.getKey().getPatternValues()){
                if(!full.startsWith("/api/"))continue;String springPath=full.substring(4),path=ApiContractServiceTypes.normalizePath(springPath);
                for(var verb:entry.getKey().getMethodsCondition().getMethods()){
                    String key=verb+" "+path;operations.add(key);ObjectNode op=json.createObjectNode();
                    op.put("operationId",handler.getBeanType().getSimpleName()+"_"+method.getName()+"_"+verb);
                    op.put("x-java-handler",handler.getBeanType().getName()+"#"+method.getName());
                    if(!springPath.equals(path))op.put("x-spring-route",springPath);
                    if(PUBLIC.contains(key))op.putArray("security");
                    if(key.equals("POST /reminders/poll"))op.set("security",json.valueToTree(List.of(Map.of("reminderKey",List.of()))));
                    PreAuthorize rule=AnnotatedElementUtils.findMergedAnnotation(method,PreAuthorize.class);
                    if(rule==null)rule=AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(),PreAuthorize.class);
                    if(rule!=null)op.put("x-pre-authorize",rule.value());
                    ArrayNode parameters=json.createArrayNode();
                    for(Parameter p:method.getParameters()){
                        RequestBody body=p.getAnnotation(RequestBody.class);
                        if(body!=null){ObjectNode request=op.putObject("requestBody");request.put("required",body.required());
                            ObjectNode reviewedRequest=reviewed.request(key);
                            ObjectNode bodySchema=reviewedRequest!=null?reviewedRequest:schema(json.constructType(p.getParameterizedType()),p.getAnnotations());
                            if(body.required())bodySchema.put("nullable",false);
                            request.putObject("content").putObject("application/json").set("schema",bodySchema);continue;}
                        PathVariable pv=p.getAnnotation(PathVariable.class);RequestParam qp=p.getAnnotation(RequestParam.class);RequestHeader hp=p.getAnnotation(RequestHeader.class);
                        if(pv==null&&qp==null&&hp==null)continue;
                        String name=pv!=null?first(pv.name(),pv.value(),p.getName()):qp!=null?first(qp.name(),qp.value(),p.getName()):first(hp.name(),hp.value(),p.getName());
                        ObjectNode value=parameters.addObject();value.put("name",name);value.put("in",pv!=null?"path":qp!=null?"query":"header");
                        String fallback=qp!=null?qp.defaultValue():hp!=null?hp.defaultValue():ValueConstants.DEFAULT_NONE;
                        value.put("required",pv!=null||(fallback.equals(ValueConstants.DEFAULT_NONE)&&(qp!=null?qp.required():hp.required())));
                        ObjectNode shape=schema(json.constructType(p.getParameterizedType()),p.getAnnotations());
                        if(!fallback.equals(ValueConstants.DEFAULT_NONE))shape.put("x-spring-default",fallback);value.set("schema",shape);
                    }
                    if(!parameters.isEmpty())op.set("parameters",parameters);
                    if(Set.of("POST /data/preview","POST /data/restore").contains(key)){
                        op.put("x-streamed-json-body","Strict duplicate-field detection and byte-limited raw JSON; see core-api.md and PersonalDataController");
                        ObjectNode request=op.putObject("requestBody");request.put("required",true);request.putObject("content").putObject("application/json").set("schema",reviewed.request(key));
                    }
                    JavaType response=json.constructType(method.getGenericReturnType());boolean dynamic=HttpEntity.class.isAssignableFrom(response.getRawClass())||response.hasRawClass(Object.class);
                    if(HttpEntity.class.isAssignableFrom(response.getRawClass()))response=response.containedTypeOrUnknown(0);
                    Type serviceResponse=ApiContractServiceTypes.response(handler.getBeanType(),method,verb.name());
                    if(serviceResponse!=null){response=json.constructType(serviceResponse);op.put("x-response-source","reviewed-service-delegation");}
                    ResponseStatus status=AnnotatedElementUtils.findMergedAnnotation(method,ResponseStatus.class);
                    ObjectNode reviewedShape=reviewed.response(key);Integer reviewedStatus=ApiContractShapes.status(key);
                    String code=reviewedStatus!=null?reviewedStatus.toString():status!=null?Integer.toString(status.code().value()):serviceResponse!=null||reviewedShape!=null?"200":dynamic?"default":"200";
                    ObjectNode reply=op.putObject("responses").putObject(code);
                    reply.put("description",dynamic&&status==null?"状态由控制器运行分支决定，声明类型不保证具体HTTP状态":"声明的正常响应；失败语义另见core-api.md");
                    if(!code.equals("204")&&!response.hasRawClass(void.class)&&!response.hasRawClass(Void.class)){
                        var produced=entry.getKey().getProducesCondition().getProducibleMediaTypes();
                        String media=produced.size()==1?produced.iterator().next().toString():reviewedShape!=null?ApiContractShapes.media(key):serviceResponse!=null?"application/json":dynamic||response.hasRawClass(String.class)?"*/*":"application/json";
                        reply.putObject("content").putObject(media).set("schema",reviewedShape!=null?reviewedShape:schema(response,new Annotation[0]));
                        if(reviewedShape!=null)op.put("x-response-source","reviewed-wire-whitelist");
                        if(media.equals("*/*"))reply.put("x-runtime-content-type",true);
                    }
                    ObjectNode pathNode=paths.has(path)?(ObjectNode)paths.get(path):paths.putObject(path);assertThat(pathNode.has(verb.name().toLowerCase(Locale.ROOT))).as(key).isFalse();
                    pathNode.set(verb.name().toLowerCase(Locale.ROOT),op);
                }
            }
        });
        assertThat(operations).doesNotHaveDuplicates().contains("POST /auth/email-change/verify","POST /migrations/legacy/{importId}/recycling-supplement");
        ObjectNode components=contract.putObject("components");ObjectNode security=components.putObject("securitySchemes");
        security.set("bearerAuth",json.valueToTree(Map.of("type","http","scheme","bearer","bearerFormat","JWT")));
        security.set("reminderKey",json.valueToTree(Map.of("type","apiKey","in","header","name","X-Reminder-Key")));
        ObjectNode types=components.putObject("schemas");schemas.forEach(types::set);types.set("legacy.CanonicalEnvelopes",new ApiLegacyContract(json).canonical());contract.put("x-operation-count",operations.size());
        Path destination=Path.of("../docs/openapi-current.json").toAbsolutePath().normalize();
        if("true".equals(System.getProperty("contract.update"))){Files.writeString(destination,json.writerWithDefaultPrettyPrinter().writeValueAsString(contract)+"\n");}
        // Normalize numeric node widths just as the checked-in JSON is parsed (Min.value is long).
        assertThat(json.readTree(destination.toFile()).equals(json.readTree(json.writeValueAsBytes(contract))))
            .as("Route/DTO contract drift: review changes then run with -Dcontract.update=true").isTrue();
        assertThat(paths.path("/auth/email-change/verify").path("post").path("security").isArray()).isTrue();
        assertThat(paths.path("/migrations/legacy/{importId}/recycling-supplement").path("post").has("security")).isFalse();
    }

    private ObjectNode schema(JavaType type,Annotation[] constraints){
        ObjectNode out=json.createObjectNode();Class<?> raw=type.getRawClass();
        if(type.isEnumType()){out.put("type","string");out.set("enum",json.valueToTree(Arrays.stream(raw.getEnumConstants()).map(Object::toString).toList()));}
        else if(raw==String.class||raw==char.class||raw==Character.class||raw==UUID.class||Temporal.class.isAssignableFrom(raw)){
            out.put("type","string");if(raw==UUID.class)out.put("format","uuid");else if(raw==java.time.LocalDate.class)out.put("format","date");else if(raw==java.time.Instant.class||raw==java.time.OffsetDateTime.class)out.put("format","date-time");
        }else if(raw==boolean.class||raw==Boolean.class)out.put("type","boolean");
        else if(raw.isPrimitive()||Number.class.isAssignableFrom(raw)){out.put("type",raw==double.class||raw==float.class||raw==Double.class||raw==Float.class||raw==java.math.BigDecimal.class?"number":"integer");}
        else if(raw==byte[].class){out.put("type","string");out.put("format","binary");}
        else if(type.isArrayType()||type.isCollectionLikeType()){out.put("type","array");out.set("items",schema(type.getContentType(),new Annotation[0]));}
        else if(type.isMapLikeType()){out.put("type","object");out.set("additionalProperties",schema(type.getContentType(),new Annotation[0]));}
        else if(raw.isRecord()){
            String name=raw.getName().replace("com.oyts.planplatform.","").replace('$','.');
            if(type.hasGenericTypes())name+="."+Integer.toUnsignedString(type.toCanonical().hashCode(),16);
            out.put("$ref","#/components/schemas/"+name);
            if(!schemas.containsKey(name)){
                ObjectNode record=json.createObjectNode();schemas.put(name,record);record.put("type","object");ObjectNode props=record.putObject("properties");ArrayNode required=json.createArrayNode();
                for(RecordComponent field:raw.getRecordComponents()){
                    Annotation[] annotations=field.getAccessor().getAnnotations();
                    // Validation annotations on record components are propagated to the backing field.
                    try{annotations=raw.getDeclaredField(field.getName()).getAnnotations();}catch(NoSuchFieldException impossible){throw new IllegalStateException(impossible);}
                    JavaType resolved=json.getTypeFactory().resolveMemberType(field.getGenericType(),type.getBindings());
                    ObjectNode reviewedField=new ApiContractShapes(json,t->schema(t,new Annotation[0])).field(raw,field.getName());
                    props.set(field.getName(),reviewedField!=null?reviewedField:schema(resolved,annotations));
                    if(Arrays.stream(annotations).anyMatch(a->a instanceof NotNull||a instanceof NotBlank||a instanceof NotEmpty||a instanceof AssertTrue))required.add(field.getName());
                }
                if(!required.isEmpty())record.set("required",required);
            }
        }else{out.put("x-dynamic-schema",type.toCanonical());out.put("description","动态JSON／非record声明，未从真实数据猜测结构；见对应控制器及专题契约。");}
        for(Annotation a:constraints){
            if(a instanceof Size size){boolean array=out.path("type").asText().equals("array"),map=out.path("type").asText().equals("object");String min=array?"minItems":map?"minProperties":"minLength",max=array?"maxItems":map?"maxProperties":"maxLength";out.put(min,size.min());if(size.max()!=Integer.MAX_VALUE)out.put(max,size.max());}
            if(a instanceof Min min)out.put("minimum",min.value());if(a instanceof Max max)out.put("maximum",max.value());
            if(a instanceof Positive)out.put("minimum",1);if(a instanceof PositiveOrZero)out.put("minimum",0);
            if(a instanceof Pattern pattern)out.put("pattern",pattern.regexp());if(a instanceof Email)out.put("format","email");
            if(a instanceof AssertTrue)out.set("enum",json.valueToTree(List.of(true)));
            if(a instanceof NotBlank){out.put("x-not-blank",true);out.put("minLength",Math.max(1,out.path("minLength").asInt()));}
            if(a instanceof NotEmpty){String min=out.path("type").asText().equals("array")?"minItems":out.path("type").asText().equals("object")?"minProperties":"minLength";out.put(min,Math.max(1,out.path(min).asInt()));}
        }
        if(!raw.isPrimitive()&&Arrays.stream(constraints).noneMatch(a->a instanceof NotNull||a instanceof NotBlank||a instanceof NotEmpty)){
            if(out.has("$ref")){String reference=out.remove("$ref").asText();out.putArray("allOf").addObject().put("$ref",reference);}
            out.put("nullable",true);
        }
        return out;
    }
    private static String first(String...values){return Arrays.stream(values).filter(v->!v.isBlank()).findFirst().orElseThrow();}
}
