package com.innowise.orderservice.controller;

import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  @PostMapping
  public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
    return ResponseEntity.ok(orderService.create(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
    return ResponseEntity.ok(orderService.getById(id));
  }

  @GetMapping
  public ResponseEntity<Page<OrderResponse>> getAll(
          Pageable pageable,
          @RequestParam(required = false) List<OrderStatus> statuses,
          @RequestParam(required = false) LocalDateTime from,
          @RequestParam(required = false) LocalDateTime to) {

    return ResponseEntity.ok(orderService.getAll(pageable, statuses, from, to));
  }

  @GetMapping("/user/{userId}")
  public ResponseEntity<List<OrderResponse>> getByUserId(@PathVariable Long userId) {
    return ResponseEntity.ok(orderService.getByUserId(userId));
  }

  @PutMapping("/{id}")
  public ResponseEntity<OrderResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody UpdateOrderRequest request) {
    return ResponseEntity.ok(orderService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    orderService.delete(id);
    return ResponseEntity.noContent().build();
  }
}