package io.mikoshift.natsu.backend.security;

import io.mikoshift.natsu.backend.entity.AuthToken;
import io.mikoshift.natsu.backend.service.auth.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves {@code Authorization: Bearer <token>} against the auth_tokens table. On success, the
 * SecurityContext principal is the {@code User} and the credentials are the {@code AuthToken}
 * itself, so controllers that need to know "which device session is this request" (logout, session
 * list "current" flag) can read it off {@code Authentication#getCredentials()}.
 */
@Component
@RequiredArgsConstructor
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final TokenService tokenService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      String accessToken = header.substring(BEARER_PREFIX.length());
      tokenService.resolveAccessToken(accessToken).ifPresent(this::authenticate);
    }
    filterChain.doFilter(request, response);
  }

  private void authenticate(AuthToken token) {
    var authentication = new UsernamePasswordAuthenticationToken(token.getUser(), token, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
