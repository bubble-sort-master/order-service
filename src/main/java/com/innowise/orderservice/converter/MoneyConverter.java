package com.innowise.orderservice.converter;

import com.innowise.orderservice.model.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, Long> {

  @Override
  public Long convertToDatabaseColumn(Money attribute) {
    return attribute == null ? null : attribute.getAmountInCents();
  }

  @Override
  public Money convertToEntityAttribute(Long dbData) {
    return dbData == null ? null : Money.of(dbData);
  }
}