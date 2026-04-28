package com.innowise.orderservice.service.impl;

import com.innowise.orderservice.client.UserClient;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.dto.response.UserInfoDto;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderItem;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.event.PaymentEvent;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.exception.UserNotFoundException;
import com.innowise.orderservice.mapper.OrderItemMapper;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Money;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.service.OrderService;
import com.innowise.orderservice.specification.OrderSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
  private final OrderItemMapper orderItemMapper;

  @Override
  @Transactional
  public OrderResponse create(CreateOrderRequest req) {
    UserInfoDto user = userClient.getUserByEmail(req.userEmail());

    Order order = mapper.toEntity(req);
    order.setUserId(user.id());
    order.setStatus(OrderStatus.PENDING);

    Map<Long, Item> itemMap = getItemsMap(req.items());

    List<OrderItem> orderItems = req.items().stream()
            .map(itemReq -> {
              Item item = itemMap.get(itemReq.itemId());
              OrderItem oi = orderItemMapper.toEntity(itemReq);
              oi.setOrder(order);
              oi.setItem(item);
              return oi;
            })
            .toList();

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
    Page<Order> orderPage = orderRepository.findAll(spec, pageable);

    Set<Long> userIds = orderPage.getContent().stream()
            .map(Order::getUserId)
            .collect(Collectors.toSet());

    Map<Long, UserInfoDto> userMap = userIds.isEmpty()
            ? Map.of()
            : userClient.getUsersByIds(new ArrayList<>(userIds))
            .stream()
            .collect(Collectors.toMap(UserInfoDto::id, Function.identity()));

    return orderPage.map(order -> {
      UserInfoDto user = userMap.get(order.getUserId());
      if (user == null) {
        throw new UserNotFoundException(order.getUserId());
      }
      return new OrderResponse(mapper.toDto(order), user);
    });
  }

  @Override
  public List<OrderResponse> getByUserId(Long userId) {
    List<Order> orders = orderRepository.findByUserId(userId);

    if (orders.isEmpty()) {
      return List.of();
    }

    UserInfoDto user = userClient.getUserById(userId);

    return orders.stream()
            .map(order -> new OrderResponse(mapper.toDto(order), user))
            .toList();
  }

  @Override
  @Transactional
  public OrderResponse update(Long id, UpdateOrderRequest req) {
    Order order = orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));

    mapper.updateEntityFromRequest(req, order);

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

  private Money calculateTotal(List<OrderItem> orderItems) {
    Money total = Money.zero();

    for (OrderItem oi : orderItems) {
      total = total.add(oi.getItem().getPrice().multiply(oi.getQuantity()));
    }
    return total;
  }

  @Override
  @Transactional
  public void processPaymentEvent(PaymentEvent event) {
    Order order = orderRepository.findById(event.orderId())
            .orElseThrow(() -> new OrderNotFoundException(event.orderId()));

    OrderStatus newStatus = switch (event.status()) {
      case SUCCESS -> OrderStatus.PROCESSING;
      case FAILED -> OrderStatus.FAILED;
    };

    if (order.getStatus() == newStatus) {
      return;
    }

    order.setStatus(newStatus);
    orderRepository.save(order);
  }
}