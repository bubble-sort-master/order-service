package com.innowise.orderservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.innowise.orderservice.config.TestKafkaProducerConfig;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.event.PaymentEvent;
import com.innowise.orderservice.event.PaymentStatus;
import com.innowise.orderservice.listener.PaymentEventListener;
import com.innowise.orderservice.model.Money;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@Import(TestKafkaProducerConfig.class)
class OrderServiceIntegrationTest {

  private static WireMockServer wireMockServer = new WireMockServer(0);

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @Container
  static final KafkaContainer kafka = new KafkaContainer(
          DockerImageName.parse("apache/kafka-native:3.8.0")
  ).withStartupTimeout(Duration.ofMinutes(3));

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private ItemRepository itemRepository;

  @Autowired
  private KafkaTemplate<String, PaymentEvent> kafkaTemplate;

  @Autowired(required = false)
  private PaymentEventListener paymentEventListener;

  @Autowired(required = false)
  private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("user.service.url", () -> "http://localhost:" + wireMockServer.port());
    registry.add("jwt.secret", () -> "super-secret-key-at-least-32-characters-long-for-hmac-sha256");
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    registry.add("spring.kafka.consumer.group-id", () -> "order-service-test-" + UUID.randomUUID());
  }

  @BeforeAll
  static void startWireMock() {
    wireMockServer.start();
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    wireMockServer.resetAll();
    orderRepository.deleteAll();
    itemRepository.deleteAll();
  }

  @Test
  void debugKafkaListener_shouldBeRegistered() {
    assertThat(paymentEventListener)
            .as("PaymentEventListener bean должен быть в Spring context")
            .isNotNull();

    assertThat(kafkaListenerEndpointRegistry)
            .as("KafkaListenerEndpointRegistry должен быть в Spring context")
            .isNotNull();

    assertThat(kafkaListenerEndpointRegistry.getListenerContainers())
            .as("Должен быть хотя бы один Kafka listener container")
            .isNotEmpty();

    kafkaListenerEndpointRegistry.getListenerContainers().forEach(container -> {
      System.out.println("Kafka container id = " + container.getListenerId());
      System.out.println("Kafka container running = " + container.isRunning());
    });
  }

  // ---------- Kafka-тесты (без @Transactional) ----------
  @Test
  void kafkaListener_shouldUpdateOrderStatusToProcessingOnSuccess() throws Exception {
    Long userId = 1L;
    stubUserById(userId, "john@example.com", "John", "Doe");
    Order order = new Order();
    order.setUserId(userId);
    order.setStatus(OrderStatus.PENDING);
    order.setTotalPrice(Money.of(1000L));
    order.setDeleted(false);
    Order saved = orderRepository.save(order);

    PaymentEvent event = new PaymentEvent(saved.getId(), PaymentStatus.SUCCESS, LocalDateTime.now());
    kafkaTemplate.send("payment-events", String.valueOf(saved.getId()), event).get(5, TimeUnit.SECONDS);

    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
      Order updated = orderRepository.findById(saved.getId()).orElseThrow();
      assertThat(updated.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    });
  }

  @Test
  void kafkaListener_shouldUpdateOrderStatusToFailedOnFailed() throws Exception {
    Long userId = 1L;
    stubUserById(userId, "john@example.com", "John", "Doe");
    Order order = new Order();
    order.setUserId(userId);
    order.setStatus(OrderStatus.PENDING);
    order.setTotalPrice(Money.of(1000L));
    order.setDeleted(false);
    Order saved = orderRepository.save(order);

    PaymentEvent event = new PaymentEvent(saved.getId(), PaymentStatus.FAILED, LocalDateTime.now());
    kafkaTemplate.send("payment-events", String.valueOf(saved.getId()), event).get(5, TimeUnit.SECONDS);

    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
      Order updated = orderRepository.findById(saved.getId()).orElseThrow();
      assertThat(updated.getStatus()).isEqualTo(OrderStatus.FAILED);
    });
  }

  // ---------- Остальные тесты с @Transactional (возвращаем назад) ----------
  @Test
  @Transactional
  void createOrder_shouldReturn201AndSaveOrder() throws Exception {
    Long userId = 1L;
    String userEmail = "john@example.com";
    stubUserByEmail(userEmail, userId, "John", "Doe");
    Item item = createItem("Laptop", 100_00L);
    CreateOrderRequest request = new CreateOrderRequest(userEmail, List.of(new OrderItemRequest(item.getId(), 2)));

    mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.order.status").value("PENDING"))
            .andExpect(jsonPath("$.order.totalPrice").value(200.00))
            .andExpect(jsonPath("$.user.id").value(userId))
            .andExpect(jsonPath("$.user.email").value(userEmail));

    List<Order> orders = orderRepository.findAll();
    assertThat(orders).hasSize(1);
    Order saved = orders.get(0);
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getTotalPrice()).isEqualTo(Money.of(200_00L));
    assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(saved.getOrderItems()).hasSize(1);
  }

  @Test
  @Transactional
  void createOrder_whenItemNotFound_shouldReturn400() throws Exception {
    stubUserByEmail("john@example.com", 1L, "John", "Doe");
    CreateOrderRequest request = new CreateOrderRequest("john@example.com", List.of(new OrderItemRequest(999L, 1)));

    mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Missing items: [999]"));
  }

  @Test
  @Transactional
  void createOrder_whenUserServiceReturns404_shouldReturn404() throws Exception {
    wireMockServer.stubFor(get(urlPathMatching("/api/users/by-email/.*"))
            .willReturn(aResponse().withStatus(404)));

    CreateOrderRequest request = new CreateOrderRequest("unknown@example.com", List.of(new OrderItemRequest(1L, 1)));

    mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
  }

  @Test
  @Transactional
  void createOrder_whenUserServiceDown_shouldReturn503() throws Exception {
    wireMockServer.stubFor(any(anyUrl())
            .willReturn(aResponse().withStatus(503)));

    CreateOrderRequest request = new CreateOrderRequest("john@example.com", List.of(new OrderItemRequest(1L, 1)));

    mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isServiceUnavailable());
  }

  @Test
  @Transactional
  void createOrder_invalidRequest_shouldReturn400() throws Exception {
    CreateOrderRequest invalid = new CreateOrderRequest("", List.of());
    mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest());
  }

  @Test
  @Transactional
  void getById_shouldReturnOrderWithUser() throws Exception {
    Long userId = 2L;
    stubUserById(userId, "mary@example.com", "Mary", "Smith");
    Order order = new Order();
    order.setUserId(userId);
    order.setStatus(OrderStatus.PENDING);
    order.setTotalPrice(Money.of(50_00L));
    Order saved = orderRepository.save(order);

    mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/{id}", saved.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.order.id").value(saved.getId()))
            .andExpect(jsonPath("$.order.userId").value(userId))
            .andExpect(jsonPath("$.user.id").value(userId))
            .andExpect(jsonPath("$.user.email").value("mary@example.com"));
  }

  @Test
  @Transactional
  void getById_whenOrderNotFound_shouldReturn404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/999"))
            .andExpect(status().isNotFound())
            .andExpect(content().string("Order with id 999 not found"));
  }

  @Test
  @Transactional
  void getById_whenUserServiceUnavailable_shouldReturn503() throws Exception {
    Order order = new Order();
    order.setUserId(1L);
    order.setStatus(OrderStatus.PENDING);
    order.setTotalPrice(Money.zero());
    Order saved = orderRepository.save(order);

    wireMockServer.stubFor(any(anyUrl())
            .willReturn(aResponse().withStatus(503)));

    mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/{id}", saved.getId()))
            .andExpect(status().isServiceUnavailable());
  }

  @Test
  @Transactional
  void getAll_shouldReturnPagedOrdersWithUsers() throws Exception {
    Long userId1 = 1L, userId2 = 2L;
    stubUsersBulk(userId1, userId2);

    Order order1 = new Order();
    order1.setUserId(userId1);
    order1.setStatus(OrderStatus.PENDING);
    order1.setTotalPrice(Money.of(100L));
    order1.setCreatedAt(LocalDateTime.now().minusDays(1));

    Order order2 = new Order();
    order2.setUserId(userId2);
    order2.setStatus(OrderStatus.PROCESSING);
    order2.setTotalPrice(Money.of(200L));
    order2.setCreatedAt(LocalDateTime.now());

    orderRepository.saveAll(List.of(order1, order2));

    mockMvc.perform(MockMvcRequestBuilders.get("/api/orders?page=0&size=2&sort=id,asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].user.id").value(userId1))
            .andExpect(jsonPath("$.content[1].user.id").value(userId2));
  }

  @Test
  @Transactional
  void getAll_withStatusFilter_shouldReturnFilteredOrders() throws Exception {
    Long userId = 1L;
    stubUsersBulk(userId);

    Order pending = new Order();
    pending.setUserId(userId);
    pending.setStatus(OrderStatus.PENDING);
    pending.setTotalPrice(Money.zero());

    Order completed = new Order();
    completed.setUserId(userId);
    completed.setStatus(OrderStatus.COMPLETED);
    completed.setTotalPrice(Money.zero());

    orderRepository.saveAll(List.of(pending, completed));

    mockMvc.perform(MockMvcRequestBuilders.get("/api/orders?statuses=PENDING&statuses=PROCESSING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].order.status").value("PENDING"));
  }

  @Test
  @Transactional
  void getAll_whenUserNotFoundInBatch_shouldReturn404() throws Exception {
    Long existingUserId = 1L;
    Long missingUserId = 999L;

    stubUsersBulkWithPartialResult(existingUserId);

    Order order1 = new Order();
    order1.setUserId(existingUserId);
    order1.setStatus(OrderStatus.PENDING);
    order1.setTotalPrice(Money.of(100L));

    Order order2 = new Order();
    order2.setUserId(missingUserId);
    order2.setStatus(OrderStatus.PROCESSING);
    order2.setTotalPrice(Money.of(200L));

    orderRepository.saveAll(List.of(order1, order2));

    mockMvc.perform(MockMvcRequestBuilders.get("/api/orders?page=0&size=10"))
            .andExpect(status().isNotFound())
            .andExpect(content().string(containsString(String.valueOf(missingUserId))))
            .andExpect(content().string(containsString("User")))
            .andExpect(result -> {
              String body = result.getResponse().getContentAsString();
              assertThat(body).contains("999");
            });
  }

  @Test
  @Transactional
  void getByUserId_shouldReturnUserOrders() throws Exception {
    Long userId = 10L;
    stubUserById(userId, "user10@test.com", "User", "Ten");

    Order order1 = new Order();
    order1.setUserId(userId);
    order1.setStatus(OrderStatus.PENDING);
    order1.setTotalPrice(Money.zero());

    Order order2 = new Order();
    order2.setUserId(userId);
    order2.setStatus(OrderStatus.COMPLETED);
    order2.setTotalPrice(Money.zero());

    orderRepository.saveAll(List.of(order1, order2));

    mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/user/{userId}", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].order.userId").value(userId))
            .andExpect(jsonPath("$[1].order.userId").value(userId));
  }

  @Test
  @Transactional
  void getByUserId_noOrders_shouldReturnEmptyList() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/user/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @Transactional
  void update_shouldChangeStatusAndReturnUpdatedOrder() throws Exception {
    Long userId = 5L;
    stubUserById(userId, "update@test.com", "Update", "Test");

    Order order = new Order();
    order.setUserId(userId);
    order.setStatus(OrderStatus.PENDING);
    order.setTotalPrice(Money.zero());
    Order saved = orderRepository.save(order);

    UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.COMPLETED);

    mockMvc.perform(MockMvcRequestBuilders.put("/api/orders/{id}", saved.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.order.status").value("COMPLETED"));

    Order updated = orderRepository.findById(saved.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo(OrderStatus.COMPLETED);
  }

  @Test
  @Transactional
  void update_whenOrderNotFound_shouldReturn404() throws Exception {
    UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.PROCESSING);
    mockMvc.perform(MockMvcRequestBuilders.put("/api/orders/999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
  }

  @Test
  @Transactional
  void delete_shouldSoftDeleteOrder() throws Exception {
    Order order = new Order();
    order.setUserId(1L);
    order.setStatus(OrderStatus.PENDING);
    order.setTotalPrice(Money.zero());
    order.setDeleted(false);
    Order saved = orderRepository.save(order);

    mockMvc.perform(MockMvcRequestBuilders.delete("/api/orders/{id}", saved.getId()))
            .andExpect(status().isNoContent());

    Order deletedOrder = orderRepository.findByIdIncludingDeleted(saved.getId())
            .orElseThrow();

    assertThat(deletedOrder.isDeleted()).isTrue();
  }

  @Test
  @Transactional
  void delete_whenOrderNotFound_shouldReturn404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.delete("/api/orders/999"))
            .andExpect(status().isNotFound());
  }

  @Test
  @Transactional
  void itemNotFoundException_shouldBeHandledAs400() throws Exception {
    stubUserByEmail("john@example.com", 1L, "John", "Doe");
    CreateOrderRequest request = new CreateOrderRequest("john@example.com", List.of(new OrderItemRequest(777L, 1)));
    mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
  }

  // ---------- вспомогательные методы ----------
  private void stubUserById(Long userId, String email, String name, String surname) {
    wireMockServer.stubFor(get(urlPathMatching("/api/users/" + userId))
            .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("""
                                        {
                                          "id": %d,
                                          "name": "%s",
                                          "surname": "%s",
                                          "email": "%s",
                                          "active": true
                                        }
                                        """, userId, name, surname, email))));
  }

  private void stubUserByEmail(String email, Long userId, String name, String surname) {
    String encodedEmail = email.replace("@", "%40");
    wireMockServer.stubFor(get(urlPathEqualTo("/api/users/by-email/" + encodedEmail))
            .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("""
                                        {
                                          "id": %d,
                                          "name": "%s",
                                          "surname": "%s",
                                          "email": "%s",
                                          "active": true
                                        }
                                        """, userId, name, surname, email))));
  }

  private Item createItem(String name, long priceCents) {
    Item item = new Item();
    item.setName(name);
    item.setPrice(Money.of(priceCents));
    return itemRepository.save(item);
  }

  private void stubUsersBulk(Long... userIds) {
    StringBuilder body = new StringBuilder("[");
    for (int i = 0; i < userIds.length; i++) {
      Long id = userIds[i];
      String email = "u" + id + "@test.com";
      String name = "User" + id;
      String surname = "Test" + id;
      body.append(String.format("""
                {"id": %d, "name": "%s", "surname": "%s", "email": "%s", "active": true}
                """, id, name, surname, email));
      if (i < userIds.length - 1) body.append(",");
    }
    body.append("]");

    wireMockServer.stubFor(get(urlPathEqualTo("/api/users/bulk"))
            .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body.toString())));
  }

  private void stubUsersBulkWithPartialResult(Long... existingUserIds) {
    StringBuilder body = new StringBuilder("[");
    for (int i = 0; i < existingUserIds.length; i++) {
      Long id = existingUserIds[i];
      String email = "u" + id + "@test.com";
      String name = "User" + id;
      String surname = "Test" + id;

      body.append(String.format("""
                {"id": %d, "name": "%s", "surname": "%s", "email": "%s", "active": true}
                """, id, name, surname, email));

      if (i < existingUserIds.length - 1) {
        body.append(",");
      }
    }
    body.append("]");

    wireMockServer.stubFor(get(urlPathEqualTo("/api/users/bulk"))
            .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body.toString())));
  }
}