package com.oyts.planplatform.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(@DefaultValue("false") boolean enabled, @DefaultValue("deepseek-flash") String model,
                           @DefaultValue("") String apiKeyFile, @DefaultValue("16000") int maxInputChars,
                           @DefaultValue("4096") int maxOutputTokens, @DefaultValue("2") int maxConcurrent,
                           @DefaultValue("4") int queueCapacity, @DefaultValue("60") int timeoutSeconds) {
    public AiProperties {
        if (!"deepseek-flash".equals(model)) throw new IllegalArgumentException("AI 模型配置无效。");
        if (maxInputChars < 100 || maxInputChars > 32000 || maxOutputTokens < 128 || maxOutputTokens > 8192
            || maxConcurrent < 1 || maxConcurrent > 4 || queueCapacity < 0 || queueCapacity > 20 || timeoutSeconds < 1 || timeoutSeconds > 120)
            throw new IllegalArgumentException("AI 资源限制配置无效。");
        apiKeyFile = apiKeyFile == null ? "" : apiKeyFile;
    }
    // A fixed official origin; neither prompts nor clients can choose an outbound URL.
    public String baseUrl() { return "https://api.deepseek.com"; }
}
