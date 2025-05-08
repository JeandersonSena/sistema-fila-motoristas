package com.suaempresa.driverqueue.service;

import com.suaempresa.driverqueue.model.Driver;
import com.suaempresa.driverqueue.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Serviço responsável pela lógica de negócio principal relacionada aos motoristas e à fila.
 * Gerencia a adição, chamada, limpeza e visualização da fila.
 */
@Service
public class DriverService {

    // Declaração correta do Logger para esta classe
    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private final DriverRepository driverRepository;
    private final TwilioService twilioService;

    /**
     * Construtor para injeção de dependências (DriverRepository, TwilioService).
     *
     * @param driverRepository Repositório para acesso aos dados dos motoristas.
     * @param twilioService Serviço para envio de notificações SMS.
     */
    public DriverService(DriverRepository driverRepository, TwilioService twilioService) {
        this.driverRepository = driverRepository;
        this.twilioService = twilioService;
        log.info("DriverService inicializado e pronto.");
    }

    /**
     * Adiciona um novo motorista à fila de espera.
     * Realiza validações básicas nos dados de entrada (validação principal via DTO).
     *
     * @param plate Placa do veículo do motorista.
     * @param name Nome do motorista.
     * @param phoneNumber Número de telefone do motorista (formato E.164).
     * @return O objeto Driver persistido com ID e status WAITING.
     * @throws IllegalArgumentException Se a placa, nome ou telefone forem inválidos ou vazios.
     */
    @Transactional // Garante atomicidade
    public Driver addDriver(String plate, String name, String phoneNumber) {
        log.debug("addDriver: Iniciando adição para Placa={}, Nome={}, Telefone={}", plate, name, phoneNumber);

        // Validações de entrada básicas (principais devem estar no DTO/Controller com @Valid)
        if (plate == null || plate.trim().isEmpty()) {
            log.warn("addDriver: Falha na validação - Placa obrigatória.");
            throw new IllegalArgumentException("Placa do veículo é obrigatória.");
        }
        if (name == null || name.trim().isEmpty()) {
            log.warn("addDriver: Falha na validação - Nome obrigatório.");
            throw new IllegalArgumentException("Nome do motorista é obrigatório.");
        }
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            log.warn("addDriver: Falha na validação - Telefone obrigatório.");
            throw new IllegalArgumentException("Número de telefone é obrigatório.");
        }

        // Limpeza e Validação de Formato (principais devem estar no DTO)
        String cleanedPhoneNumber = phoneNumber.replaceAll("[^0-9+]", "");
        if (!cleanedPhoneNumber.startsWith("+") || cleanedPhoneNumber.length() < 11) { // Checagem mínima de formato E.164
            log.warn("addDriver: Falha na validação - Formato de telefone inválido: '{}' (original: '{}')", cleanedPhoneNumber, phoneNumber);
            throw new IllegalArgumentException("Formato de telefone inválido. Use o padrão internacional (+55...).");
        }
        String cleanedPlate = plate.toUpperCase().trim();

        // Criação da Entidade
        Driver driver = new Driver();
        driver.setPlate(cleanedPlate);
        driver.setName(name.trim());
        driver.setPhoneNumber(cleanedPhoneNumber);
        driver.setEntryTime(LocalDateTime.now());
        driver.setStatus(Driver.DriverStatus.WAITING);

