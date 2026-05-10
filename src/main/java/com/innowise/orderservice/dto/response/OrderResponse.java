package com.innowise.orderservice.dto.response;

public record OrderResponse(
        OrderDto order,
        UserInfoDto user
) {}