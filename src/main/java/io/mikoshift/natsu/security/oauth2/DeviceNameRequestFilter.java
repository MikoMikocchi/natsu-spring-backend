package io.mikoshift.natsu.security.oauth2;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Captures User-Agent for device session labeling before the OAuth2 token endpoint runs. */
@Component
public class DeviceNameRequestFilter extends OncePerRequestFilter {

    public static final String DEVICE_NAME_ATTRIBUTE = "natsu.device_name";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("/oauth2/token".equals(request.getRequestURI())) {
            request.setAttribute(DEVICE_NAME_ATTRIBUTE, deviceName(request));
        }
        filterChain.doFilter(request, response);
    }

    private static String deviceName(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }
}
