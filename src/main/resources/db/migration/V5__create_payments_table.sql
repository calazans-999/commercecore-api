
-- V5: Pagamentos simulados do CommerceCore
-- Nenhuma transacao financeira real sera realizada.

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,

    order_id BIGINT NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    amount NUMERIC(12, 2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id),

    CONSTRAINT uq_payments_order
        UNIQUE (order_id),

    CONSTRAINT chk_payments_status
        CHECK (status IN (
            'PENDING',
            'APPROVED',
            'REJECTED'
        )),

    CONSTRAINT chk_payments_amount
        CHECK (amount >= 0)
);

CREATE INDEX idx_payments_status
    ON payments(status);
