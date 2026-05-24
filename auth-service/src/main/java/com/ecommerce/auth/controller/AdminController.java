package com.ecommerce.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")

public class AdminController {

    @GetMapping("/users")
    public Map<String, String> adminOnly() {

        return Map.of(
                "message",
                "Admin API accessed successfully"
        );
    }
}