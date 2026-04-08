package com.innowise.orderservice.client;

import com.innowise.orderservice.config.FeignConfig;
import com.innowise.orderservice.dto.response.UserInfoDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "user-service",
        url = "${user.service.url}",
        configuration = FeignConfig.class,
        fallbackFactory = UserClientFallbackFactory.class
)
public interface UserClient {

  @GetMapping("/api/users/by-email/{email}")
  UserInfoDto getUserByEmail(@PathVariable String email);

  @GetMapping("/api/users/{id}")
  UserInfoDto getUserById(@PathVariable Long id);
}