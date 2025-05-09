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
 * Gerencia a adição, chamada, limpeza e visualização da fila, incluindo re-chamadas
 * com limite de tentativas.
 */
@Service
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private final DriverRepository driverRepository;
    private final TwilioService twilioService;

    /** Limite máximo de chamadas permitidas (1ª chamada + 1 re-chamada = 2 tentativas). */
    private static final int MAX_CALL_ATTEMPTS = 2;

    /**
     * Construtor para injeção de dependências (DriverRepository, TwilioService).
     * @param driverRepository Repositório para acesso aos dados dos motoristas.
     * @param twilioService Serviço para envio de notificações SMS.
     */
    public DriverService(DriverRepository driverRepository, TwilioService twilioService) {
        this.driverRepository = driverRepository;
        this.twilioService = twilioService;
        log.info("DriverService inicializado e pronto.");
    }

    /**
     * Adiciona um novo motorista à fila de espera. Inicializa tentativas de chamada como 0.
     * @param plate Placa do veículo.
     * @param name Nome do motorista.
     * @param phoneNumber Número de telefone (formato E.164).
     * @return O objeto Driver persistido.
     * @throws IllegalArgumentException Se dados de entrada forem inválidos.
     */
    @Transactional
    public Driver addDriver(String plate, String name, String phoneNumber) {
        log.debug("addDriver: Iniciando adição para Placa={}, Nome={}, Telefone={}", plate, name, phoneNumber);
        // Validações de entrada...
        if (plate == null || plate.trim().isEmpty()) throw new IllegalArgumentException("Placa do veículo é obrigatória.");
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Nome do motorista é obrigatório.");
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) throw new IllegalArgumentException("Número de telefone é obrigatório.");

        String cleanedPhoneNumber = phoneNumber.replaceAll("[^0-9+]", "");
        if (!cleanedPhoneNumber.startsWith("+") || cleanedPhoneNumber.length() < 11) {
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
        driver.setCallAttempts(0); // <<-- Garante inicialização em 0

        // Persistência
        Driver savedDriver = driverRepository.save(driver);
        log.info("addDriver: Motorista adicionado: ID={}, Placa={}, Tentativas={}",
                savedDriver.getId(), savedDriver.getPlate(), savedDriver.getCallAttempts());
        return savedDriver;
    }

    /**
     * Retorna a lista de motoristas que estão aguardando (status WAITING).
     * @return Lista de motoristas WAITING, ordenada por entrada.
     */
    @Transactional(readOnly = true)
    public List<Driver> getAdminQueueView() {
        log.debug("getAdminQueueView: Buscando motoristas WAITING.");
        List<Driver> waitingDrivers = driverRepository.findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);
        return waitingDrivers != null ? waitingDrivers : Collections.emptyList();
    }

    /**
     * Retorna a lista de motoristas que já foram chamados e aguardam confirmação (status CALLED).
     * @return Lista de motoristas CALLED, ordenada por horário da última chamada.
     */
    @Transactional(readOnly = true)
    public List<Driver> getCalledDriversView() {
        log.debug("getCalledDriversView: Buscando motoristas CALLED ordenados por calledTime DESC.");
        List<Driver> calledDrivers = driverRepository.findByStatusOrderByCalledTimeDesc(Driver.DriverStatus.CALLED);
        return calledDrivers != null ? calledDrivers : Collections.emptyList();
    }

    /**
     * Realiza a PRIMEIRA chamada para o próximo motorista na fila (status WAITING).
     * Muda status para CALLED, define calledTime e incrementa callAttempts para 1. Envia SMS.
     * @return Optional contendo o motorista chamado, ou vazio se a fila estiver vazia.
     */
    @Transactional
    public Optional<Driver> callNextDriver() {
        log.info("callNextDriver: Iniciando processo de chamada para o próximo da fila.");
        Optional<Driver> nextDriverOpt = driverRepository.findFirstByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);

        if (nextDriverOpt.isPresent()) {
            Driver driverToCall = nextDriverOpt.get();
            driverToCall.setStatus(Driver.DriverStatus.CALLED);
            driverToCall.setCalledTime(LocalDateTime.now());
            driverToCall.setCallAttempts(1); // <<< Define a primeira tentativa
            driverRepository.save(driverToCall);
            log.info("callNextDriver: Motorista ID {} ({}) chamado. Status: CALLED. Tentativa: {}.",
                    driverToCall.getId(), driverToCall.getName(), driverToCall.getCallAttempts());

            sendSmsNotification(driverToCall, "sua vez na fila chegou!"); // Chama método auxiliar
            return Optional.of(driverToCall);
        } else {
            log.info("callNextDriver: Nenhum motorista WAITING encontrado para chamar.");
            return Optional.empty();
        }
    }

    /**
     * Tenta chamar NOVAMENTE um motorista que já está com status CALLED.
     * Se o limite de MAX_CALL_ATTEMPTS for atingido, muda o status para NO_SHOW.
     * Caso contrário, incrementa callAttempts, atualiza calledTime e reenvia SMS.
     *
     * @param driverId O ID do motorista a ser chamado novamente.
     * @return Um Optional contendo o motorista (com status CALLED ou NO_SHOW).
     * @throws IllegalArgumentException se o motorista não for encontrado ou não estiver no status CALLED.
     */
    @Transactional
    public Optional<Driver> recallDriver(Long driverId) { // <<< NOVO MÉTODO (ou substitui o antigo no-show)
        log.info("recallDriver: Tentando chamar novamente o motorista ID {}.", driverId);
        Driver driverToRecall = driverRepository.findById(driverId)
                .orElseThrow(() -> {
                    log.warn("recallDriver: Motorista com ID {} não encontrado.", driverId);
                    return new IllegalArgumentException("Motorista não encontrado com ID: " + driverId);
                });

        if (driverToRecall.getStatus() != Driver.DriverStatus.CALLED) {
            log.warn("recallDriver: Motorista ID {} não está CALLED (status: {}). Não pode ser chamado novamente.", driverId, driverToRecall.getStatus());
            throw new IllegalArgumentException("Motorista " + driverToRecall.getName() + " não está aguardando confirmação (status não é CALLED).");
        }

        int currentAttempts = driverToRecall.getCallAttempts();
        // Verifica se já atingiu ou ultrapassou o limite MÁXIMO de tentativas
        if (currentAttempts >= MAX_CALL_ATTEMPTS) {
            log.warn("recallDriver: Motorista ID {} ({}) já atingiu o limite de {} tentativas. Marcando como NO_SHOW.",
                    driverId, driverToRecall.getName(), MAX_CALL_ATTEMPTS);
            driverToRecall.setStatus(Driver.DriverStatus.NO_SHOW);
            // Aqui não enviamos SMS, apenas mudamos o status
            driverRepository.save(driverToRecall);
            return Optional.of(driverToRecall); // Retorna com status NO_SHOW
        }

        // Se ainda pode chamar novamente (ex: se MAX_CALL_ATTEMPTS=2 e currentAttempts=1)
        driverToRecall.setCallAttempts(currentAttempts + 1); // Incrementa a tentativa
        driverToRecall.setCalledTime(LocalDateTime.now()); // Atualiza para o horário desta tentativa
        driverRepository.save(driverToRecall);
        log.info("recallDriver: Motorista ID {} ({}) chamado novamente. Tentativa: {} de {}.",
                driverId, driverToRecall.getName(), driverToRecall.getCallAttempts(), MAX_CALL_ATTEMPTS);

        sendSmsNotification(driverToRecall, "Lembrete: sua vez na fila chegou!"); // Envia SMS de lembrete
        return Optional.of(driverToRecall); // Retorna com status CALLED e contagem incrementada
    }

    /**
     * Marca um motorista específico como tendo comparecido (status ATTENDED).
     * Só pode ser marcado se estiver no status CALLED.
     */
    @Transactional
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
        // Poderíamos zerar callAttempts aqui se quiséssemos: driver.setCallAttempts(0);
        driverRepository.save(driver);
        log.info("markDriverAsAttended: Motorista ID {} ({}) marcado como ATTENDED com sucesso.", driverId, driver.getName());
    }

    // REMOVEMOS o método markDriverAsNoShow, pois o fluxo agora é via recallDriver atingindo o limite.
    // Se precisar de uma ação manual para NO_SHOW, podemos adicioná-lo de volta ou usar o endpoint do controller.

    /**
     * Método auxiliar privado para encapsular a lógica de envio de SMS.
     */
    private void sendSmsNotification(Driver driver, String baseMessage) {
        try {
            String phone = driver.getPhoneNumber();
            if (phone != null && !phone.isBlank()) {
                // Mensagem personalizada incluindo o número de tentativas (se maior que 1)
                String attemptInfo = driver.getCallAttempts() > 1 ? " (Tentativa " + driver.getCallAttempts() + ")" : "";
                String message = "Ola " + driver.getName() + ", " + baseMessage + attemptInfo + ". Por favor, dirija-se ao local indicado.";

                log.info("sendSmsNotification: Solicitando envio de SMS para Motorista ID {} ({}) no número {}. Tentativa: {}",
                        driver.getId(), driver.getName(), phone, driver.getCallAttempts());
                twilioService.sendSms(phone, message);
            } else {
                log.warn("sendSmsNotification: Motorista ID {} ({}) não possui número de telefone. SMS não enviado.", driver.getId(), driver.getName());
            }
        } catch (Exception e) {
            log.error("sendSmsNotification: Erro ao tentar enviar SMS para Motorista ID {} ({}): {}",
                    driver.getId(), driver.getName(), e.getMessage(), e);
        }
    }

    /**
     * Muda o status de TODOS os motoristas atualmente com status WAITING para CLEARED.
     */
    @Transactional
    public int clearWaitingList() {
        log.warn("clearWaitingList: Iniciando limpeza da fila de espera...");
        List<Driver> waitingDrivers = driverRepository.findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);

        if (waitingDrivers.isEmpty()) {
            log.info("clearWaitingList: Fila de espera já está vazia.");
            return 0;
        }
        for (Driver driverLoop : waitingDrivers) {
            driverLoop.setStatus(Driver.DriverStatus.CLEARED);
            driverLoop.setCallAttempts(0); // Zera tentativas ao limpar
        }
        driverRepository.saveAll(waitingDrivers);
        log.warn("clearWaitingList: Fila limpa. {} motoristas tiveram o status alterado para CLEARED.", waitingDrivers.size());
        return waitingDrivers.size();
    }
}