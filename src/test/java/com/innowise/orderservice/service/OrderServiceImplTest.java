package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserClient;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderDto;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.dto.response.UserInfoDto;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderItem;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.event.PaymentEvent;
import com.innowise.orderservice.event.PaymentStatus;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.exception.UserNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.mapper.OrderItemMapper;
import com.innowise.orderservice.model.Money;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

  @Mock
  private OrderRepository orderRepository;
  @Mock
  private ItemRepository itemRepository;
  @Mock
  private UserClient userClient;
  @Mock
  private OrderMapper mapper;
  @Mock
  private OrderItemMapper orderItemMapper;

  @InjectMocks
  private OrderServiceImpl orderService;

  private final UserInfoDto testUser = new UserInfoDto(1L, "John", "Doe", "john@example.com", true);
  private final Item testItem = createItem(10L, "Test Item", Money.of(500L));
  private final Order testOrder = createOrder(100L, 1L, OrderStatus.PENDING, Money.of(1000L));
  private final OrderDto testOrderDto = createOrderDto(100L, 1L, OrderStatus.PENDING, BigDecimal.TEN);

  @Test
  void create_shouldCreateOrderSuccessfully() {
    CreateOrderRequest request = new CreateOrderRequest(
            "john@example.com",
            List.of(new OrderItemRequest(10L, 2))
    );

    Order orderToSave = new Order();

    when(userClient.getUserByEmail("john@example.com")).thenReturn(testUser);
    when(mapper.toEntity(any(CreateOrderRequest.class))).thenReturn(orderToSave);
    when(orderItemMapper.toEntity(any(OrderItemRequest.class))).thenAnswer(invocation -> {
      OrderItem oi = new OrderItem();
      oi.setQuantity(invocation.getArgument(0, OrderItemRequest.class).quantity());
      return oi;
    });

    List<Long> requestedIds = List.of(10L);
    when(itemRepository.findAllById(requestedIds)).thenReturn(List.of(testItem));

    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(mapper.toDto(any(Order.class))).thenReturn(testOrderDto);

    OrderResponse response = orderService.create(request);

    assertThat(response).isNotNull();
    assertThat(response.user()).isEqualTo(testUser);
    assertThat(response.order()).isEqualTo(testOrderDto);

    ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository).save(orderCaptor.capture());
    Order savedOrder = orderCaptor.getValue();

    assertThat(savedOrder.getUserId()).isEqualTo(testUser.id());
    assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(savedOrder.getTotalPrice()).isEqualTo(Money.of(1000L));
    assertThat(savedOrder.getOrderItems()).hasSize(1);

    verify(itemRepository).findAllById(requestedIds);
    verify(mapper).toEntity(any(CreateOrderRequest.class));
    verify(orderItemMapper).toEntity(any(OrderItemRequest.class));
  }

  @Test
  void create_shouldThrowItemNotFoundExceptionWhenItemMissing() {
    CreateOrderRequest request = new CreateOrderRequest(
            "john@example.com",
            List.of(new OrderItemRequest(999L, 1))
    );

    when(userClient.getUserByEmail("john@example.com")).thenReturn(testUser);
    when(mapper.toEntity(any(CreateOrderRequest.class))).thenReturn(new Order());

    when(itemRepository.findAllById(List.of(999L))).thenReturn(List.of());

    assertThatThrownBy(() -> orderService.create(request))
            .isInstanceOf(ItemNotFoundException.class)
            .hasMessageContaining("Missing items: [999]");

    verify(orderRepository, never()).save(any());
    verify(itemRepository).findAllById(List.of(999L));
  }

  @Test
  void getById_shouldReturnOrderResponseWhenExists() {
    when(orderRepository.findById(100L)).thenReturn(Optional.of(testOrder));
    when(userClient.getUserById(1L)).thenReturn(testUser);
    when(mapper.toDto(testOrder)).thenReturn(testOrderDto);

    OrderResponse response = orderService.getById(100L);

    assertThat(response).isNotNull();
    assertThat(response.order()).isEqualTo(testOrderDto);
    assertThat(response.user()).isEqualTo(testUser);
  }

  @Test
  void getById_shouldThrowOrderNotFoundExceptionWhenMissing() {
    when(orderRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.getById(999L))
            .isInstanceOf(OrderNotFoundException.class)
            .hasMessageContaining("999");
  }

  @Test
  void getAll_shouldReturnPagedOrderResponses() {
    Pageable pageable = PageRequest.of(0, 10);
    List<Order> orders = List.of(testOrder);
    Page<Order> orderPage = new PageImpl<>(orders, pageable, 1);

    when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);
    when(userClient.getUsersByIds(anyList())).thenReturn(List.of(testUser));
    when(mapper.toDto(any(Order.class))).thenReturn(testOrderDto);

    Page<OrderResponse> result = orderService.getAll(pageable, List.of(OrderStatus.PENDING), null, null);

    assertThat(result).hasSize(1);
    OrderResponse response = result.getContent().get(0);
    assertThat(response.order()).isEqualTo(testOrderDto);
    assertThat(response.user()).isEqualTo(testUser);
  }

  @Test
  void getAll_withDateRangeAndStatuses_shouldPassSpecToRepository() {
    Pageable pageable = PageRequest.of(0, 10);
    LocalDateTime from = LocalDateTime.now().minusDays(1);
    LocalDateTime to = LocalDateTime.now();
    List<OrderStatus> statuses = List.of(OrderStatus.PENDING, OrderStatus.PROCESSING);

    when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty());

    orderService.getAll(pageable, statuses, from, to);

    verify(orderRepository).findAll(any(Specification.class), eq(pageable));
    verify(userClient, never()).getUsersByIds(anyList());
    verify(mapper, never()).toDto(any(Order.class));
  }

  @Test
  void getAll_shouldThrowUserNotFoundExceptionWhenUserMissingInBatch() {
    Pageable pageable = PageRequest.of(0, 10);
    List<Order> orders = List.of(testOrder);
    Page<Order> orderPage = new PageImpl<>(orders, pageable, 1);

    when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);
    when(userClient.getUsersByIds(anyList())).thenReturn(List.of());

    assertThatThrownBy(() -> orderService.getAll(pageable, List.of(OrderStatus.PENDING), null, null))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining("1");
  }

  @Test
  void getByUserId_shouldReturnListOfOrderResponses() {
    List<Order> orders = List.of(testOrder);
    when(orderRepository.findByUserId(1L)).thenReturn(orders);
    when(userClient.getUserById(1L)).thenReturn(testUser);
    when(mapper.toDto(testOrder)).thenReturn(testOrderDto);

    List<OrderResponse> responses = orderService.getByUserId(1L);

    assertThat(responses).hasSize(1);
    OrderResponse response = responses.get(0);
    assertThat(response.order()).isEqualTo(testOrderDto);
    assertThat(response.user()).isEqualTo(testUser);
  }

  @Test
  void getByUserId_shouldReturnEmptyListWhenNoOrders() {
    when(orderRepository.findByUserId(1L)).thenReturn(List.of());

    List<OrderResponse> responses = orderService.getByUserId(1L);

    assertThat(responses).isEmpty();
    verify(userClient, never()).getUserById(any());
    verify(mapper, never()).toDto(any(Order.class));
  }

  @Test
  void update_shouldUpdateOrderStatusAndReturnResponse() {
    UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.PROCESSING);
    Order existingOrder = createOrder(100L, 1L, OrderStatus.PENDING, Money.of(1000L));

    when(orderRepository.findById(100L)).thenReturn(Optional.of(existingOrder));
    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userClient.getUserById(1L)).thenReturn(testUser);
    when(mapper.toDto(any(Order.class))).thenReturn(
            createOrderDto(100L, 1L, OrderStatus.PROCESSING, BigDecimal.TEN)
    );

    doAnswer(invocation -> {
      Order target = invocation.getArgument(1);
      UpdateOrderRequest req = invocation.getArgument(0);
      target.setStatus(req.status());
      return null;
    }).when(mapper).updateEntityFromRequest(any(UpdateOrderRequest.class), any(Order.class));

    OrderResponse response = orderService.update(100L, request);

    assertThat(response.order().status()).isEqualTo(OrderStatus.PROCESSING);
    assertThat(response.user()).isEqualTo(testUser);

    ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PROCESSING);

    verify(mapper).updateEntityFromRequest(any(UpdateOrderRequest.class), any(Order.class));
  }

  @Test
  void update_shouldThrowOrderNotFoundExceptionWhenMissing() {
    UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.PROCESSING);
    when(orderRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.update(999L, request))
            .isInstanceOf(OrderNotFoundException.class);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void delete_shouldSoftDeleteOrder() {
    Order orderToDelete = createOrder(100L, 1L, OrderStatus.PENDING, Money.of(1000L));
    when(orderRepository.findById(100L)).thenReturn(Optional.of(orderToDelete));

    orderService.delete(100L);

    verify(orderRepository).delete(orderToDelete);
  }

  @Test
  void delete_shouldThrowOrderNotFoundExceptionWhenMissing() {
    when(orderRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.delete(999L))
            .isInstanceOf(OrderNotFoundException.class);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void processPaymentEvent_shouldUpdateStatusToProcessingWhenSuccess() {
    PaymentEvent event = new PaymentEvent(100L, PaymentStatus.SUCCESS, LocalDateTime.now());
    Order order = createOrder(100L, 1L, OrderStatus.PENDING, Money.of(1000L));

    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    orderService.processPaymentEvent(event);

    ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PROCESSING);
  }

  @Test
  void processPaymentEvent_shouldUpdateStatusToFailedWhenFailed() {
    PaymentEvent event = new PaymentEvent(100L, PaymentStatus.FAILED, LocalDateTime.now());
    Order order = createOrder(100L, 1L, OrderStatus.PENDING, Money.of(1000L));

    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    orderService.processPaymentEvent(event);

    ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.FAILED);
  }

  @Test
  void processPaymentEvent_shouldSkipWhenAlreadyInTargetStatus() {
    PaymentEvent event = new PaymentEvent(100L, PaymentStatus.SUCCESS, LocalDateTime.now());
    Order order = createOrder(100L, 1L, OrderStatus.PROCESSING, Money.of(1000L));

    when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

    orderService.processPaymentEvent(event);

    verify(orderRepository, never()).save(any());
  }

  @Test
  void processPaymentEvent_shouldThrowOrderNotFoundExceptionWhenMissing() {
    PaymentEvent event = new PaymentEvent(999L, PaymentStatus.SUCCESS, LocalDateTime.now());
    when(orderRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.processPaymentEvent(event))
            .isInstanceOf(OrderNotFoundException.class)
            .hasMessageContaining("999");
  }

  private Item createItem(Long id, String name, Money price) {
    Item item = new Item();
    item.setId(id);
    item.setName(name);
    item.setPrice(price);
    return item;
  }

  private Order createOrder(Long id, Long userId, OrderStatus status, Money totalPrice) {
    Order order = new Order();
    order.setId(id);
    order.setUserId(userId);
    order.setStatus(status);
    order.setTotalPrice(totalPrice);
    order.setDeleted(false);
    return order;
  }

  private OrderDto createOrderDto(Long id, Long userId, OrderStatus status, BigDecimal totalPrice) {
    return new OrderDto(id, userId, status, totalPrice, LocalDateTime.now(), List.of());
  }
}