package com.innowise.orderservice.repository;

import com.innowise.orderservice.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

  @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
  Optional<Order> findById(Long id);

  List<Order> findByUserId(Long userId);

  @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
  Page<Order> findAll(Specification<Order> spec, Pageable pageable);
}