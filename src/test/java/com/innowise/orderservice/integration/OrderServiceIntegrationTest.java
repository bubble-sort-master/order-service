package com.innowise.orderservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.model.Money;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

import java.time.LocalDateTime;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@Transactional
class OrderServiceIntegrationTest {

  private static WireMockServer wireMockServer = new WireMockServer(0);

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private ItemRepository itemRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("user.service.url", () -> "http://localhost:" + wireMockServer.port());
    registry.add("jwt.secret", () -> "super-secret-key-at-least-32-characters-long-for-hmac-sha256");
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

  @Test
  void createOrder_shouldReturn200AndSaveOrder() throws Exception {
    Long userId = 1L;
    String userEmail = "john@example.com";
    stubUserByEmail(userEmail, userId, "John", "Doe");
    Item item = createItem("Laptop", 100_00L);
    CreateOrderRequest request = new CreateOrderRequest(userEmail, List.of(new OrderItemRequest(item.getId(), 2)));

    mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
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
  void createOrder_whenItemNotFound_shouldReturn400() throws Exception {
    stubUserByEmail("john@example.com", 1L, "John", "Doe");
    CreateOrderRequest request = new CreateOrderRequest("john@example.com", List.of(new OrderItemRequest(999L, 1)));

    mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Item with id 999 not found"));
  }

  @Test
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
  void createOrder_invalidRequest_shouldReturn400() throws Exception {
    CreateOrderRequest invalid = new CreateOrderRequest("", List.of());
    mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest());
  }

  @Test
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
  void getById_whenOrderNotFound_shouldReturn404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/999"))
            .andExpect(status().isNotFound())
            .andExpect(content().string("Order with id 999 not found"));
  }

  @Test
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
  void getAll_shouldReturnPagedOrdersWithUsers() throws Exception {
    Long userId1 = 1L, userId2 = 2L;
    stubUserById(userId1, "u1@test.com", "A", "B");
    stubUserById(userId2, "u2@test.com", "C", "D");

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
  void getAll_withStatusFilter_shouldReturnFilteredOrders() throws Exception {
    Long userId = 1L;
    stubUserById(userId, "filter@test.com", "X", "Y");

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
  void getByUserId_noOrders_shouldReturnEmptyList() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/user/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
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
  void update_whenOrderNotFound_shouldReturn404() throws Exception {
    UpdateOrderRequest request = new UpdateOrderRequest(OrderStatus.PROCESSING);
    mockMvc.perform(MockMvcRequestBuilders.put("/api/orders/999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
  }

  @Test
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
  void delete_whenOrderNotFound_shouldReturn404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.delete("/api/orders/999"))
            .andExpect(status().isNotFound());
  }

  @Test
  void itemNotFoundException_shouldBeHandledAs400() throws Exception {
    stubUserByEmail("john@example.com", 1L, "John", "Doe");
    CreateOrderRequest request = new CreateOrderRequest("john@example.com", List.of(new OrderItemRequest(777L, 1)));
    mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
  }
}