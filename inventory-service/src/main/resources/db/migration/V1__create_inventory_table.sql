CREATE TABLE inventory (

    id UUID PRIMARY KEY,

    product_id UUID NOT NULL UNIQUE,

    available_stock INT NOT NULL,

    reserved_stock INT NOT NULL,

    updated_at TIMESTAMP NOT NULL

);