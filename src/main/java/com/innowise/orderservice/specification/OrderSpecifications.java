package com.innowise.orderservice.specification;

import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

public class OrderSpecifications {

  private OrderSpecifications() {}

  public static Specification<Order> createdAtBetween(LocalDateTime from, LocalDateTime to) {
    return (root, query, cb) -> {
      if (from == null && to == null) return cb.conjunction();
      if (from != null && to != null) {
        return cb.between(root.get("createdAt"), from, to);
      }
      if (from != null) return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
      return cb.lessThanOrEqualTo(root.get("createdAt"), to);
    };
  }

  public static Specification<Order> statusIn(List<OrderStatus> statuses) {
    return (root, query, cb) ->
            (statuses == null || statuses.isEmpty())
                    ? cb.conjunction()
                    : root.get("status").in(statuses);
  }

  public static Specification<Order> searchByDateRangeAndStatus(List<OrderStatus> statuses,
                                                                LocalDateTime from,
                                                                LocalDateTime to) {
    return Specification.where(createdAtBetween(from, to))
            .and(statusIn(statuses));
  }
}