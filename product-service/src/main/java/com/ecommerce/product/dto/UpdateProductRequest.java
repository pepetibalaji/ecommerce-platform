package com.ecommerce.product.dto;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.NotNull;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class UpdateProductRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")

    @Positive(message = "Price must be positive")
    private BigDecimal price;

    private String category;
    
    private String brand;

    @Size(max = 10, message = "A product can have at most 10 image URLs")
    private List<@URL(protocol = "https", message = "Image URLs must be valid HTTPS URLs")
                 @Size(max = 2048, message = "Image URL must not exceed 2048 characters") String> imageUrls;
}
