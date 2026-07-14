package io.mikoshift.natsu.security.oauth2;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

public final class NatsuAuthorizationGrantTypes {

    public static final AuthorizationGrantType PASSWORD = new AuthorizationGrantType("password");

    private NatsuAuthorizationGrantTypes() {}
}
