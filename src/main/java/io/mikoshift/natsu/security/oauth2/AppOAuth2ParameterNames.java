package io.mikoshift.natsu.security.oauth2;

import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

public final class AppOAuth2ParameterNames {

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    private AppOAuth2ParameterNames() {}

    public static boolean isPasswordGrant(String grantType) {
        return CustomAuthorizationGrantTypes.PASSWORD.getValue().equals(grantType);
    }

    public static boolean isRefreshGrant(String grantType) {
        return OAuth2ParameterNames.REFRESH_TOKEN.equals(grantType);
    }
}
