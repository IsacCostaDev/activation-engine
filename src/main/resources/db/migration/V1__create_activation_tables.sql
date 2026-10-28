-- V1__create_activation_tables.sql
-- Tabela principal de solicitações de ativação de produtos

CREATE TABLE IF NOT EXISTS activation_requests (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    failure_reason TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_activation_requests_external_id UNIQUE (external_id)
);

-- Índices estratégicos para consulta e agendador de reconciliação
CREATE INDEX IF NOT EXISTS idx_requests_status ON activation_requests (status);
CREATE INDEX IF NOT EXISTS idx_requests_customer_id ON activation_requests (customer_id);
CREATE INDEX IF NOT EXISTS idx_requests_created_at ON activation_requests (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_requests_reconciliation ON activation_requests (status, updated_at);
