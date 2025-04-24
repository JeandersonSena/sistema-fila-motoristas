package com.suaempresa.driverqueue.repository;

import com.suaempresa.driverqueue.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    // Busca todos os motoristas com um status específico, ordenados pelo horário de entrada (mais antigo primeiro)
    List<Driver> findByStatusOrderByEntryTimeAsc(Driver.DriverStatus status);

    // Busca O PRIMEIRO motorista com um status específico, ordenado pelo horário de entrada
    Optional<Driver> findFirstByStatusOrderByEntryTimeAsc(Driver.DriverStatus status);
}