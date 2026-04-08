package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.response.UserInfoDto;
import com.innowise.orderservice.exception.UserNotFoundException;
import com.innowise.orderservice.exception.UserServiceException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

  @Override
  public UserClient create(Throwable cause) {

    return new UserClient() {
      @Override
      public UserInfoDto getUserByEmail(String email) {
        return handleException(cause, email, null);
      }

      @Override
      public UserInfoDto getUserById(Long id) {
        return handleException(cause, null, id);
      }
    };
  }

  private UserInfoDto handleException(Throwable cause, String email, Long id) {
    FeignException feignException = unwrapFeignException(cause);

    if (feignException != null) {
      int status = feignException.status();

      if (status == 404) {
        if (email != null) {
          throw new UserNotFoundException(email);
        } else {
          throw new UserNotFoundException(id);
        }
      }
      if (status == 403) {
        throw new AccessDeniedException("Access denied");
      }
    }

    if (cause instanceof CallNotPermittedException ||
            (cause.getCause() instanceof CallNotPermittedException)) {
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