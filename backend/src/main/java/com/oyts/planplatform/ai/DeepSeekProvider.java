package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

/** Official origin only, no SDK retries, no redirects and no upstream response logging. */
final class DeepSeekProvider implements AiProvider {
    private final ObjectMapper mapper;
    private final AiProperties config;
    private final HttpClient client;
    private final URI endpoint;
    private final Supplier<String> key;
    DeepSeekProvider(ObjectMapper mapper, AiProperties config) {
        this(mapper, config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
            URI.create("https://api.deepseek.com/chat/completions"), () -> AiSecretReader.read(config));
    }
    // Package-private transport injection for loopback-only tests; never exposed as a setting.
    DeepSeekProvider(ObjectMapper mapper, AiProperties config, HttpClient client, URI endpoint, Supplier<String> key) {
        this.mapper=mapper; this.config=config; this.client=client; this.endpoint=endpoint; this.key=key;
    }
    @Override public Result generate(Request request) {
        if (!config.enabled()) throw new AiFailure(AiFailure.Code.DISABLED);
        if (request.message().length()>config.maxInputChars() || request.maxOutputTokens()>config.maxOutputTokens()) throw new AiFailure(AiFailure.Code.INVALID_CONFIG);
        try {
            String body=mapper.writeValueAsString(Map.of("model",config.model(),"messages",List.of(
                Map.of("role","system","content",AiPrompt.SYSTEM),Map.of("role","user","content",request.message())),
                "response_format",Map.of("type","json_object"),"thinking",Map.of("type","disabled"),"max_tokens",request.maxOutputTokens(),"stream",false));
            var http=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(config.timeoutSeconds())).header("Authorization","Bearer "+key.get())
                .header("Content-Type","application/json").header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=client.send(http,info->new LimitedBody(256_000));
            if (response.statusCode()!=200) throw new AiFailure(AiFailure.Code.UPSTREAM_UNAVAILABLE,false);
            var root=mapper.readTree(response.body());var choice=root.path("choices").path(0);
            if (!"stop".equals(choice.path("finish_reason").asText()) || !choice.path("message").path("content").isTextual()) throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);
            String text=choice.path("message").path("content").textValue();
            var usage=root.path("usage");
            if (!usage.path("prompt_tokens").canConvertToLong() || !usage.path("completion_tokens").canConvertToLong()) throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);
            return new Result(text,usage.path("prompt_tokens").longValue(),usage.path("completion_tokens").longValue());
        } catch (AiFailure safe) { throw safe; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AiFailure(AiFailure.Code.UPSTREAM_UNAVAILABLE,true); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new AiFailure(AiFailure.Code.INVALID_OUTPUT); }
        catch (Exception transport) { throw new AiFailure(AiFailure.Code.UPSTREAM_UNAVAILABLE,true); }
    }
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit; private final ByteArrayOutputStream output=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>(); private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit=limit; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription value) { subscription=value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for(var buffer:buffers) {
                if (buffer.remaining()>limit-output.size()) { subscription.cancel(); result.completeExceptionally(new AiFailure(AiFailure.Code.INVALID_OUTPUT));return; }
                byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);output.writeBytes(bytes);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(new AiFailure(AiFailure.Code.UPSTREAM_UNAVAILABLE,true)); }
        public void onComplete() { result.complete(output.toByteArray()); }
    }
}
