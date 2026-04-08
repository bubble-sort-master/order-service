package com.innowise.orderservice.client;

import com.innowise.orderservice.config.FeignConfig;
import com.innowise.orderservice.dto.response.UserInfoDto;
import com.innowise.orderservice.exception.UserServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "user-service",
        url = "${user.service.url}",
        configuration = FeignConfig.class
)
public interface UserClient {

  @CircuitBreaker(name = "userService", fallbackMethod = "fallback")
  @GetMapping("/api/users/by-email/{email}")
  UserInfoDto getUserByEmail(@PathVariable String email);

  @CircuitBreaker(name = "userService", fallbackMethod = "fallback")
  @GetMapping("/api/users/{id}")
  UserInfoDto getUserById(@PathVariable Long id);

  default UserInfoDto fallback(Throwable ex) {
    throw new UserServiceException("User service is unavailable: " + ex.getMessage());
  }
}