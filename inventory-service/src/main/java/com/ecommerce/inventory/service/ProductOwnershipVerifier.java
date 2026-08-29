package com.ecommerce.inventory.service;

import java.util.UUID;

public interface ProductOwnershipVerifier {
    void assertOwnedBy(UUID productId, UUID sellerId);
}
