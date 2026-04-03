package com.innowise.orderservice.service;

import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service interface for managing orders.
 * Provides operations for creating, retrieving, updating and deleting orders.
 */
public interface OrderService {

  /**
   * Creates a new order for the user identified by email.
   * <p>

   * @param request DTO containing user email and list of items to order.
   * @return full order response including user info.
   *
   * @throws com.innowise.orderservice.exception.ItemNotFoundException
   *         if any of the requested items does not exist.
   * @throws com.innowise.orderservice.exception.UserServiceException
   *         if user cannot be retrieved from User Service.
   */
  OrderResponse create(CreateOrderRequest request);

  /**
   * Retrieves an order by its ID.
   * <p>
   * Loads the order entity and fetches user information from User Service.
   *
   * @param id order identifier.
   * @return order response including user info.
   *
   * @throws com.innowise.orderservice.exception.OrderNotFoundException
   *         if order with the given ID does not exist.
   * @throws com.innowise.orderservice.exception.UserServiceException
   *         if user cannot be retrieved from User Service.
   */
  OrderResponse getById(Long id);

  /**
   * Retrieves a paginated list of orders filtered by:
   * <ul>
   *   <li>order statuses (optional)</li>
   *   <li>date range (optional)</li>
   * </ul>
   * <p>
   * For each order, user information is fetched from User Service.
   *
   * @param pageable pagination parameters.
   * @param statuses optional list of statuses to filter by.
   * @param from optional start date for filtering.
   * @param to optional end date for filtering.
   * @return page of order responses including user info.
   */
  Page<OrderResponse> getAll(Pageable pageable,
                             List<OrderStatus> statuses,
                             LocalDateTime from,
                             LocalDateTime to);

  /**
   * Retrieves all orders belonging to a specific user.
   * <p>
   * For each order, user information is fetched from User Service.
   *
   * @param userId identifier of the user.
   * @return list of order responses including user info.
   */
  List<OrderResponse> getByUserId(Long userId);

  /**
   * Updates an existing order.
   *
   * @param id order identifier.
   * @param request DTO containing new order status.
   * @return updated order response including user info.
   *
   * @throws com.innowise.orderservice.exception.OrderNotFoundException
   *         if order with the given ID does not exist.
   * @throws com.innowise.orderservice.exception.UserServiceException
   *         if user cannot be retrieved from User Service.
   */
  OrderResponse update(Long id, UpdateOrderRequest request);

  /**
   * Soft deletes an order by its ID (marks as deleted).
   *
   * @param id order identifier.
   *
   * @throws com.innowise.orderservice.exception.OrderNotFoundException
   *         if order with the given ID does not exist.
   */
  void delete(Long id);
}
