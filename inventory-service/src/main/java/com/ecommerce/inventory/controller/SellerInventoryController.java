package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.CreateInventoryRequest;
import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.UpdateInventoryRequest;
import com.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seller/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
public class SellerInventoryController {
    private final InventoryService inventoryService;

    @PostMapping
    public InventoryResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateInventoryRequest request) {
        return inventoryService.createSellerInventory(request, userId(jwt), isAdmin(jwt));
    }

    @GetMapping("/{productId}")
    public InventoryResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        return inventoryService.getSellerInventory(productId, userId(jwt), isAdmin(jwt));
    }

    @PutMapping("/{productId}")
    public InventoryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId,
            @Valid @RequestBody UpdateInventoryRequest request) {
        return inventoryService.updateSellerInventory(productId, request, userId(jwt), isAdmin(jwt));
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("userId")); }
    private boolean isAdmin(Jwt jwt) { return "ADMIN".equals(jwt.getClaimAsString("role")); }
}
