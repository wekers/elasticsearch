package com.wekers.microsb.dto;

import java.util.UUID;

public record ProductDeletedEvent(
        UUID id,
        String name,
        String description
) {}
