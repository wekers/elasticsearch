package com.wekers.microsa.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductUpdatedEvent(
        UUID id,
        String name,
        String description,
        BigDecimal price
) {}
