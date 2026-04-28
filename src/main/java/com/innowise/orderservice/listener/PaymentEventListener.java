package com.innowise.orderservice.listener;

import com.innowise.orderservice.event.PaymentEvent;
import com.innowise.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

  private final OrderService orderService;

  @KafkaListener(topics = "payment-events", groupId = "order-service")
  public void handlePaymentEvent(PaymentEvent event) {
    log.info("Received payment event for order {}: {}", event.orderId(), event.status());
    try {
      orderService.processPaymentEvent(event);
      log.info("Successfully processed payment event for order {}", event.orderId());
    } catch (Exception e) {
      log.error("Error processing payment event for order {}: {}", event.orderId(), e.getMessage());
    }
  }
}