package com.suaempresa.driverqueue.repository;

import com.suaempresa.driverqueue.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório Spring Data JPA para a entidade {@link Driver}.
 * Fornece métodos CRUD básicos e métodos de consulta personalizados.
 */
@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    // ... métodos existentes ...
    List<Driver> findByStatusOrderByEntryTimeAsc(Driver.DriverStatus status);
    Optional<Driver> findFirstByStatusOrderByEntryTimeAsc(Driver.DriverStatus status);

    /**
     * Encontra todos os motoristas com status CALLED,
     * ordenados pelo horário da chamada em ordem descendente (mais recente primeiro).
     *
     * @param status O status a ser buscado (será DriverStatus.CALLED).
     * @return Uma lista de motoristas chamados, ordenada por calledTime.
     */
    List<Driver> findByStatusOrderByCalledTimeDesc(Driver.DriverStatus status); // NOVO MÉTODO
}