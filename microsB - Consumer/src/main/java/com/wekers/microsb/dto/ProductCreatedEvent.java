package com.wekers.microsb.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductCreatedEvent(
        UUID id,
        String name,
        String description,
        BigDecimal price
) {}
