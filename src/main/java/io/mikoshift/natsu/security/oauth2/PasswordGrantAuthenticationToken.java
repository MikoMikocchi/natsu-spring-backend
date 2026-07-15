package io.mikoshift.natsu.security.oauth2;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

public class PasswordGrantAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

    private final String clientId;
    private final String username;
    private final String password;
    private final String deviceName;

    public PasswordGrantAuthenticationToken(
            String clientId, Authentication clientPrincipal, String username, String password, String deviceName) {
        super(CustomAuthorizationGrantTypes.PASSWORD, clientPrincipal, null);
        this.clientId = clientId;
        this.username = username;
        this.password = password;
        this.deviceName = deviceName;
    }

    public String getClientId() {
        return clientId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDeviceName() {
        return deviceName;
    }
}
