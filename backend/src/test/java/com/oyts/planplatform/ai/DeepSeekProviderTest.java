package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class DeepSeekProviderTest {
    HttpServer server;AtomicInteger hits=new AtomicInteger(),code=new AtomicInteger(200);AtomicReference<String> sent=new AtomicReference<>();
    String valid="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":2}}";
    AtomicReference<String> response=new AtomicReference<>();
    final ObjectMapper mapper=new ObjectMapper();
    @BeforeEach void setup() throws Exception {
        response.set(valid);server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat/completions",exchange->{hits.incrementAndGet();sent.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));assertEquals("Bearer sk-fake-not-real",exchange.getRequestHeaders().getFirst("Authorization"));byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Location","/redirected");exchange.sendResponseHeaders(code.get(),bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}});
        server.start();
    }
    @AfterEach void close(){server.stop(0);}
    DeepSeekProvider provider(){var config=new AiProperties(true,"deepseek-flash","/fake",8000,2048,2,4,2);return new DeepSeekProvider(mapper,config,HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/chat/completions"),()->"sk-fake-not-real");}
    @Test void sendsOfficialShapeWithExplicitJsonAndBoundedNonThinkingOutput() throws Exception {
        var result=provider().generate(new AiProvider.Request("测试上下文",2048));assertEquals("{}",result.text());assertEquals(12,result.inputTokens());
        var body=mapper.readTree(sent.get());assertEquals("deepseek-flash",body.path("model").asText());assertEquals("json_object",body.path("response_format").path("type").asText());assertEquals("disabled",body.path("thinking").path("type").asText());assertEquals(2048,body.path("max_tokens").asInt());assertFalse(body.path("stream").asBoolean());assertEquals(1,hits.get());
    }
    @Test void neverRetriesOrFollowsRedirectsAndDoesNotLeakUpstreamBody() {
        for(int status:new int[]{302,401,429,503}) {code.set(status);response.set("private-upstream-content");var failure=assertThrows(AiFailure.class,()->provider().generate(new AiProvider.Request("fixture",128)));assertFalse(failure.uncertain());assertFalse(failure.toString().contains("private-upstream"));}
        assertEquals(4,hits.get());
    }
    @Test void rejectsTruncationMissingUsageAndOversizedResponse() {
        response.set(valid.replace("stop","length"));assertEquals(AiFailure.Code.INVALID_OUTPUT,assertThrows(AiFailure.class,()->provider().generate(new AiProvider.Request("fixture",128))).code());
        response.set("{}");assertThrows(AiFailure.class,()->provider().generate(new AiProvider.Request("fixture",128)));
        response.set("x".repeat(256001));assertThrows(AiFailure.class,()->provider().generate(new AiProvider.Request("fixture",128)));
        assertEquals(3,hits.get());
    }
}
