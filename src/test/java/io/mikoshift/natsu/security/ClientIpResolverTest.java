package io.mikoshift.natsu.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.mikoshift.natsu.config.NatsuProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private static final NatsuProperties.RateLimit.Bucket BUCKET = new NatsuProperties.RateLimit.Bucket(5, 60);

    @Test
    void ignoresForwardedForWhenDirectPeerIsNotTrusted() {
        ClientIpResolver resolver = resolverWithTrustedProxies();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50");
    }

    @Test
    void usesForwardedForWhenDirectPeerIsTrusted() {
        ClientIpResolver resolver = resolverWithTrustedProxies("10.0.0.0/8");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void walksForwardedChainFromRightSkippingTrustedHops() {
        ClientIpResolver resolver = resolverWithTrustedProxies("10.0.0.0/8");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void normalizesIpv4MappedAddresses() {
        ClientIpResolver resolver = resolverWithTrustedProxies("127.0.0.1/32");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::ffff:127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    private static ClientIpResolver resolverWithTrustedProxies(String... trustedProxyCidrs) {
        NatsuProperties.RateLimit rateLimit =
                new NatsuProperties.RateLimit(BUCKET, BUCKET, BUCKET, BUCKET, BUCKET, BUCKET);
        NatsuProperties properties = new NatsuProperties(
                "/tmp/natsu-test",
                52_428_800L,
                524_288_000L,
                List.of("*"),
                List.of(trustedProxyCidrs),
                rateLimit,
                "http://localhost:3000/reset-password?token={token}",
                "noreply@example.com",
                new NatsuProperties.Auth(Duration.ofHours(1), Duration.ofDays(365), Duration.ofSeconds(30)),
                new NatsuProperties.BookImportRecovery(true, 15, 5, 3));
        return new ClientIpResolver(properties);
    }
}
