-- Script V2: Altera a constraint CHECK na coluna 'status' da tabela 'driver'
-- para incluir os novos status ATTENDED e NO_SHOW.

-- 1. Remove a constraint check antiga.
--    O nome 'driver_status_check' é o padrão que o Postgres geralmente cria,
--    mas se der erro ao rodar, pode ser necessário verificar o nome exato no seu banco.
--    O IF EXISTS evita erro se a constraint já foi removida ou nunca existiu com esse nome.
ALTER TABLE driver DROP CONSTRAINT IF EXISTS driver_status_check;

-- 2. Adiciona a nova constraint check incluindo TODOS os valores permitidos agora.
ALTER TABLE driver ADD CONSTRAINT driver_status_check
    CHECK (status IN ('WAITING', 'CALLED', 'CLEARED', 'ATTENDED', 'NO_SHOW'));

-- 3. (Opcional) Adicionar um comentário no banco sobre a mudança (bom para documentação do schema)
COMMENT ON CONSTRAINT driver_status_check ON driver IS 'Restringe os valores permitidos para o status do motorista na fila.';