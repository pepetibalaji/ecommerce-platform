package com.ecommerce.auth.controller;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")

public class UserController {

    @GetMapping("/me")
    public Map<String, Object> currentUser(
            Authentication authentication
    ) {
        return Map.of(
                "email", authentication.getName(),
                "authorities", authentication.getAuthorities()
        );
    }
}