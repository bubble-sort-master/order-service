package com.innowise.orderservice.event;

import java.time.LocalDateTime;

public record PaymentEvent(
        Long orderId,
        PaymentStatus status,
        LocalDateTime timestamp
) {}