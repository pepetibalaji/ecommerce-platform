package com.ecommerce.order.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.ecommerce.order.dto.CreateOrderItemRequest;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.common.security.filter.JwtAuthenticationFilter;
import com.ecommerce.common.security.jwt.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtService jwtService;

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "USER")
    void shouldCreateOrder() throws Exception {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.randomUUID());
        item.setQuantity(2);
        item.setPrice(new BigDecimal("100.00"));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        OrderResponse response = new OrderResponse(
                UUID.randomUUID(),
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new BigDecimal("200.00"),
                OrderStatus.PENDING,
                LocalDateTime.now(),
                List.of(new OrderItemResponse(UUID.randomUUID(), item.getProductId(), 2, item.getPrice()))
        );

        when(orderService.createOrder(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "USER")
    void shouldGetMyOrders() throws Exception {
        when(orderService.getMyOrders(any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "USER")
    void shouldCancelOrder() throws Exception {
        UUID orderId = UUID.randomUUID();

        OrderResponse response = new OrderResponse(
                orderId,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new BigDecimal("200.00"),
                OrderStatus.CANCELLED,
                LocalDateTime.now(),
                List.of()
        );

        when(orderService.cancelOrder(any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/orders/" + orderId + "/cancel")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111", roles = "ADMIN")
    void shouldUpdateOrderStatus() throws Exception {
        UUID orderId = UUID.randomUUID();

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CONFIRMED);

        OrderResponse response = new OrderResponse(
                orderId,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new BigDecimal("200.00"),
                OrderStatus.CONFIRMED,
                LocalDateTime.now(),
                List.of()
        );

        when(orderService.updateOrderStatus(any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/orders/" + orderId + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}