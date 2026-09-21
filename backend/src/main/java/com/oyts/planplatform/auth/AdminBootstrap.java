package com.oyts.planplatform.auth;

import com.oyts.planplatform.config.SecurityProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AdminBootstrap {
    @Bean
    ApplicationRunner bootstrapAdmin(UserAccountRepository users, SecurityProperties security) {
        return ignored -> {
            String email = security.bootstrapAdminEmail();
            if (email == null || email.isBlank()) return;
            users.findByEmailIgnoreCase(email.trim()).ifPresent(user -> { user.makeAdmin(); users.save(user); });
        };
    }
}
