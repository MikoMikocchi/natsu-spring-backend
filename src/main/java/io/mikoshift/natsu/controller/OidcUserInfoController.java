package io.mikoshift.natsu.controller;

import io.mikoshift.natsu.entity.User;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OidcUserInfoController {

    @GetMapping("/userinfo")
    Map<String, Object> userInfo(@AuthenticationPrincipal User user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.getId().toString());
        claims.put("email", user.getEmail());
        claims.put("name", user.getName());
        return claims;
    }
}
