package com.innowise.orderservice.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

  @Bean
  public RequestInterceptor jwtTokenPropagator() {
    return template -> {
      ServletRequestAttributes attributes =
              (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attributes != null) {
        String authHeader = attributes.getRequest().getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
          template.header("Authorization", authHeader);
        }
      }
    };
  }
}