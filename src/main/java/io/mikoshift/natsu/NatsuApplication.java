package io.mikoshift.natsu;

import io.mikoshift.natsu.config.NatsuProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration is excluded: auth is entirely bearer-token based
// (BearerTokenAuthenticationFilter + AuthToken table), so the default in-memory user Spring
// Security would otherwise generate is unused and just noise in the logs.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(NatsuProperties.class)
public class NatsuApplication {

    public static void main(String[] args) {
        SpringApplication.run(NatsuApplication.class, args);
    }
}
