package io.mikoshift.natsu.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "natsu")
public record NatsuProperties(String storageRoot, long maxPackageBytes, long maxStorageBytesPerUser) {}
