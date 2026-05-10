package com.innowise.orderservice.mapper;

import com.innowise.orderservice.model.Money;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface MoneyMapper {

  @Named("moneyToBigDecimal")
  default BigDecimal moneyToBigDecimal(Money money) {
    return money == null ? null : money.toBigDecimal();
  }
}