        // Persistência
        Driver savedDriver = driverRepository.save(driver);
        log.info("addDriver: Motorista adicionado com sucesso: ID={}, Nome={}, Placa={}",
                savedDriver.getId(), savedDriver.getName(), savedDriver.getPlate());
        return savedDriver;
    } // <-- CHAVE FECHANDO addDriver CORRETAMENTE

    /**
     * Retorna a lista de motoristas que estão atualmente aguardando na fila (status WAITING).
     * A lista é ordenada pelo horário de entrada (o mais antigo primeiro).
     *
     * @return Uma lista (List) de objetos Driver com status WAITING, ou uma lista vazia se não houver nenhum.
     */
    @Transactional(readOnly = true)
    public List<Driver> getAdminQueueView() {
        log.debug("getAdminQueueView: Buscando motoristas WAITING.");
        List<Driver> waitingDrivers = driverRepository.findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);
        return waitingDrivers != null ? waitingDrivers : Collections.emptyList();
    } // <-- CHAVE FECHANDO getAdminQueueView CORRETAMENTE

    /**
     * Retorna a lista de motoristas que já foram chamados (status CALLED).
     * A lista é ordenada pelo horário da chamada (o mais recente primeiro).
     *
     * @return Uma lista (List) de objetos Driver com status CALLED, ou uma lista vazia se não houver nenhum.
     */
    @Transactional(readOnly = true)
    public List<Driver> getCalledDriversView() {
        log.debug("getCalledDriversView: Buscando motoristas CALLED ordenados por calledTime DESC.");
        List<Driver> calledDrivers = driverRepository.findByStatusOrderByCalledTimeDesc(Driver.DriverStatus.CALLED);
        return calledDrivers != null ? calledDrivers : Collections.emptyList();
    } // <-- CHAVE FECHANDO getCalledDriversView CORRETAMENTE

    /**
     * Encontra o próximo motorista na fila, atualiza seu status para CALLED,
     * define o horário da chamada e tenta enviar uma notificação por SMS.
     *
     * @return Um Optional contendo o motorista chamado, ou Optional.empty() se a fila estiver vazia.
     */
    @Transactional
    public Optional<Driver> callNextDriver() {
        log.info("callNextDriver: Iniciando processo de chamada.");
        Optional<Driver> nextDriverOpt = driverRepository.findFirstByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);

        if (nextDriverOpt.isPresent()) {
            Driver driverToCall = nextDriverOpt.get();
            driverToCall.setStatus(Driver.DriverStatus.CALLED);
            driverToCall.setCalledTime(LocalDateTime.now());
            driverRepository.save(driverToCall);
            log.info("callNextDriver: Status do Motorista ID {} ({}) atualizado para CALLED.", driverToCall.getId(), driverToCall.getName());

            try {
                String phone = driverToCall.getPhoneNumber();
                if (phone != null && !phone.isBlank()) {
                    String message = "Ola " + driverToCall.getName() + ", sua vez na fila chegou! Por favor, dirija-se ao local indicado.";
                    log.info("callNextDriver: Solicitando envio de SMS para Motorista ID {} ({}) no número {}",
                            driverToCall.getId(), driverToCall.getName(), phone);
                    twilioService.sendSms(phone, message);
                } else {
                    log.warn("callNextDriver: Motorista ID {} ({}) não possui número de telefone. SMS não enviado.", driverToCall.getId(), driverToCall.getName());
                }
            } catch (Exception e) {
                log.error("callNextDriver: Erro ao tentar enviar SMS para Motorista ID {} ({}): {}",
                        driverToCall.getId(), driverToCall.getName(), e.getMessage(), e);
            }
            return Optional.of(driverToCall);
        } else {
            log.info("callNextDriver: Nenhum motorista encontrado para chamar.");
            return Optional.empty();
        }
    } // <-- CHAVE FECHANDO callNextDriver CORRETAMENTE

    /**
     * Muda o status de TODOS os motoristas atualmente com status WAITING para CLEARED.
     *
     * @return O número de motoristas que tiveram seu status alterado para CLEARED.
     */
    @Transactional
    public int clearWaitingList() {
        log.warn("clearWaitingList: Iniciando limpeza da fila de espera...");
        List<Driver> waitingDrivers = driverRepository.findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);

        if (waitingDrivers.isEmpty()) {
            log.info("clearWaitingList: Fila de espera já está vazia.");
            return 0;
        }
        for (Driver driver : waitingDrivers) {
            driver.setStatus(Driver.DriverStatus.CLEARED);
        }
        driverRepository.saveAll(waitingDrivers);
        log.warn("clearWaitingList: Fila limpa. {} motoristas tiveram o status alterado para CLEARED.", waitingDrivers.size());
        return waitingDrivers.size();
    } // <-- CHAVE FECHANDO clearWaitingList CORRETAMENTE


    // --- NOVOS MÉTODOS ADICIONADOS ABAIXO ---

    /**
     * Marca um motorista específico como tendo comparecido (status ATTENDED).
     * Loga um aviso se o motorista não for encontrado ou não estiver no status CALLED.
     *
     * @param driverId O ID do motorista a ser marcado como comparecido.
     * @throws IllegalArgumentException se o motorista não for encontrado ou não estiver no status CALLED.
     */
    @Transactional // Usando a anotação do Spring
    public void markDriverAsAttended(Long driverId) {
        log.info("markDriverAsAttended: Tentando marcar motorista ID {} como ATTENDED.", driverId);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> {
                    log.warn("markDriverAsAttended: Motorista com ID {} não encontrado.", driverId);
                    return new IllegalArgumentException("Motorista não encontrado com ID: " + driverId);
                });

        if (driver.getStatus() != Driver.DriverStatus.CALLED) {
            log.warn("markDriverAsAttended: Motorista ID {} não está com status CALLED (status atual: {}). Não pode ser marcado como ATTENDED.", driverId, driver.getStatus());
            throw new IllegalArgumentException("Motorista " + driver.getName() + " não está aguardando confirmação (status não é CALLED).");
        }

        driver.setStatus(Driver.DriverStatus.ATTENDED);
        driverRepository.save(driver);
        log.info("markDriverAsAttended: Motorista ID {} ({}) marcado como ATTENDED com sucesso.", driverId, driver.getName());
    } // <-- CHAVE FECHANDO markDriverAsAttended CORRETAMENTE

    /**
     * Marca um motorista específico como não tendo comparecido (status NO_SHOW).
     * Loga um aviso se o motorista não for encontrado ou não estiver no status CALLED.
     *
     * @param driverId O ID do motorista a ser marcado como não compareceu.
     * @throws IllegalArgumentException se o motorista não for encontrado ou não estiver no status CALLED.
     */
    @Transactional // Usando a anotação do Spring
    public void markDriverAsNoShow(Long driverId) {
        log.info("markDriverAsNoShow: Tentando marcar motorista ID {} como NO_SHOW.", driverId);
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> {
                    log.warn("markDriverAsNoShow: Motorista com ID {} não encontrado.", driverId);
                    return new IllegalArgumentException("Motorista não encontrado com ID: " + driverId);
                });

        if (driver.getStatus() != Driver.DriverStatus.CALLED) {
            log.warn("markDriverAsNoShow: Motorista ID {} não está com status CALLED (status atual: {}). Não pode ser marcado como NO_SHOW.", driverId, driver.getStatus());
            throw new IllegalArgumentException("Motorista " + driver.getName() + " não está aguardando confirmação (status não é CALLED).");
        }

        driver.setStatus(Driver.DriverStatus.NO_SHOW);
        driverRepository.save(driver);
        log.info("markDriverAsNoShow: Motorista ID {} ({}) marcado como NO_SHOW com sucesso.", driverId, driver.getName());
    } // <-- CHAVE FECHANDO markDriverAsNoShow CORRETAMENTE

} // <<<=== CHAVE FINAL FECHANDO A CLASSE DriverService