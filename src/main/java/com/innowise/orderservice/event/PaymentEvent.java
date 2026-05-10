package com.innowise.orderservice.event;

import java.time.LocalDateTime;

public record PaymentEvent(
        String eventType,
        Long orderId,
        PaymentStatus status,
        LocalDateTime timestamp
) {
    public static final String TYPE_CREATE_PAYMENT = "CREATE_PAYMENT";

    public PaymentEvent(Long orderId, PaymentStatus status, LocalDateTime timestamp) {
        this(TYPE_CREATE_PAYMENT, orderId, status, timestamp);
    }
}