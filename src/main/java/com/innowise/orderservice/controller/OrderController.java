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

/**
 * REST controller responsible for managing orders.
 * <p>
 * Provides endpoints for creating, retrieving, updating and deleting orders.
 * Delegates business logic to {@link OrderService}.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService orderService;

  /**
   * Creates a new order.
   * <p>
   * Accepts user email and list of items, validates input and delegates creation
   * to the service layer.
   *
   * @param request DTO containing user email and items to order.
   * @return created order including user info.
   *
   * @see OrderService#create(CreateOrderRequest)
   */
  @PostMapping
  public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
    return ResponseEntity.ok(orderService.create(request));
  }

  /**
   * Retrieves an order by its ID.
   *
   * @param id identifier of the order.
   * @return order response including user info.
   *
   * @see OrderService#getById(Long)
   */
  @GetMapping("/{id}")
  public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
    return ResponseEntity.ok(orderService.getById(id));
  }

  /**
   * Retrieves a paginated list of orders with optional filtering.
   * <p>
   * Supported filters:
   * <ul>
   *   <li>list of order statuses</li>
   *   <li>date range (from–to)</li>
   * </ul>
   *
   * @param pageable pagination parameters.
   * @param statuses optional list of statuses to filter by.
   * @param from optional start date for filtering.
   * @param to optional end date for filtering.
   * @return page of order responses including user info.
   *
   * @see OrderService#getAll(Pageable, List, LocalDateTime, LocalDateTime)
   */
  @GetMapping
  public ResponseEntity<Page<OrderResponse>> getAll(
          Pageable pageable,
          @RequestParam(required = false) List<OrderStatus> statuses,
          @RequestParam(required = false) LocalDateTime from,
          @RequestParam(required = false) LocalDateTime to) {

    return ResponseEntity.ok(orderService.getAll(pageable, statuses, from, to));
  }

  /**
   * Retrieves all orders belonging to a specific user.
   *
   * @param userId identifier of the user.
   * @return list of order responses including user info.
   *
   * @see OrderService#getByUserId(Long)
   */
  @GetMapping("/user/{userId}")
  public ResponseEntity<List<OrderResponse>> getByUserId(@PathVariable Long userId) {
    return ResponseEntity.ok(orderService.getByUserId(userId));
  }

  /**
   * Updates an existing order.
   *
   * @param id identifier of the order.
   * @param request DTO containing new status.
   * @return updated order including user info.
   *
   * @see OrderService#update(Long, UpdateOrderRequest)
   */
  @PutMapping("/{id}")
  public ResponseEntity<OrderResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody UpdateOrderRequest request) {
    return ResponseEntity.ok(orderService.update(id, request));
  }

  /**
   * Soft Delete an order by its ID.
   *
   * @param id identifier of the order.
   *
   * @see OrderService#delete(Long)
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    orderService.delete(id);
    return ResponseEntity.noContent().build();
  }
}