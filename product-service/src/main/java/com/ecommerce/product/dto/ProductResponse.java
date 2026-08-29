package com.ecommerce.product.dto;

import lombok.*;

import java.math.BigDecimal;

import java.time.LocalDateTime;

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

    private String name;

    private String description;

    private BigDecimal price;

    private String category;

    private String brand;

    private List<String> imageUrls;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
