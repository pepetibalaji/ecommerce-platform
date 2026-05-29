CREATE TABLE products (

    id UUID PRIMARY KEY,

    name VARCHAR(255) NOT NULL,

    description TEXT,

    price DECIMAL(10,2) NOT NULL,

    category VARCHAR(255),

    brand VARCHAR(255),

    created_at TIMESTAMP,

    updated_at TIMESTAMP
);