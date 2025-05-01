package com.suaempresa.driverqueue.service;

import com.suaempresa.driverqueue.model.Driver;
import com.suaempresa.driverqueue.repository.DriverRepository;
import com.suaempresa.driverqueue.service.TwilioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Serviço responsável pela lógica de negócio principal relacionada aos motoristas e à fila.
 * Gerencia a adição, chamada, limpeza e visualização da fila.
 */
@Service
public class DriverService {

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
     * Realiza validações básicas nos dados de entrada.
     *
     * @param plate Placa do veículo do motorista (formato Mercosul ou antigo).
     * @param name Nome do motorista.
     * @param phoneNumber Número de telefone do motorista (formato E.164, ex: +55...).
     * @return O objeto Driver persistido com ID e status WAITING.
     * @throws IllegalArgumentException Se a placa, nome ou telefone forem inválidos ou vazios.
     *         Também pode lançar exceções do banco de dados se houver violação de constraints (ex: placa duplicada).
     */
    @Transactional // Garante atomicidade da operação
    public Driver addDriver(String plate, String name, String phoneNumber) {
        log.debug("addDriver: Iniciando adição para Placa={}, Nome={}, Telefone={}", plate, name, phoneNumber);
        // 1. Validação de Entrada (Simples - Validação principal via DTO/Bean Validation)
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

        // 2. Limpeza e Validação de Formato (Simples - Principal no DTO)
        String cleanedPhoneNumber = phoneNumber.replaceAll("[^0-9+]", "");
        if (!cleanedPhoneNumber.startsWith("+") || cleanedPhoneNumber.length() < 11) {
            log.warn("addDriver: Falha na validação - Formato de telefone inválido: '{}' (original: '{}')", cleanedPhoneNumber, phoneNumber);
            throw new IllegalArgumentException("Formato de telefone inválido. Use o padrão internacional (+55...).");
        }
        String cleanedPlate = plate.toUpperCase().trim(); // Padroniza antes de salvar

        // 3. Criação da Entidade
        Driver driver = new Driver();
        driver.setPlate(cleanedPlate);
        driver.setName(name.trim());
        driver.setPhoneNumber(cleanedPhoneNumber);
        driver.setEntryTime(LocalDateTime.now());
        driver.setStatus(Driver.DriverStatus.WAITING);

        // 4. Persistência
        Driver savedDriver = driverRepository.save(driver);
        log.info("addDriver: Motorista adicionado com sucesso: ID={}, Nome={}, Placa={}",
                savedDriver.getId(), savedDriver.getName(), savedDriver.getPlate());
        return savedDriver;
    }

    /**
     * Retorna a lista de motoristas que estão atualmente aguardando na fila (status WAITING).
     * A lista é ordenada pelo horário de entrada (o mais antigo primeiro).
     *
     * @return Uma lista (List) de objetos Driver com status WAITING, ou uma lista vazia se não houver nenhum.
     */
    @Transactional(readOnly = true) // Otimização para consulta
    public List<Driver> getAdminQueueView() {
        log.debug("getAdminQueueView: Buscando motoristas WAITING.");
        return driverRepository.findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);
    }

    /**
     * Encontra o próximo motorista na fila (o mais antigo com status WAITING),
     * atualiza seu status para CALLED, define o horário da chamada e tenta
     * enviar uma notificação por SMS.
     *
     * @return Um Optional contendo o motorista que foi chamado, ou Optional.empty() se a fila estiver vazia.
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
                // Não relança a exceção para não impedir a chamada
            }

            return Optional.of(driverToCall);
        } else {
            log.info("callNextDriver: Nenhum motorista encontrado para chamar.");
            return Optional.empty();
        }
    }

    /**
     * Muda o status de TODOS os motoristas atualmente com status WAITING para CLEARED.
     * Usado pela funcionalidade "Limpar Fila" do administrador.
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
    }
}