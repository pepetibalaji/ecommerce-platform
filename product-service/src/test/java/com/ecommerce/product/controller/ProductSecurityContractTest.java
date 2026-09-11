package com.ecommerce.product.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.product.config.SecurityConfig;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.exception.ProductExceptionHandler;
import com.ecommerce.product.exception.SellerEligibilityUnavailableException;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.outbox.ProductOutboxService;
import com.ecommerce.product.outbox.ProductReconciliationService;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.product.service.SellerEligibilityClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Actual HTTP/method security and ownership service logic; repositories and dependencies are mocked. */
@WebMvcTest(controllers = ProductController.class, properties = "spring.cloud.config.enabled=false")
@Import({SecurityConfig.class, ProductService.class, ProductMapper.class, ProductExceptionHandler.class})
class ProductSecurityContractTest {
  private static final UUID PRODUCT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID SELLER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_SELLER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final String PAYLOAD = """
      {"name":"Updated product","price":49.95,"currency":"USD","imageUrls":[]}
      """;

  @Autowired private MockMvc mvc;
  @MockitoBean private ProductRepository products;
  @MockitoBean private ProductOutboxService outbox;
  @MockitoBean private ProductReconciliationService reconciliation;
  @MockitoBean private SellerEligibilityClient eligibility;
  @MockitoBean private MongoTemplate mongoTemplate;
  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private JwtAuthenticationConverter jwtAuthenticationConverter;

  @Test
  void anonymousCatalogueListDetailAndFacetsReachTheService() throws Exception {
    when(products.searchPublicProducts(any(), any(), any(), any(), any(), any(Pageable.class)))
        .thenReturn(Page.empty());
    when(products.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq("products"), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));
    mvc.perform(get("/api/v1/products")).andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray()).andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/v1/products/{id}", PRODUCT_ID)).andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()));
    mvc.perform(get("/api/v1/products/facets")).andExpect(status().isOk())
        .andExpect(jsonPath("$.categories").isArray());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/products/not-a-uuid", "/api/v1/products?page=invalid"})
  void malformedPublicIdentifiersAndPagingAreBadRequests(String path) throws Exception {
    mvc.perform(get(path)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
    verifyNoInteractions(products, outbox);
  }

  @Test
  void anonymousAndCustomerWritesCannotReachPersistence() throws Exception {
    mvc.perform(post("/api/v1/seller/products").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/seller/products").with(identity(SELLER_ID, "CUSTOMER"))
        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isForbidden());
    verifyNoInteractions(products, outbox, eligibility);
  }

  @Test
  void adminCreationRequiresExplicitValidSellerUuid() throws Exception {
    mvc.perform(post("/api/v1/admin/products").with(identity(SELLER_ID, "ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/admin/products").with(identity(SELLER_ID, "ADMIN"))
        .param("sellerId", "invalid").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(products, eligibility, outbox);
  }

  @Test
  void sellerCannotUpdateOrArchiveAnotherSellersProduct() throws Exception {
    when(products.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
    mvc.perform(put("/api/v1/seller/products/{id}", PRODUCT_ID).with(identity(OTHER_SELLER_ID, "SELLER"))
        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/seller/products/{id}", PRODUCT_ID).with(identity(OTHER_SELLER_ID, "SELLER")))
        .andExpect(status().isNotFound());
    verify(products, never()).save(any(Product.class));
    verifyNoInteractions(outbox);
  }

  @Test
  void ownerCanUpdateTheirProductAndTheOwnerCannotBeChangedThroughBody() throws Exception {
    when(products.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
    when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    String payload = """
        {"name":"Updated product","price":49.95,"currency":"USD","sellerId":"%s"}
        """.formatted(OTHER_SELLER_ID);
    mvc.perform(put("/api/v1/seller/products/{id}", PRODUCT_ID).with(identity(SELLER_ID, "SELLER"))
        .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk())
        .andExpect(jsonPath("$.sellerId").value(SELLER_ID.toString()))
        .andExpect(jsonPath("$.name").value("Updated product"));
    verify(outbox).enqueue(any(Product.class), eq("product.updated"));
  }

  @Test
  void adminCanManageAProductOwnedByAnotherAccount() throws Exception {
    when(products.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
    when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    mvc.perform(delete("/api/v1/admin/products/{id}", PRODUCT_ID).with(identity(OTHER_SELLER_ID, "ADMIN")))
        .andExpect(status().isNoContent());
    verify(outbox).enqueue(any(Product.class), eq("product.archived"));
  }

  @Test
  void inactiveProductIsHiddenFromPublicReads() throws Exception {
    Product inactive = product();
    inactive.setActive(false);
    when(products.findById(PRODUCT_ID)).thenReturn(Optional.of(inactive));
    mvc.perform(get("/api/v1/products/{id}", PRODUCT_ID)).andExpect(status().isNotFound());
  }

  @Test
  void unavailableEligibilityPreventsCreateAndReturns503() throws Exception {
    doThrow(new SellerEligibilityUnavailableException()).when(eligibility).requireEligible(SELLER_ID);
    mvc.perform(post("/api/v1/seller/products").with(identity(SELLER_ID, "SELLER"))
        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value(503));
    verifyNoInteractions(products, outbox);
  }

  @Test
  void databaseOutageUses503WithoutExposingConnectionDetails() throws Exception {
    when(products.findById(PRODUCT_ID))
        .thenThrow(new DataAccessResourceFailureException("mongodb://private:secret@internal"));
    mvc.perform(get("/api/v1/products/{id}", PRODUCT_ID)).andExpect(status().isServiceUnavailable())
        .andExpect(content().string(not(containsString("private:secret"))))
        .andExpect(jsonPath("$.status").value(503));
  }

  @Test
  void optimisticWriteConflictReturns409WithoutAnOutboxEvent() throws Exception {
    when(products.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
    when(products.save(any(Product.class))).thenThrow(new OptimisticLockingFailureException("version conflict"));
    mvc.perform(put("/api/v1/seller/products/{id}", PRODUCT_ID).with(identity(SELLER_ID, "SELLER"))
        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
    verifyNoInteractions(outbox);
  }

  @Test
  void adminWithoutSellerRoleCannotUseSellerBulkCreation() throws Exception {
    mvc.perform(post("/api/v1/seller/products/bulk").with(identity(SELLER_ID, "ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content("[" + PAYLOAD + "]"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(products, outbox, eligibility);
  }

  @ParameterizedTest
  @ValueSource(strings = {"[]", "[null]", "[{\"name\":\"\",\"price\":0,\"currency\":\"USD\"}]"})
  void bulkValidationRejectsEmptyNullOrInvalidItemsWithFieldErrors(String body) throws Exception {
    mvc.perform(post("/api/v1/seller/products/bulk").with(identity(SELLER_ID, "SELLER"))
        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    verifyNoInteractions(products, outbox, eligibility);
  }

  private RequestPostProcessor identity(UUID id, String role) {
    return jwt().jwt(jwt -> jwt.claim("userId", id.toString()).claim("roles", List.of(role)))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  private Product product() {
    return Product.builder().id(PRODUCT_ID).sellerId(SELLER_ID).active(true)
        .name("Product").price(new BigDecimal("49.95")).currency("USD").imageUrls(List.of())
        .createdAt(Instant.parse("2026-09-10T00:00:00Z"))
        .updatedAt(Instant.parse("2026-09-10T00:00:00Z")).build();
  }
}
