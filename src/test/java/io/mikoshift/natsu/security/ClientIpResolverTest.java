package io.mikoshift.natsu.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.mikoshift.natsu.config.NatsuProperties;
import io.mikoshift.natsu.config.NatsuPropertiesFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

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
        NatsuProperties properties =
                NatsuPropertiesFixtures.minimal("/tmp/natsu-test", 52_428_800L, 524_288_000L, trustedProxyCidrs);
        return new ClientIpResolver(properties);
    }
}
