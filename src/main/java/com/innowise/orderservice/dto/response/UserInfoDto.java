package com.innowise.orderservice.dto.response;

public record UserInfoDto(
        Long id,
        String name,
        String surname,
        String email,
        Boolean active
) {}