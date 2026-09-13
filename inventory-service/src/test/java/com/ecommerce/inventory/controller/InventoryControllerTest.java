package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.CreateInventoryRequest;
import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.StockAdjustmentRequest;
import com.ecommerce.inventory.dto.UpdateInventoryRequest;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.inventory.service.InventoryOutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.http.MediaType;

import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import io.micrometer.tracing.Tracer; 

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private InventoryOutboxService inventoryOutboxService;

    @MockBean
    private Tracer tracer;

    @Test
    void shouldGetInventory() throws Exception {

        UUID productId = UUID.randomUUID();

        InventoryResponse response =
                InventoryResponse.builder()
                        .productId(productId)
                        .availableStock(100)
                        .reservedStock(0)
                        .build();

        when(inventoryService.getInventory(productId))
                .thenReturn(response);

        mockMvc.perform(
                        get(
                                "/api/v1/admin/inventory/{productId}",
                                productId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.productId")
                                .value(productId.toString())
                )
                .andExpect(
                        jsonPath("$.availableStock")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.reservedStock")
                                .value(0)
                );
    }

    @Test
    void shouldCreateInventory() throws Exception {

        UUID productId = UUID.randomUUID();

        CreateInventoryRequest request =
                new CreateInventoryRequest();

        request.setProductId(productId);
        request.setAvailableStock(100);

        InventoryResponse response =
                InventoryResponse.builder()
                        .productId(productId)
                        .availableStock(100)
                        .reservedStock(0)
                        .build();

        when(
                inventoryService.createInventory(
                        any(CreateInventoryRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/admin/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.productId")
                                .value(productId.toString())
                )
                .andExpect(
                        jsonPath("$.availableStock")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.reservedStock")
                                .value(0)
                );
    }

    @Test
    void shouldAdjustInventory() throws Exception {

        UUID productId = UUID.randomUUID();

        StockAdjustmentRequest request = new StockAdjustmentRequest();

        request.setAdjustment(100);
        request.setReason(com.ecommerce.inventory.entity.StockAdjustmentReason.STOCK_RECEIVED);

        InventoryResponse response =
                InventoryResponse.builder()
                        .productId(productId)
                        .availableStock(200)
                        .reservedStock(0)
                        .build();

        when(
                inventoryService.adjustStock(
                        eq(productId),
                        any(StockAdjustmentRequest.class), eq("admin")
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/v1/admin/inventory/{productId}/adjustments",
                                productId
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.productId")
                                .value(productId.toString())
                )
                .andExpect(
                        jsonPath("$.availableStock")
                                .value(200)
                )
                .andExpect(
                        jsonPath("$.reservedStock")
                                .value(0)
                );
    }
}
