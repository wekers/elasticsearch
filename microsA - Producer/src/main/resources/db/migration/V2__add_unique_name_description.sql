ALTER TABLE product_entity
    ADD CONSTRAINT unique_name_description
        UNIQUE (name, description);
