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

    public DriverService(DriverRepository driverRepository, TwilioService twilioService) {
        this.driverRepository = driverRepository;
        this.twilioService = twilioService;
        log.info("DriverService inicializado e pronto.");
    }

    /**
     * Adiciona um novo motorista à fila de espera.
     * Normaliza o número de telefone para o formato E.164 (+55...) antes de salvar.
     * Inicializa tentativas de chamada como 0.
     *
     * @param plate Placa do veículo.
     * @param name Nome do motorista.
     * @param phoneNumberInput O número de telefone como inserido pelo usuário (ex: (XX) XXXXX-XXXX). // <<< CORREÇÃO NO JAVADOC
     * @return O objeto Driver persistido.
     * @throws IllegalArgumentException Se dados de entrada forem inválidos ou o telefone não puder ser normalizado.
     */
    @Transactional
    public Driver addDriver(String plate, String name, String phoneNumberInput) { // <<< CORREÇÃO NO NOME DO PARÂMETRO
        log.debug("addDriver: Iniciando adição para Placa={}, Nome={}, Telefone (entrada)='{}'", plate, name, phoneNumberInput);

        if (plate == null || plate.trim().isEmpty()) {
            log.warn("addDriver: Falha na validação - Placa obrigatória.");
            throw new IllegalArgumentException("Placa do veículo é obrigatória.");
        }
        if (name == null || name.trim().isEmpty()) {
            log.warn("addDriver: Falha na validação - Nome obrigatório.");
            throw new IllegalArgumentException("Nome do motorista é obrigatório.");
        }
        if (phoneNumberInput == null || phoneNumberInput.trim().isEmpty()) { // <<< USA O PARÂMETRO CORRETO
            log.warn("addDriver: Falha na validação - Telefone obrigatório.");
            throw new IllegalArgumentException("Número de telefone é obrigatório.");
        }

        String digitsOnly = phoneNumberInput.replaceAll("[^0-9]", "");

        String e164PhoneNumber;
        if (digitsOnly.length() == 10 || digitsOnly.length() == 11) {
            e164PhoneNumber = "+55" + digitsOnly;
            log.debug("addDriver: Telefone normalizado de '{}' ({} dígitos) para '{}'", phoneNumberInput, digitsOnly.length(), e164PhoneNumber);
        } else if (digitsOnly.startsWith("55") && (digitsOnly.length() == 12 || digitsOnly.length() == 13)) {
            e164PhoneNumber = "+" + digitsOnly;
            log.debug("addDriver: Telefone normalizado de '{}' (já com 55) para '{}'", phoneNumberInput, e164PhoneNumber);
        } else if (phoneNumberInput.startsWith("+55") && (phoneNumberInput.replaceAll("[^0-9]", "").length() == 12 || phoneNumberInput.replaceAll("[^0-9]", "").length() == 13)) {
            e164PhoneNumber = "+" + phoneNumberInput.replaceAll("[^0-9]", "");
            log.debug("addDriver: Telefone normalizado de '{}' (já com +55) para '{}'", phoneNumberInput, e164PhoneNumber);
        }
        else {
            log.warn("addDriver: Formato de telefone não reconhecido após limpeza: '{}' (original: '{}'). Não foi possível adicionar '+55'.", digitsOnly, phoneNumberInput);
            throw new IllegalArgumentException("Formato de telefone inválido. Forneça DDD e número (ex: (XX) XXXXX-XXXX ou XX XXXXXXXXX).");
        }

        if (!e164PhoneNumber.matches("^\\+[1-9]\\d{10,14}$")) {
            log.warn("addDriver: Falha na validação final do formato E.164 para o telefone normalizado: '{}'", e164PhoneNumber);
            throw new IllegalArgumentException("Formato de telefone inválido após tentativa de normalização para o padrão internacional.");
        }

        String cleanedPlate = plate.toUpperCase().trim();

        Driver driver = new Driver();
        driver.setPlate(cleanedPlate); // <<< CORREÇÃO: USA cleanedPlate
        driver.setName(name.trim());
        driver.setPhoneNumber(e164PhoneNumber); // <<< CORREÇÃO: Apenas esta chamada para setPhoneNumber
        driver.setEntryTime(LocalDateTime.now());
        driver.setStatus(Driver.DriverStatus.WAITING);
        driver.setCallAttempts(0);

        Driver savedDriver = driverRepository.save(driver);
        log.info("addDriver: Motorista adicionado com sucesso: ID={}, Placa={}, Telefone (E.164)='{}', Tentativas={}",
                savedDriver.getId(), savedDriver.getPlate(), savedDriver.getPhoneNumber(), savedDriver.getCallAttempts());
        return savedDriver;
    }

    // ... (getAdminQueueView - sem mudanças) ...
    @Transactional(readOnly = true)
    public List<Driver> getAdminQueueView() {
        log.debug("getAdminQueueView: Buscando motoristas WAITING.");
        List<Driver> waitingDrivers = driverRepository.findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);
        return waitingDrivers != null ? waitingDrivers : Collections.emptyList();
    }

    // ... (getCalledDriversView - sem mudanças) ...
    @Transactional(readOnly = true)
    public List<Driver> getCalledDriversView() {
        log.debug("getCalledDriversView: Buscando motoristas CALLED ordenados por calledTime DESC.");
        List<Driver> calledDrivers = driverRepository.findByStatusOrderByCalledTimeDesc(Driver.DriverStatus.CALLED);
        return calledDrivers != null ? calledDrivers : Collections.emptyList();
    }

    // ... (callNextDriver - sem mudanças na lógica principal, mas note que a variável 'driverToCall' foi usada no log do recallDriver,
    // o que estava incorreto, mas já corrigi no recallDriver abaixo) ...
    @Transactional
    public Optional<Driver> callNextDriver() {
        log.info("callNextDriver: Iniciando processo de chamada para o próximo da fila.");
        Optional<Driver> nextDriverOpt = driverRepository.findFirstByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);

        if (nextDriverOpt.isPresent()) {
            Driver driverToCall = nextDriverOpt.get();
            driverToCall.setStatus(Driver.DriverStatus.CALLED);
            driverToCall.setCalledTime(LocalDateTime.now());
            driverToCall.setCallAttempts(1);
            driverRepository.save(driverToCall);
            log.info("callNextDriver: Motorista ID {} ({}) chamado. Status: CALLED. Tentativa: {}.",
                    driverToCall.getId(), driverToCall.getName(), driverToCall.getCallAttempts());

            sendSmsNotification(driverToCall, "sua vez na fila chegou!");
            return Optional.of(driverToCall);
        } else {
            log.info("callNextDriver: Nenhum motorista WAITING encontrado para chamar.");
            return Optional.empty();
        }
    }

    // ... (recallDriver - código que você já tinha e que eu ajustei na mensagem anterior, mantido aqui) ...
    @Transactional
    public Optional<Driver> recallDriver(Long driverId) {
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
        if (currentAttempts >= MAX_CALL_ATTEMPTS) {
            log.warn("recallDriver: Motorista ID {} ({}) já atingiu o limite de {} tentativas. Marcando como NO_SHOW.",
                    driverId, driverToRecall.getName(), MAX_CALL_ATTEMPTS);
            driverToRecall.setStatus(Driver.DriverStatus.NO_SHOW);
            driverRepository.save(driverToRecall);
            return Optional.of(driverToRecall);
        }

        driverToRecall.setCallAttempts(currentAttempts + 1);
        driverToRecall.setCalledTime(LocalDateTime.now()); // << CORREÇÃO AQUI: usei driverToCall antes, agora é driverToRecall
        driverRepository.save(driverToRecall);
        log.info("recallDriver: Motorista ID {} ({}) chamado novamente. Tentativa: {} de {}.",
                driverId, driverToRecall.getName(), driverToRecall.getCallAttempts(), MAX_CALL_ATTEMPTS);

        sendSmsNotification(driverToRecall, "Lembrete: sua vez na fila chegou!");
        return Optional.of(driverToRecall);
    }

    // ... (markDriverAsAttended - sem mudanças) ...
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
        driverRepository.save(driver);
        log.info("markDriverAsAttended: Motorista ID {} ({}) marcado como ATTENDED com sucesso.", driverId, driver.getName());
    }


    // ... (sendSmsNotification - sem mudanças) ...
    private void sendSmsNotification(Driver driver, String baseMessage) {
        try {
            String phone = driver.getPhoneNumber();
            if (phone != null && !phone.isBlank()) {
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

    // ... (clearWaitingList - sem mudanças) ...
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
            driverLoop.setCallAttempts(0);
        }
        driverRepository.saveAll(waitingDrivers);
        log.warn("clearWaitingList: Fila limpa. {} motoristas tiveram o status alterado para CLEARED.", waitingDrivers.size());
        return waitingDrivers.size();
    }
}