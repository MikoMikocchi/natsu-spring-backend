package io.mikoshift.natsu;

import io.mikoshift.natsu.config.NatsuProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(NatsuProperties.class)
public class NatsuApplication {

    public static void main(String[] args) {
        SpringApplication.run(NatsuApplication.class, args);
    }
}
