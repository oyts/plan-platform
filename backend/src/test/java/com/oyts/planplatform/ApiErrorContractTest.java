package com.oyts.planplatform;

import com.fasterxml.jackson.databind.*;
import com.oyts.planplatform.auth.MemberAccessFilter;
import com.oyts.planplatform.common.*;
import com.oyts.planplatform.sync.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.*;
import org.springframework.mock.web.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.*;
import org.springframework.web.bind.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiErrorContractTest {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    @AfterEach void clearAuthentication(){SecurityContextHolder.clearContext();}
    @Test void allAdviceExceptionsMatchReviewedStatusAndWireKeys()throws Exception{
        var advice=new ApiExceptionHandler();Set<Integer> statuses=new TreeSet<>();Set<String> handlers=new TreeSet<>();
        for(var method:ApiExceptionHandler.class.getDeclaredMethods()){
            var annotation=method.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class);
            if(annotation==null||method.getName().equals("validation"))continue;
            handlers.add(method.getName());method.setAccessible(true);
            for(Class<? extends Throwable> exception:annotation.value()){
                Throwable input=exception==MailDeliveryException.class?new MailDeliveryException():exception.getConstructor(String.class).newInstance("合成错误");
                var response=(ResponseEntity<?>)method.invoke(advice,input);statuses.add(response.getStatusCode().value());
                assertKeys(json.valueToTree(response.getBody()),"controllerAdvice");
                assertThat(json.valueToTree(response.getBody()).path("status").asInt()).isEqualTo(response.getStatusCode().value());
            }
        }
        assertThat(handlers).containsExactlyInAnyOrder("mailUnavailable","notFound","conflict","unauthorized","forbidden","tooManyRequests","badRequest");
        assertThat(statuses).containsExactly(400,401,403,404,409,429,503);
    }
    void syntheticBody(String value){}
    @Test void validationUsesFieldMapWithoutEchoingRejectedValue()throws Exception{
        var binding=new BeanPropertyBindingResult(new Object(),"synthetic");
        binding.addError(new FieldError("synthetic","name","不可回显的合成值",false,null,null,"不能为空"));
        var input=new MethodArgumentNotValidException(new MethodParameter(getClass().getDeclaredMethod("syntheticBody",String.class),0),binding);
        var method=ApiExceptionHandler.class.getDeclaredMethod("validation",MethodArgumentNotValidException.class);method.setAccessible(true);
        var response=(ResponseEntity<?>)method.invoke(new ApiExceptionHandler(),input);JsonNode body=json.valueToTree(response.getBody());
        assertKeys(body,"beanValidation");assertThat(body.path("fields").path("name").asText()).isEqualTo("不能为空");
        assertThat(body.toString()).doesNotContain("不可回显的合成值");
    }
    @Test void memberFilterHasMessageOnlyForRevocationAndDatabaseOutage()throws Exception{
        var db=mock(JdbcTemplate.class);authenticate();
        when(db.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<long[]>>any(),any(Object[].class))).thenReturn(List.of());
        var request=new MockHttpServletRequest("GET","/api/plans");var response=new MockHttpServletResponse();
        new MemberAccessFilter(db,true).doFilter(request,response,(a,b)->{throw new AssertionError("Must stop before business handler");});
        assertThat(response.getStatus()).isEqualTo(401);assertKeys(json.readTree(response.getContentAsByteArray()),"memberAccess");
        authenticate();reset(db);
        when(db.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<long[]>>any(),any(Object[].class))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("合成数据库错误"));
        response=new MockHttpServletResponse();new MemberAccessFilter(db,true).doFilter(request,response,(a,b)->{throw new AssertionError("Must stop");});
        assertThat(response.getStatus()).isEqualTo(503);assertKeys(json.readTree(response.getContentAsByteArray()),"memberAccess");
        assertThat(response.getContentAsString()).doesNotContain("合成数据库错误");
    }
    @Test void idempotencyConflictAndMalformedKeyHaveDifferentBodies()throws Exception{
        authenticate();var service=mock(IdempotencyService.class);
        when(service.reserve(anyString(),anyString(),anyString(),anyString(),anyString())).thenThrow(new ConflictException("合成冲突"));
        var request=new MockHttpServletRequest("POST","/api/todos");request.addHeader("X-Idempotency-Key","synthetic-001");
        var response=new MockHttpServletResponse();new IdempotencyFilter(service).doFilter(request,response,(a,b)->{throw new AssertionError("Must stop");});
        assertThat(response.getStatus()).isEqualTo(409);assertKeys(json.readTree(response.getContentAsByteArray()),"idempotencyConflict");
        request=new MockHttpServletRequest("POST","/api/todos");request.addHeader("X-Idempotency-Key","bad");response=new MockHttpServletResponse();
        new IdempotencyFilter(service).doFilter(request,response,(a,b)->{throw new AssertionError("Must stop");});
        assertThat(response.getStatus()).isEqualTo(400);assertThat(response.getErrorMessage()).contains("格式无效");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
    @Test void bodyLimitUsesContainerErrorsAndKeepsLargerSavedDraftAllowance()throws Exception{
        var constructor=Class.forName("com.oyts.planplatform.ai.AiBodyLimitFilter").getDeclaredConstructor();constructor.setAccessible(true);
        for(String path:List.of("/api/ai/jobs","/api/reflection/analyses","/api/ai/saved-drafts/synthetic")){
            int limit=path.contains("saved-drafts")?524288:256000;
            for(int size:List.of(limit,limit+1)){
                var request=new MockHttpServletRequest("POST",path);request.setContent(new byte[size]);var response=new MockHttpServletResponse();
                ((jakarta.servlet.Filter)constructor.newInstance()).doFilter(request,response,(a,b)->((jakarta.servlet.http.HttpServletResponse)b).setStatus(204));
                assertThat(response.getStatus()).as(path+" bytes="+size).isEqualTo(size==limit?204:413);
                assertThat(response.getContentAsByteArray()).isEmpty();
            }
        }
    }
    private void authenticate(){SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("synthetic-not-a-secret").header("alg","HS256").subject("synthetic-user").build(),List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));}
    private void assertKeys(JsonNode body,String family){
        Set<String> actual=new TreeSet<>();body.fieldNames().forEachRemaining(actual::add);
        Set<String> expected=new TreeSet<>();ApiContractErrors.catalog(json).path(family).path("schema").path("required").forEach(n->expected.add(n.asText()));
        assertThat(actual).isEqualTo(expected);
    }
}
