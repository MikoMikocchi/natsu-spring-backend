package io.mikoshift.natsu.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration is excluded: auth is entirely bearer-token based
// (BearerTokenAuthenticationFilter + AuthToken table), so the default in-memory user Spring
// Security would otherwise generate is unused and just noise in the logs.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class NatsuSpringBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(NatsuSpringBackendApplication.class, args);
    }

}
