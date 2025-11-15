package com.wekers.microsa.dto;

import java.util.UUID;

public record ProductDeletedEvent(
        UUID id,
        String name,
        String description
) {}
