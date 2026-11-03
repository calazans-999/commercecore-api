CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PLACED',

    total_amount NUMERIC(12, 2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT chk_orders_status
        CHECK (status IN ('PLACED', 'CANCELLED')),

    CONSTRAINT chk_orders_total
        CHECK (total_amount >= 0)
);

CREATE INDEX idx_orders_user_created
    ON orders(user_id, created_at DESC);


CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,

    order_id BIGINT NOT NULL,

    product_id BIGINT NOT NULL,

    product_name VARCHAR(255) NOT NULL,

    unit_price NUMERIC(12, 2) NOT NULL,

    quantity INTEGER NOT NULL,

    subtotal NUMERIC(12, 2) NOT NULL,

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id),

    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    CONSTRAINT chk_order_items_price
        CHECK (unit_price >= 0),

    CONSTRAINT chk_order_items_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_order_items_subtotal
        CHECK (subtotal = unit_price * quantity),

    CONSTRAINT uq_order_items_order_product
        UNIQUE (order_id, product_id)
);

CREATE INDEX idx_order_items_product
    ON order_items(product_id);