package io.mikoshift.natsu.security.oauth2;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

public final class CustomAuthorizationGrantTypes {

    /** First-party login via {@code POST /v1/auth/login}; not exposed as an OAuth2 token grant. */
    public static final AuthorizationGrantType FIRST_PARTY = new AuthorizationGrantType("first_party");

    private CustomAuthorizationGrantTypes() {}
}
