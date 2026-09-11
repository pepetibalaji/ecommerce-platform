package com.ecommerce.product.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Map;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    OpenApiCustomizer catalogueContractExamples() {
        return api -> {
            if (api.getPaths() == null) return;
            api.getPaths().forEach((path, item) -> {
                if (path.startsWith("/api/v1/products") && item.getGet() != null)
                    item.getGet().setSecurity(List.of());
                item.readOperations().forEach(operation -> {
                    error(operation, "400", "Bad Request", "Request validation failed");
                    error(operation, "401", "Unauthorized", "Authentication required");
                    error(operation, "403", "Forbidden", "Access denied");
                    error(operation, "404", "Not Found", "Product not found");
                    error(operation, "409", "Conflict", "Product was modified. Reload and retry.");
                    error(operation, "429", "Too Many Requests", "Too many requests. Please try again later.");
                    error(operation, "503", "Service Unavailable", "Service temporarily unavailable");
                });
                if (item.getPost() != null && (path.equals("/api/v1/admin/products") || path.equals("/api/v1/seller/products")))
                    requestExample(item.getPost(), productRequestExample());
                if (item.getPut() != null && path.endsWith("/products/{productId}"))
                    requestExample(item.getPut(), productRequestExample());
            });
            var catalogue = api.getPaths().get("/api/v1/products");
            if (catalogue == null || catalogue.getGet() == null) return;
            Operation browse = catalogue.getGet();
            browse.setDescription("Public active catalogue. All filters combine. q is a literal case-insensitive substring across name, brand, description. Relevance ranks exact name, name prefix, name substring, exact brand, brand substring, description; ties use product ID. Without q, relevance uses newest. Category and brand are trimmed case-insensitive exact matches. Price bounds are inclusive and independent.");
            if (browse.getParameters() != null) browse.getParameters().forEach(parameter -> {
                switch (parameter.getName()) {
                    case "q" -> { parameter.setExample("phone"); parameter.setDescription("Literal substring, maximum 200 characters. Blank means no search."); }
                    case "category" -> { parameter.setExample("Mobile"); parameter.setDescription("Case-insensitive exact category, maximum 100 characters."); }
                    case "brand" -> { parameter.setExample("Acme"); parameter.setDescription("Case-insensitive exact brand, maximum 100 characters."); }
                    case "sort" -> { parameter.setExample("relevance"); parameter.setSchema(new Schema<String>().type("string")._default("newest")._enum(List.of("relevance", "newest", "price_asc", "price_desc", "name_asc", "name_desc"))); }
                    case "page" -> { parameter.setExample(0); parameter.setDescription("Zero-based page number, at least 0."); }
                    case "size" -> { parameter.setExample(10); parameter.setDescription("Page size, 1 through 100."); }
                    case "minPrice" -> { parameter.setExample(50); parameter.setDescription("Inclusive nonnegative lower price bound."); }
                    case "maxPrice" -> { parameter.setExample(150); parameter.setDescription("Inclusive nonnegative upper price bound; must be >= minPrice."); }
                    default -> { }
                }
            });
            var response = browse.getResponses().get("200");
            if (response != null && response.getContent() != null) response.getContent().values().forEach(media -> media.setExample(Map.of(
                    "content", List.of(productResponseExample()), "number", 0, "size", 10,
                    "totalElements", 1, "totalPages", 1, "first", true, "last", true, "empty", false, "numberOfElements", 1)));
        };
    }

    private static void requestExample(Operation operation, Object example) {
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null)
            operation.getRequestBody().getContent().values().forEach(media -> media.setExample(example));
    }

    private static Map<String, Object> productRequestExample() {
        return Map.of("name", "Phone", "description", "Plain-text product description", "price", 100.25,
                "currency", "USD", "category", "Mobile", "brand", "Acme",
                "imageUrls", List.of("https://cdn.example.com/products/phone.jpg"));
    }

    private static Map<String, Object> productResponseExample() {
        Map<String, Object> product = new java.util.LinkedHashMap<>(productRequestExample());
        product.put("id", "11111111-1111-1111-1111-111111111111");
        product.put("sellerId", "22222222-2222-2222-2222-222222222222");
        product.put("active", true);
        product.put("createdAt", "2026-09-10T10:15:30Z");
        product.put("updatedAt", "2026-09-10T10:15:30Z");
        return product;
    }

    private static void error(Operation operation, String status, String title, String message) {
        if (operation.getResponses() == null || operation.getResponses().containsKey(status)) return;
        operation.getResponses().addApiResponse(status, new ApiResponse().description(title).content(new Content()
                .addMediaType("application/json", new MediaType().example(Map.of("timestamp", "2026-09-10T10:15:30",
                        "status", Integer.parseInt(status), "error", title, "message", message, "path", "/api/v1/products")))));
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Product Service API")
                                .version("1.0")
                                .description("Ecommerce Product Service APIs")
                )
                .servers(List.of(
                        new Server().url("/").description("Relative server for Gateway Swagger aggregation")
                ))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(SECURITY_SCHEME_NAME)
                )
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SECURITY_SCHEME_NAME,
                                        new SecurityScheme()
                                                .name(SECURITY_SCHEME_NAME)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .in(SecurityScheme.In.HEADER)
                                )
                );
    }
}
