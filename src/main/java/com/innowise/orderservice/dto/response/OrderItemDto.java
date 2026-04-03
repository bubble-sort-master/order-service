package com.innowise.orderservice.dto.response;

import java.math.BigDecimal;

public record OrderItemDto(
        Long itemId,
        String itemName,
        Integer quantity,
        BigDecimal price
) {}