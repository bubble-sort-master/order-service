package com.innowise.orderservice.exception;

public class ItemNotFoundException extends RuntimeException {
  public ItemNotFoundException(Long itemId) {
    super("Item with id " + itemId + " not found");
  }
}