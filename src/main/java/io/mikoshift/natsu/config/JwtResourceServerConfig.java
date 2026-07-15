package io.mikoshift.natsu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

@Configuration
public class JwtResourceServerConfig {

    @Bean
    BearerTokenResolver bearerTokenResolver() {
        return new DefaultBearerTokenResolver();
    }
}
