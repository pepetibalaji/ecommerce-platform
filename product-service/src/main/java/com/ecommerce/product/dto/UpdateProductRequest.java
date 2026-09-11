package com.ecommerce.product.dto;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.NotNull;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import com.ecommerce.product.validation.IsoCurrency;
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
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 10000, message = "Description must not exceed 10000 characters")
    private String description;

    @NotNull(message = "Price is required")

    @Positive(message = "Price must be positive")
    @Digits(integer = 18, fraction = 4, message = "Price supports at most 18 integer digits and 4 decimal places")
    private BigDecimal price;

    @NotBlank(message = "Currency is required")
    @IsoCurrency
    @Builder.Default
    private String currency = "USD";

    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;
    
    @Size(max = 100, message = "Brand must not exceed 100 characters")
    private String brand;

    /** Sellers may temporarily make their own catalog item unavailable for purchase. */
    private Boolean active;

    @Size(max = 10, message = "A product can have at most 10 image URLs")
    private List<@NotBlank(message = "Image URL must not be blank")
                 @URL(protocol = "https", message = "Image URLs must be valid HTTPS URLs")
                 @Size(max = 2048, message = "Image URL must not exceed 2048 characters") String> imageUrls;
}
