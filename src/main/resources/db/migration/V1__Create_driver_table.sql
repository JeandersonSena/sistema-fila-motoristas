-- Script de Migração Flyway: V1__Create_driver_table.sql

-- Cria a tabela principal para os motoristas
CREATE TABLE driver (
    -- ID auto-incremental (bigserial é específico do Postgres e recomendado)
    id bigserial NOT NULL PRIMARY KEY,

    -- Placa do veículo: não nula e única
    plate varchar(255) NOT NULL UNIQUE,

    -- Nome do motorista: não nulo
    name varchar(100) NOT NULL, -- Ajustado tamanho máximo da validação

    -- Número de telefone: não nulo (formato E.164 esperado)
    phone_number varchar(20) NOT NULL, -- Ajustado tamanho, +55... cabe

    -- Horário de entrada na fila: não nulo, timestamp com timezone é geralmente melhor
    entry_time timestamp with time zone NOT NULL,

    -- Horário da chamada: pode ser nulo inicialmente
    called_time timestamp with time zone,

    -- Status do motorista: não nulo, com restrição nos valores permitidos
    status varchar(10) NOT NULL check (status in ('WAITING','CALLED','CLEARED'))
);

-- Opcional, mas bom: Adicionar um índice na coluna status e entry_time,
-- pois a usamos frequentemente para buscar o próximo motorista.
CREATE INDEX idx_driver_status_entry_time ON driver (status, entry_time);

-- Comentário: O índice na coluna 'plate' é criado automaticamente
-- devido à restrição UNIQUE.