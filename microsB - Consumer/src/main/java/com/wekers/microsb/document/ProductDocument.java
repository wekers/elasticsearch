package com.wekers.microsb.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;

@Document(indexName = "products")
@Setting(settingPath = "/elasticsearch/product-settings.json")
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "autocomplete_analyzer", searchAnalyzer = "standard")
    private String name;

    @Field(type = FieldType.Float)
    private BigDecimal price;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name_spell; // ← usado para correção ortográfica

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String correctedQuery;



    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCorrectedQuery() {
        return correctedQuery;
    }

    public void setCorrectedQuery(String correctedQuery) {
        this.correctedQuery = correctedQuery;
    }

    public String getNameSpell() {
        return name_spell;
    }

    public void setNameSpell(String nameSpell) {
        this.name_spell = nameSpell;
    }
}
