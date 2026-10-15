package com.oyts.planplatform;

import com.oyts.planplatform.config.JwtProperties;
import com.oyts.planplatform.config.SecurityProperties;
import com.oyts.planplatform.config.BootstrapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, SecurityProperties.class, BootstrapProperties.class, com.oyts.planplatform.ai.AiProperties.class})
public class PlanPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlanPlatformApplication.class, args);
    }
}
