package com.suaempresa.driverqueue.service;

import com.suaempresa.driverqueue.model.Driver;
import com.suaempresa.driverqueue.repository.DriverRepository;
import com.suaempresa.driverqueue.service.TwilioService; // Importa o serviço Twilio
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    // Dependências injetadas via construtor (final garante inicialização)
    private final DriverRepository driverRepository;
    private final TwilioService twilioService;

    // Construtor para o Spring injetar as dependências
    public DriverService(DriverRepository driverRepository, TwilioService twilioService) {
        this.driverRepository = driverRepository;
        this.twilioService = twilioService;
        log.info("DriverService inicializado e pronto.");
    }

    @Transactional // Operação atômica no banco
    public Driver addDriver(String plate, String name, String phoneNumber) {
        // 1. Validação de Entrada
        if (plate == null || plate.trim().isEmpty()) {
            throw new IllegalArgumentException("Placa do veículo é obrigatória.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do motorista é obrigatório.");
        }
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Número de telefone é obrigatório.");
        }

        // 2. Limpeza e Validação do Telefone (Formato E.164 esperado: +55...)
        String cleanedPhoneNumber = phoneNumber.replaceAll("[^0-9+]", ""); // Remove caracteres não numéricos, exceto '+'
        if (!cleanedPhoneNumber.startsWith("+") || cleanedPhoneNumber.length() < 11) { // Checagem mínima
            log.warn("Número de telefone inválido após limpeza: '{}' (original: '{}')", cleanedPhoneNumber, phoneNumber);
            throw new IllegalArgumentException("Formato de telefone inválido. Use o padrão internacional (+55...).");
        }

        // 3. Criação da Entidade
        Driver driver = new Driver();
        driver.setPlate(plate.toUpperCase().trim()); // Padroniza para maiúsculas e remove espaços extras
        driver.setName(name.trim());
        driver.setPhoneNumber(cleanedPhoneNumber); // Salva o número limpo
        driver.setEntryTime(LocalDateTime.now()); // Define horário de entrada
        driver.setStatus(Driver.DriverStatus.WAITING); // Status inicial

        // 4. Persistência
        Driver savedDriver = driverRepository.save(driver);
        log.info("Motorista adicionado com sucesso: ID={}, Nome={}, Placa={}, Telefone={}",
                savedDriver.getId(), savedDriver.getName(), savedDriver.getPlate(), savedDriver.getPhoneNumber());
        return savedDriver;
    }

    @Transactional(readOnly = true) // Otimiza para leitura
    public List<Driver> getAdminQueueView() {
        log.debug("Buscando motoristas com status WAITING ordenados por entrada.");
        return driverRepository.findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);
    }

    @Transactional
    public Optional<Driver> callNextDriver() {
        log.info("Processando chamada para o próximo motorista...");
        // 1. Busca o próximo motorista na fila
        Optional<Driver> nextDriverOpt = driverRepository.findFirstByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);

        if (nextDriverOpt.isPresent()) {
            Driver driverToCall = nextDriverOpt.get();

            // 2. Atualiza o status e horário da chamada
            driverToCall.setStatus(Driver.DriverStatus.CALLED);
            driverToCall.setCalledTime(LocalDateTime.now());
            driverRepository.save(driverToCall); // Salva a atualização ANTES de tentar enviar SMS
            log.info("Status do Motorista ID {} ({}) atualizado para CALLED.", driverToCall.getId(), driverToCall.getName());

            // 3. Tenta enviar o SMS (operação separada, não deve impedir a chamada)
            try {
                String phone = driverToCall.getPhoneNumber();
                if (phone != null && !phone.isBlank()) {
                    String message = "Olá " + driverToCall.getName() + ", sua vez na fila chegou! Por favor, dirija-se ao local de marcação.";
                    log.info("Solicitando envio de SMS para {}...", phone);
                    twilioService.sendSms(phone, message); // Chama o serviço Twilio
                } else {
                    log.warn("Motorista ID {} ({}) não possui número de telefone cadastrado. SMS não enviado.", driverToCall.getId(), driverToCall.getName());
                }
            } catch (Exception e) {
                // Captura qualquer erro inesperado vindo do TwilioService (embora ele já logue)
                log.error("Erro ao tentar disparar SMS para Motorista ID {} ({}): {}",
                        driverToCall.getId(), driverToCall.getName(), e.getMessage(), e);
                // Não faz nada aqui, a chamada já foi registrada no banco.
            }

            return Optional.of(driverToCall); // Retorna o motorista que foi chamado
        } else {
            log.info("Nenhum motorista encontrado com status WAITING para chamar.");
            return Optional.empty(); // Fila vazia
        }
    }

    @Transactional
    public int clearWaitingList() {
        log.warn("Iniciando processo para limpar a lista de espera...");
        // 1. Busca todos os motoristas que estão esperando
        List<Driver> waitingDrivers = driverRepository.findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);

        if (waitingDrivers.isEmpty()) {
            log.info("Lista de espera já está vazia. Nenhuma ação necessária.");
            return 0;
        }

        // 2. Atualiza o status de cada um para CLEARED
        for (Driver driver : waitingDrivers) {
            driver.setStatus(Driver.DriverStatus.CLEARED);
            // Opcional: Adicionar um campo 'clearedTime' e definir aqui
            // driver.setClearedTime(LocalDateTime.now());
        }

        // 3. Salva todas as alterações de uma vez (mais eficiente)
        driverRepository.saveAll(waitingDrivers);
        log.warn("Lista de espera limpa. {} motoristas tiveram o status alterado para CLEARED.", waitingDrivers.size());
        return waitingDrivers.size();
    }
}