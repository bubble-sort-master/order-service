package com.innowise.orderservice.service.impl;

import com.innowise.orderservice.client.UserClient;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.dto.response.UserInfoDto;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderItem;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Money;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.specification.OrderSpecifications;
import com.innowise.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

  private final OrderRepository orderRepository;
  private final ItemRepository itemRepository;
  private final UserClient userClient;
  private final OrderMapper mapper;

  @Override
  @Transactional
  public OrderResponse create(CreateOrderRequest req) {
    UserInfoDto user = userClient.getUserByEmail(req.userEmail());

    Order order = new Order();
    order.setUserId(user.id());
    order.setStatus(OrderStatus.PENDING);

    Map<Long, Item> itemMap = getItemsMap(req.items());

    List<OrderItem> orderItems = buildOrderItems(order, req.items(), itemMap);
    Money totalPrice = calculateTotal(orderItems);

    order.setOrderItems(orderItems);
    order.setTotalPrice(totalPrice);

    Order saved = orderRepository.save(order);

    return new OrderResponse(mapper.toDto(saved), user);
  }

  @Override
  public OrderResponse getById(Long id) {
    Order order = orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));

    UserInfoDto user = userClient.getUserById(order.getUserId());

    return new OrderResponse(mapper.toDto(order), user);
  }

  @Override
  public Page<OrderResponse> getAll(Pageable pageable,
                                    List<OrderStatus> statuses,
                                    LocalDateTime from,
                                    LocalDateTime to) {

    var spec = OrderSpecifications.searchByDateRangeAndStatus(statuses, from, to);

    return orderRepository.findAll(spec, pageable)
            .map(order -> {
              UserInfoDto user = userClient.getUserById(order.getUserId());
              return new OrderResponse(mapper.toDto(order), user);
            });
  }

  @Override
  public List<OrderResponse> getByUserId(Long userId) {
    List<Order> orders = orderRepository.findByUserId(userId);

    return orders.stream()
            .map(order -> {
              UserInfoDto user = userClient.getUserById(order.getUserId());
              return new OrderResponse(mapper.toDto(order), user);
            })
            .toList();
  }

  @Override
  @Transactional
  public OrderResponse update(Long id, UpdateOrderRequest req) {
    Order order = orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));

    order.setStatus(req.status());
    Order saved = orderRepository.save(order);

    UserInfoDto user = userClient.getUserById(saved.getUserId());

    return new OrderResponse(mapper.toDto(saved), user);
  }

  @Override
  @Transactional
  public void delete(Long id) {
    Order order = orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    orderRepository.delete(order);
  }

  private Map<Long, Item> getItemsMap(List<OrderItemRequest> itemRequests) {
    List<Long> itemIds = itemRequests.stream()
            .map(OrderItemRequest::itemId)
            .distinct()
            .toList();

    List<Item> items = itemRepository.findAllById(itemIds);

    if (items.size() != itemIds.size()) {
      Set<Long> foundIds = items.stream().map(Item::getId).collect(Collectors.toSet());
      List<Long> missingIds = itemIds.stream()
              .filter(id -> !foundIds.contains(id))
              .toList();
      throw new ItemNotFoundException(missingIds);
    }

    return items.stream()
            .collect(Collectors.toMap(Item::getId, Function.identity()));
  }

  private List<OrderItem> buildOrderItems(Order order,
                                          List<OrderItemRequest> requests,
                                          Map<Long, Item> itemMap) {
    return requests.stream()
            .map(req -> {
              Item item = itemMap.get(req.itemId());

              OrderItem oi = new OrderItem();
              oi.setOrder(order);
              oi.setItem(item);
              oi.setQuantity(req.quantity());

              return oi;
            })
            .toList();
  }

  private Money calculateTotal(List<OrderItem> orderItems) {
    Money total = Money.zero();

    for (OrderItem oi : orderItems) {
      total = total.add(oi.getItem().getPrice().multiply(oi.getQuantity()));
    }
    return total;
  }
}