package com.innowise.orderservice.exception;

import java.util.List;

public class ItemNotFoundException extends RuntimeException {
  public ItemNotFoundException(Long itemId) {
    super("Item with id " + itemId + " not found");
  }
  public ItemNotFoundException( List<Long> missingIds) {
    super("Missing items: " + missingIds);
  }
}