package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration @EnableScheduling
class AiConfiguration {
    @Bean @ConditionalOnMissingBean(AiProvider.class)
    AiProvider aiProvider(ObjectMapper mapper, AiProperties config) { return new DeepSeekProvider(mapper,config); }
    @Bean(destroyMethod="close")
    AiExecutionGuard aiGuard(AiProperties config) { return new AiExecutionGuard(config.maxConcurrent(),0); }
}
