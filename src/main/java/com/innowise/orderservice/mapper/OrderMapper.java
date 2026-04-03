package com.innowise.orderservice.mapper;

import com.innowise.orderservice.dto.response.OrderDto;
import com.innowise.orderservice.dto.response.OrderItemDto;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderItem;
import com.innowise.orderservice.model.Money;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

  @Mapping(target = "totalPrice", source = "totalPrice", qualifiedByName = "moneyToBigDecimal")
  @Mapping(target = "items", source = "orderItems")
  OrderDto toDto(Order order);

  @Mapping(target = "itemId", source = "item.id")
  @Mapping(target = "itemName", source = "item.name")
  @Mapping(target = "price", source = "item.price", qualifiedByName = "moneyToBigDecimal")
  OrderItemDto toDto(OrderItem orderItem);

  List<OrderItemDto> toDtoList(List<OrderItem> orderItems);

  @Named("moneyToBigDecimal")
  default BigDecimal moneyToBigDecimal(Money money) {
    return money == null ? null : money.toBigDecimal();
  }
}