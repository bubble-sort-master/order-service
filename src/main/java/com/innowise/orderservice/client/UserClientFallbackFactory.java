package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.response.UserInfoDto;
import com.innowise.orderservice.exception.UserNotFoundException;
import com.innowise.orderservice.exception.UserServiceException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

  @Override
  public UserClient create(Throwable cause) {
    return new UserClient() {
      @Override
      public UserInfoDto getUserByEmail(String email) {
        handleSingleUserException(cause, email, null);
        return null;
      }

      @Override
      public UserInfoDto getUserById(Long id) {
        handleSingleUserException(cause, null, id);
        return null;
      }

      @Override
      public List<UserInfoDto> getUsersByIds(List<Long> ids) {
        handleServiceUnavailable(cause);
        return List.of();
      }
    };
  }

  private void handleSingleUserException(Throwable cause, String email, Long id) {
    FeignException feign = unwrapFeignException(cause);

    if (feign != null) {
      int status = feign.status();

      if (status == 404) {
        throw email != null
                ? new UserNotFoundException(email)
                : new UserNotFoundException(id);
      }

      if (status == 403) {
        throw new AccessDeniedException("Access denied");
      }
    }

    handleServiceUnavailable(cause);
  }

  private void handleServiceUnavailable(Throwable cause) {
    if (cause instanceof CallNotPermittedException ||
            cause.getCause() instanceof CallNotPermittedException) {
      throw new UserServiceException("User service circuit breaker is OPEN");
    }

    throw new UserServiceException("User service is unavailable: " + cause.getMessage());
  }

  private FeignException unwrapFeignException(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof FeignException feignException) {
        return feignException;
      }
      current = current.getCause();
    }
    return null;
  }
}