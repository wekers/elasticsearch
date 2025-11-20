package com.wekers.microsb.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Document(indexName = "products_write")
@Setting(settingPath = "/elasticsearch/product-settings.json")
public class ProductDocument {

    // ========================================================================
    // CORE FIELDS
    // ========================================================================
    @Id
    private String id;

    @Field(type = FieldType.Text,
            analyzer = "autocomplete_analyzer",
            searchAnalyzer = "standard")
    private String name;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Text)
    private String description;

    // ========================================================================
    // AUTOCOMPLETE / SUGGESTION / SPELLING SUPPORT
    // ========================================================================
    @Field(type = FieldType.Text, analyzer = "standard")
    private String nameSpell;

    // CAMPO PARA SPELL CHECK LIMPO
    @Field(type = FieldType.Text, analyzer = "standard")
    private String nameSpellClean;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String correctedQuery;

    // ========================================================================
    // UNIQUE KEY (NAME + DESCRIPTION NORMALIZED)
    // ========================================================================
    @Field(type = FieldType.Keyword, normalizer = "lowercase_normalizer")
    private String uniqueKey;

    // ========================================================================
    // AUDITING FIELDS FOR UPDATE/CHANGE DETECTION
    // ========================================================================
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant updatedAt;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant priceChangedAt;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================
    public ProductDocument() {}

    public ProductDocument(String id, String name, BigDecimal price, String description) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.description = description;
        this.nameSpell = name;
        this.nameSpellClean = cleanNameForSpellCheck(name); // ✅ SEMPRE LIMPO
        this.uniqueKey = buildUniqueKey(name, description);
        this.updatedAt = Instant.now();
        this.priceChangedAt = Instant.now();
    }

    // ========================================================================
    // NAME CLEANER FOR SPELL CHECK
    // ========================================================================
    private String cleanNameForSpellCheck(String name) {
        if (name == null) return null;
        // Remove códigos hexadecimais de 6 caracteres no final
        return name.replaceAll("[a-f0-9]{6}$", "").trim();
    }

    // ========================================================================
    // UNIQUE KEY BUILDER
    // ========================================================================
    public static String buildUniqueKey(String name, String desc) {
        if (name == null) name = "";
        if (desc == null) desc = "";
        return (name.trim().toLowerCase() + "::" + desc.trim().toLowerCase());
    }

    public void rebuildUniqueKey() {
        this.uniqueKey = buildUniqueKey(this.name, this.description);
    }

    // ========================================================================
    // MARKERS
    // ========================================================================
    public void markUpdated() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public void markPriceChanged() {
        this.priceChangedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    // ========================================================================
    // GETTERS / SETTERS
    // ========================================================================
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    // mantém sincronização com nameSpell + uniqueKey + nameSpellClean
    public void setName(String name) {
        this.name = name;
        this.nameSpell = name;
        this.nameSpellClean = cleanNameForSpellCheck(name); // ✅ ATUALIZA CAMPO LIMPO
        rebuildUniqueKey();
        markUpdated();
    }

    public BigDecimal getPrice() {
        return price;
    }

    // detecta mudança de preço
    public void setPrice(BigDecimal price) {
        if (this.price == null || (price != null && price.compareTo(this.price) != 0)) {
            markPriceChanged();
        }
        this.price = price;
        markUpdated();
    }

    public String getDescription() {
        return description;
    }

    // mantém uniqueKey sincronizada
    public void setDescription(String description) {
        this.description = description;
        rebuildUniqueKey();
        markUpdated();
    }

    public String getCorrectedQuery() {
        return correctedQuery;
    }

    public void setCorrectedQuery(String correctedQuery) {
        this.correctedQuery = correctedQuery;
    }

    public String getNameSpell() {
        return nameSpell;
    }

    public void setNameSpell(String nameSpell) {
        this.nameSpell = nameSpell;
    }

    public String getNameSpellClean() {
        return nameSpellClean;
    }

    public void setNameSpellClean(String nameSpellClean) {
        this.nameSpellClean = nameSpellClean;
    }

    public String getUniqueKey() {
        return uniqueKey;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPriceChangedAt() {
        return priceChangedAt;
    }
}