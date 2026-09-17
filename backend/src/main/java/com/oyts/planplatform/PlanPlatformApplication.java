package com.oyts.planplatform;

import com.oyts.planplatform.config.JwtProperties;
import com.oyts.planplatform.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, SecurityProperties.class})
public class PlanPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlanPlatformApplication.class, args);
    }
}
