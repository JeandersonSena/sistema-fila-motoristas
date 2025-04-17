package com.suaempresa.driverqueue.repository;

import com.suaempresa.driverqueue.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // Para queries customizadas
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    /**
     * Encontra o maior número de sequência atualmente no banco de dados.
     * Usado para determinar o próximo número da sequência ao adicionar um novo motorista.
     * @return Um Optional contendo o maior sequenceNumber, ou vazio se não houver motoristas.
     */
    @Query("SELECT MAX(d.sequenceNumber) FROM Driver d") // JPQL query
    Optional<Long> findMaxSequenceNumber();

    /**
     * Encontra o motorista com o menor número de sequência que ainda está com status "WAITING".
     * Este é o próximo motorista a ser chamado.
     * Usa findFirst para pegar apenas um resultado, ordenado por sequenceNumber.
     * @return Um Optional contendo o próximo motorista na fila, ou vazio se a fila estiver vazia.
     */
    Optional<Driver> findFirstByStatusOrderBySequenceNumberAsc(String status);

    /**
     * Encontra todos os motoristas com um status específico, ordenados pelo número de sequência.
     * Usado para exibir a fila de espera e chamados recentes no painel do admin.
     * @param status O status a ser procurado (ex: "WAITING" ou "CALLED").
     * @return Uma lista de motoristas.
     */
    List<Driver> findByStatusOrderBySequenceNumberAsc(String status);

    /**
     * Encontra todos os motoristas ordenados pelo número de sequência.
     * Pode ser útil para a visão geral do admin.
     * @return Lista de todos os motoristas ordenados.
     */
    List<Driver> findAllByOrderBySequenceNumberAsc();

    // Poderia adicionar busca por telefone ou placa se necessário:
    // Optional<Driver> findByPhoneNumberAndStatus(String phoneNumber, String status);
}