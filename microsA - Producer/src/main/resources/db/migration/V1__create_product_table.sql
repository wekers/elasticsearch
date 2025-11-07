CREATE TABLE product_entity (
id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
name VARCHAR(255) NOT NULL,
price NUMERIC(10,2) NOT NULL,
description TEXT,
created_at  timestamptz not null default now()
);
