package com.innowise.orderservice.config;

import com.innowise.orderservice.event.PaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value("${spring.kafka.consumer.group-id:order-service}")
  private String groupId;

  @Bean
  public ConsumerFactory<String, PaymentEvent> paymentEventConsumerFactory() {
    JacksonJsonDeserializer<PaymentEvent> deserializer =
            new JacksonJsonDeserializer<>(PaymentEvent.class);
    deserializer.addTrustedPackages("com.innowise.orderservice.event");
    deserializer.setUseTypeHeaders(false);

    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    return new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            deserializer
    );
  }

  @Bean(name = "kafkaListenerContainerFactory")
  public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent>
  kafkaListenerContainerFactory() {

    ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(paymentEventConsumerFactory());
    return factory;
  }
}