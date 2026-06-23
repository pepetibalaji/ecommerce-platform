package com.ecommerce.common.events.notification;

import com.ecommerce.common.events.core.AbstractDomainEvent;
import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.core.EventTypes;
import java.util.UUID;

public class NotificationRequestedEvent extends AbstractDomainEvent {

    private UUID notificationId;
    private UUID userId;
    private UUID orderId;
    private String channel;
    private String subject;
    private String message;

    public NotificationRequestedEvent() {
        super(
                EventTypes.NOTIFICATION_REQUESTED,
                EventSources.NOTIFICATION_SERVICE,
                null,
                null
        );
    }

    public NotificationRequestedEvent(
            UUID notificationId,
            UUID userId,
            UUID orderId,
            String channel,
            String subject,
            String message,
            String correlationId,
            String traceId
    ) {
        super(
                EventTypes.NOTIFICATION_REQUESTED,
                EventSources.NOTIFICATION_SERVICE,
                correlationId,
                traceId
        );
        this.notificationId = notificationId;
        this.userId = userId;
        this.orderId = orderId;
        this.channel = channel;
        this.subject = subject;
        this.message = message;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(UUID notificationId) {
        this.notificationId = notificationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}