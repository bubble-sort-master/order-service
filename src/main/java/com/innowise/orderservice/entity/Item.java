package com.innowise.orderservice.entity;

import com.innowise.orderservice.converter.MoneyConverter;
import com.innowise.orderservice.model.Money;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "items")
public class Item extends BaseEntity{
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 255)
  private String name;

  @Convert(converter = MoneyConverter.class)
  @Column(nullable = false)
  private Money price;
}
