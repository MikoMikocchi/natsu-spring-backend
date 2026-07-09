package io.mikoshift.natsu.backend;

import org.springframework.boot.SpringApplication;

public class TestNatsuSpringBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(NatsuSpringBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
