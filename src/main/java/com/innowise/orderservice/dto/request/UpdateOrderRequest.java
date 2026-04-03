package com.innowise.orderservice.dto.request;

import com.innowise.orderservice.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderRequest(
        @NotNull OrderStatus status
) {}