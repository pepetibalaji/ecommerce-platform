package com.ecommerce.auth.dto;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import lombok.Data;
@Data public class ReplaceRolesRequest { @NotEmpty private Set<String> roles; }
