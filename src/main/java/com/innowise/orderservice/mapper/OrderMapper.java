package com.innowise.orderservice.mapper;

import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderDto;
import com.innowise.orderservice.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(
        componentModel = "spring",
        uses = {OrderItemMapper.class, MoneyMapper.class}
)
public interface OrderMapper {

  @Mapping(target = "totalPrice", source = "totalPrice", qualifiedByName = "moneyToBigDecimal")
  @Mapping(target = "items", source = "orderItems")
  OrderDto toDto(Order order);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "userId", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "totalPrice", ignore = true)
  @Mapping(target = "orderItems", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  Order toEntity(CreateOrderRequest request);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "userId", ignore = true)
  @Mapping(target = "totalPrice", ignore = true)
  @Mapping(target = "orderItems", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  void updateEntityFromRequest(UpdateOrderRequest request, @MappingTarget Order order);
}