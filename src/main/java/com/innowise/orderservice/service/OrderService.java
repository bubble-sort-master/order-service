package com.innowise.orderservice.service;

import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderService {

  OrderResponse create(CreateOrderRequest request);

  OrderResponse getById(Long id);

  Page<OrderResponse> getAll(Pageable pageable, List<OrderStatus> statuses,
                             LocalDateTime from, LocalDateTime to);

  List<OrderResponse> getByUserId(Long userId);

  OrderResponse update(Long id, UpdateOrderRequest request);

  void delete(Long id);
}