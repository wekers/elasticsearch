package com.wekers.microsb.dto;

import java.math.BigDecimal;

public class ProductCreatedEvent {

    private String id;
    private String name;
    private String description;
    private BigDecimal price;

    public ProductCreatedEvent() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
