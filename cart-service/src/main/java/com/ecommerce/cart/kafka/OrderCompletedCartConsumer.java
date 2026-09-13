package com.ecommerce.cart.kafka;

import com.ecommerce.cart.service.CartService;
import com.ecommerce.common.events.order.OrderCompletedEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCompletedCartConsumer {
    private final CartService cartService;
    public OrderCompletedCartConsumer(CartService cartService) { this.cartService = cartService; }

    @KafkaListener(topics = KafkaTopics.ORDER_COMPLETED, groupId = "${spring.application.name}-cart-lifecycle")
    public void onOrderCompleted(OrderCompletedEvent event) {
        if (event == null || event.getEventId() == null || event.getUserId() == null) return;
        cartService.removePurchasedItems(event.getUserId().toString(), event.getEventId(), event.getItems());
    }
}
