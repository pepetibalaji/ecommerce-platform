package com.ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateMeRequest {

    @NotBlank
    @Size(max = 150)
    private String name;
}