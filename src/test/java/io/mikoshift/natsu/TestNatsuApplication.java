package io.mikoshift.natsu;

import org.springframework.boot.SpringApplication;

public class TestNatsuApplication {

    public static void main(String[] args) {
        SpringApplication.from(NatsuApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
