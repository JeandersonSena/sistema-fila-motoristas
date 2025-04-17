package com.suaempresa.driverqueue.service;

import com.suaempresa.driverqueue.model.Driver;
import com.suaempresa.driverqueue.repository.DriverRepository;
import com.suaempresa.driverqueue.service.TwilioService; // <<< VERIFIQUE SE ESTE PACOTE ESTÁ CORRETO PARA TwilioService
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern; // Import para Regex

@Service
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    // Regex para validar um número brasileiro com 11 dígitos após limpeza (2 DDD + 9 Número começando com 9)
    private static final Pattern BRAZILIAN_MOBILE_PATTERN = Pattern.compile("^[1-9]{2}9[0-9]{8}$");

    @Autowired private DriverRepository driverRepository;
    @Autowired private TwilioService twilioService; // Injeta o serviço Twilio
    @Autowired private EntityManager entityManager; // Para garantir que o MAX seja lido após commit

    // Guarda informações do último motorista chamado em memória
    private final AtomicReference<Driver> lastCalledDriverRef = new AtomicReference<>(null);

    /**
     * Adiciona um novo motorista à fila, validando telefone, atribuindo sequência.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Driver addDriver(String licensePlate, String phoneNumber) {
        // Validação Placa
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            log.error("Tentativa de adicionar motorista com placa inválida: '{}'", licensePlate);
            throw new IllegalArgumentException("Placa é obrigatória.");
        }

        // Validação e Limpeza do Telefone
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            log.error("Tentativa de adicionar motorista com telefone inválido: '{}'", phoneNumber);
            throw new IllegalArgumentException("Telefone é obrigatório.");
        }
        // Limpa TUDO exceto dígitos
        String cleanedPhoneNumber = phoneNumber.replaceAll("[^0-9]", "");

        // VALIDAÇÃO DO FORMATO BRASILEIRO (11 dígitos)
        if (!BRAZILIAN_MOBILE_PATTERN.matcher(cleanedPhoneNumber).matches()) {
            log.error("Número de telefone inválido (formato incorreto após limpeza: {}): {}", cleanedPhoneNumber, phoneNumber);
            throw new IllegalArgumentException("Número de telefone inválido. Use o formato (XX) 9YYYY-ZZZZ (11 dígitos).");
        }

        // Determina o próximo número sequencial
        Long lastSequence = driverRepository.findMaxSequenceNumber().orElse(0L);
        Long nextSequence = lastSequence + 1;

        log.info("Adicionando motorista. Placa: {}, Telefone Limpo: {}. Próxima sequência: {}",
                licensePlate.trim().toUpperCase(), cleanedPhoneNumber, nextSequence);

        // Cria motorista com telefone já limpo e validado
        Driver newDriver = new Driver(licensePlate.trim().toUpperCase(), cleanedPhoneNumber, nextSequence);
        Driver savedDriver = driverRepository.saveAndFlush(newDriver);
        entityManager.refresh(savedDriver); // Garante estado atualizado

        log.info("Motorista adicionado com sucesso: {}", savedDriver);
        return savedDriver;
    }

    /**
     * Encontra e chama o próximo motorista na fila (menor sequência com status WAITING).
     * Envia SMS via Twilio.
     * @return Optional contendo o motorista chamado, ou vazio se não houver ninguém na fila ou falha no SMS.
     */
    @Transactional
    public Optional<Driver> callNextDriver() {
        log.info("Tentando chamar o próximo motorista da fila...");
        Optional<Driver> nextDriverOpt = driverRepository.findFirstByStatusOrderBySequenceNumberAsc("WAITING");

        if (nextDriverOpt.isEmpty()) {
            log.info("Nenhum motorista na fila de espera (WAITING) encontrado.");
            return Optional.empty();
        }

        Driver driverToCall = nextDriverOpt.get();
        log.info("Próximo motorista encontrado: ID={}, Seq={}, Placa={}, Telefone={}",
                driverToCall.getId(), driverToCall.getSequenceNumber(), driverToCall.getLicensePlate(), driverToCall.getPhoneNumber());

        log.debug("Tentando enviar SMS para {}", driverToCall.getPhoneNumber());
        // Passa o número limpo (11 dígitos) para o TwilioService, que formatará para E.164
        boolean smsSent = twilioService.sendSms(driverToCall.getPhoneNumber(), null);

        if (!smsSent) {
            log.error("Falha ao enviar SMS para motorista ID={}, Seq {}. Motorista NÃO será marcado como CALLED.",
                    driverToCall.getId(), driverToCall.getSequenceNumber());
            return Optional.empty();
        }

        log.info("SMS enviado com sucesso para {}. Marcando motorista como CALLED.", driverToCall.getPhoneNumber());
        driverToCall.setStatus("CALLED");
        driverToCall.setCallTimestamp(LocalDateTime.now()); // Assume que Driver tem setCallTimestamp
        Driver savedDriver = driverRepository.save(driverToCall);

        lastCalledDriverRef.set(savedDriver);
        log.info("Motorista ID={}, Seq={} atualizado para CALLED.", savedDriver.getId(), savedDriver.getSequenceNumber());
        return Optional.of(savedDriver);
    }

    /**
     * Chama um motorista específico pelo ID, se ele estiver esperando. Envia SMS.
     * @param driverId O ID do motorista a ser chamado.
     * @return Optional contendo o motorista chamado, ou vazio se não encontrado, não esperando ou se o SMS falhar.
     */
    @Transactional
    public Optional<Driver> callDriver(Long driverId) {
        log.info("Tentando chamar motorista individualmente pelo ID: {}", driverId);
        Optional<Driver> driverOpt = driverRepository.findById(driverId);

        if (driverOpt.isEmpty()) {
            log.warn("Motorista com ID {} não encontrado.", driverId);
            return Optional.empty();
        }

        Driver driverToCall = driverOpt.get();

        if (!"WAITING".equals(driverToCall.getStatus())) {
            log.warn("Tentativa de chamar motorista ID {} que não está com status WAITING (Status atual: {}).",
                    driverId, driverToCall.getStatus());
            return Optional.empty();
        }

        log.info("Motorista encontrado para chamada individual: ID={}, Seq={}, Placa={}, Telefone={}",
                driverToCall.getId(), driverToCall.getSequenceNumber(), driverToCall.getLicensePlate(), driverToCall.getPhoneNumber());

        // Tenta enviar o SMS (TwilioService formatará para E.164)
        log.debug("Tentando enviar SMS para {}", driverToCall.getPhoneNumber());
        // Passa o número limpo (11 dígitos)
        boolean smsSent = twilioService.sendSms(driverToCall.getPhoneNumber(), null);

        if (!smsSent) {
            log.error("Falha ao enviar SMS na chamada individual para motorista ID={}, Seq {}. Motorista NÃO será marcado como CALLED.",
                    driverToCall.getId(), driverToCall.getSequenceNumber());
            return Optional.empty();
        }

        // SMS enviado, atualiza status
        log.info("SMS enviado com sucesso para {}. Marcando motorista como CALLED.", driverToCall.getPhoneNumber());
        driverToCall.setStatus("CALLED");
        driverToCall.setCallTimestamp(LocalDateTime.now()); // Assume que Driver tem setCallTimestamp
        Driver savedDriver = driverRepository.save(driverToCall);

        lastCalledDriverRef.set(savedDriver);
        log.info("Motorista ID={}, Seq={} atualizado para CALLED (chamada individual).", savedDriver.getId(), savedDriver.getSequenceNumber());
        return Optional.of(savedDriver);
    }

    /**
     * Reenvia o SMS para o último motorista que foi chamado com sucesso.
     * @return true se o SMS foi reenviado com sucesso, false caso contrário.
     */
    public boolean recallLastCalledDriver() {
        Driver lastCalledDriver = lastCalledDriverRef.get();

        if (lastCalledDriver == null) {
            log.warn("Nenhum motorista foi chamado anteriormente para reenviar o SMS (recall).");
            return false;
        }

        log.info("Tentando reenviar SMS (recall) para o último motorista chamado: ID={}, Seq={}, Telefone={}",
                lastCalledDriver.getId(), lastCalledDriver.getSequenceNumber(), lastCalledDriver.getPhoneNumber());

        Optional<Driver> currentDriverState = driverRepository.findById(lastCalledDriver.getId());
        if (currentDriverState.isPresent() && "CALLED".equals(currentDriverState.get().getStatus())) {
            // Passa o número limpo (11 dígitos) para o TwilioService
            boolean smsSent = twilioService.sendSms(lastCalledDriver.getPhoneNumber(), null);
            if (smsSent) {
                log.info("Recall SMS reenviado com sucesso para {}.", lastCalledDriver.getPhoneNumber());
                return true;
            } else {
                log.error("Falha ao reenviar (recall) SMS para {}.", lastCalledDriver.getPhoneNumber());
                return false;
            }
        } else {
            log.warn("O último motorista chamado (ID={}) não está mais no estado 'CALLED' no banco ou não foi encontrado. Limpando referência.",
                    lastCalledDriver.getId());
            lastCalledDriverRef.set(null);
            return false;
        }
    }

    /**
     * Retorna a lista de motoristas com status WAITING, ordenados por número de sequência.
     * Usado pela API /api/drivers/waiting.
     * @return Lista de motoristas em espera.
     */
    @Transactional(readOnly = true)
    public List<Driver> getWaitingDrivers() {
        log.debug("Buscando motoristas com status WAITING ordenados por sequência.");
        // --- CORRIGIDO ---
        return driverRepository.findByStatusOrderBySequenceNumberAsc("WAITING");
    }

    /**
     * Retorna a lista de TODOS os motoristas ordenados pelo número de sequência.
     * (Este método existia no seu código original, mantido caso seja útil para outra view).
     * @return Lista de todos os motoristas ordenados.
     */
    @Transactional(readOnly = true)
    public List<Driver> getAdminQueueView() {
        log.debug("Buscando visão da fila para o admin (todos ordenados por sequência).");
        return driverRepository.findAllByOrderBySequenceNumberAsc();
    }

    /**
     * Limpa a fila marcando TODOS os motoristas como CLEARED.
     * @return número de motoristas que foram marcados como CLEARED.
     */
    @Transactional
    public int clearQueue() {
        log.warn("Iniciando limpeza da fila (marcando todos como CLEARED)!");
        List<Driver> allDrivers = driverRepository.findAll();
        int count = 0;
        for (Driver driver : allDrivers) {
            driver.setStatus("CLEARED");
            // Considerar setar callTimestamp para null também
            // driver.setCallTimestamp(null);
            driverRepository.save(driver);
            count++;
        }
        lastCalledDriverRef.set(null);
        log.warn("Fila limpa. {} motoristas marcados como CLEARED.", count);
        return count;
    }
}