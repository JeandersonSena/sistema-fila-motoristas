-- V3__Add_call_attempts_to_driver_table.sql
ALTER TABLE driver
ADD COLUMN call_attempts INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN driver.call_attempts IS 'Número de vezes que o motorista foi chamado (para controle de re-chamadas)';