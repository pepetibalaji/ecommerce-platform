package com.ecommerce.product.dto;

import lombok.*;

import java.math.BigDecimal;

import java.time.Instant;

import java.util.UUID;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class ProductResponse {

    private UUID id;

    private UUID sellerId;

    private boolean active;

    private String name;

    private String description;

    private BigDecimal price;

    private String currency;

    private String category;

    private String brand;

    private List<String> imageUrls;

    private Instant createdAt;

    private Instant updatedAt;
}
