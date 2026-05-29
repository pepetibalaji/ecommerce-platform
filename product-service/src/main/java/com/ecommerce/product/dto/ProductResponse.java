package com.ecommerce.product.dto;

import lombok.*;

import java.math.BigDecimal;

import java.time.LocalDateTime;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class ProductResponse {

    private UUID id;

    private String name;

    private String description;

    private BigDecimal price;

    private String category;

    private String brand;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}