package com.suaempresa.driverqueue.repository;

import com.suaempresa.driverqueue.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório Spring Data JPA para a entidade {@link Driver}.
 * Fornece métodos CRUD básicos (save, findById, findAll, deleteById, etc.)
 * e métodos de consulta personalizados baseados na convenção de nomes ou {@code @Query}.
 */
@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    /**
     * Encontra todos os motoristas com um status específico, ordenados pelo
     * horário de entrada em ordem ascendente (o mais antigo primeiro).
     *
     * @param status O {@link Driver.DriverStatus} a ser buscado.
     * @return Uma lista de motoristas que correspondem ao status, ordenada por entryTime.
     */
    List<Driver> findByStatusOrderByEntryTimeAsc(Driver.DriverStatus status);

    /**
     * Encontra o primeiro (mais antigo) motorista com um status específico,
     * ordenado pelo horário de entrada em ordem ascendente.
     * Útil para encontrar o próximo motorista a ser chamado na fila.
     *
     * @param status O {@link Driver.DriverStatus} a ser buscado (geralmente WAITING).
     * @return Um {@link Optional} contendo o motorista mais antigo com o status especificado,
     *         ou {@link Optional#empty()} se nenhum for encontrado.
     */
    Optional<Driver> findFirstByStatusOrderByEntryTimeAsc(Driver.DriverStatus status);

}