package io.mikoshift.natsu.security;

import io.mikoshift.natsu.config.NatsuProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Resolves the client IP for throttling and similar per-client checks.
 *
 * <p>When the app sits behind a reverse proxy or load balancer, the direct TCP peer is the proxy,
 * not the end user. {@code X-Forwarded-For} is parsed only when that peer is listed in {@link
 * NatsuProperties#trustedProxyCidrs()}; otherwise the direct address is returned and any forwarded
 * header from the client is ignored. This prevents spoofing when the app is reachable without a
 * trusted proxy in front (the common case for local dev and misconfigured production deploys).
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final NatsuProperties properties;

    public String resolve(HttpServletRequest request) {
        String directIp = normalizeIp(request.getRemoteAddr());
        if (!isTrustedProxy(directIp)) {
            return directIp;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return directIp;
        }

        List<String> chain = Arrays.stream(forwardedFor.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(ClientIpResolver::normalizeIp)
                .filter(ClientIpResolver::isValidIp)
                .toList();
        if (chain.isEmpty()) {
            return directIp;
        }

        // Walk from the right, skipping trusted hops; the rightmost non-trusted entry is the client.
        for (int i = chain.size() - 1; i >= 0; i--) {
            String hop = chain.get(i);
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }

        return directIp;
    }

    private boolean isTrustedProxy(String ip) {
        List<String> trustedProxyCidrs = properties.trustedProxyCidrs();
        if (trustedProxyCidrs == null || trustedProxyCidrs.isEmpty()) {
            return false;
        }
        for (String cidr : trustedProxyCidrs) {
            if (cidr != null && !cidr.isBlank() && new IpAddressMatcher(cidr.trim()).matches(ip)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeIp(String ip) {
        if (ip == null) {
            return "";
        }
        if (ip.regionMatches(true, 0, "::ffff:", 0, 7)) {
            return ip.substring(7);
        }
        return ip;
    }

    private static boolean isValidIp(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
