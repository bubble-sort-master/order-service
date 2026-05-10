package com.innowise.orderservice.mapper;

import com.innowise.orderservice.dto.response.OrderItemDto;
import com.innowise.orderservice.entity.OrderItem;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = MoneyMapper.class)
public interface OrderItemMapper {

  @Mapping(target = "itemId", source = "item.id")
  @Mapping(target = "itemName", source = "item.name")
  @Mapping(target = "price", source = "item.price", qualifiedByName = "moneyToBigDecimal")
  OrderItemDto toDto(OrderItem orderItem);

  List<OrderItemDto> toDtoList(List<OrderItem> orderItems);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "order", ignore = true)
  @Mapping(target = "item", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  OrderItem toEntity(OrderItemRequest request);
}