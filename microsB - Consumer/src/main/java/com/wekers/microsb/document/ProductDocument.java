package com.wekers.microsb.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;

@Document(indexName = "products_write")
@Setting(settingPath = "/elasticsearch/product-settings.json")
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "autocomplete_analyzer", searchAnalyzer = "standard")
    private String name;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Text)
    private String description;

    // Campo para correção ortográfica + busca normalizada
    @Field(type = FieldType.Text, analyzer = "standard")
    private String nameSpell;

    // Chave única para prevenir duplicidade (name + description)
    @Field(type = FieldType.Keyword)
    private String uniqueKey;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String correctedQuery;

    // ---------- CONSTRUCTORS ----------

    public ProductDocument() {}

    public ProductDocument(String id, String name, BigDecimal price, String description) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.description = description;

        // Preenche os campos derivados
        this.nameSpell = name;
        this.uniqueKey = buildUniqueKey(name, description);
    }

    // ---------- GETTERS / SETTERS ----------

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    /**
     * Sempre que o nome muda, refaz nameSpell e uniqueKey também.
     */
    public void setName(String name) {
        this.name = name;
        this.nameSpell = name; // mantém sincronizado
        this.rebuildUniqueKey();
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

    /**
     * Sempre que a descrição muda, refaz uniqueKey.
     */
    public void setDescription(String description) {
        this.description = description;
        rebuildUniqueKey();
    }

    public String getNameSpell() {
        return nameSpell;
    }

    public void setNameSpell(String nameSpell) {
        this.nameSpell = nameSpell;
    }

    public String getUniqueKey() {
        return uniqueKey;
    }

    private void setUniqueKey(String uniqueKey) {
        this.uniqueKey = uniqueKey;
    }

    public String getCorrectedQuery() {
        return correctedQuery;
    }

    public void setCorrectedQuery(String correctedQuery) {
        this.correctedQuery = correctedQuery;
    }

    // ---------- HELPERS ----------

    /**
     * Gera chave única normalizada:
     *   notebook lenovo ryzen::memory 24gb
     */
    private static String buildUniqueKey(String name, String desc) {
        if (name == null) name = "";
        if (desc == null) desc = "";

        return (name.trim().toLowerCase() + "::" + desc.trim().toLowerCase()).trim();
    }

    /**
     * Recria a uniqueKey quando nome ou descrição mudam.
     */
    public void rebuildUniqueKey() {
        this.uniqueKey = buildUniqueKey(this.name, this.description);
    }
}
