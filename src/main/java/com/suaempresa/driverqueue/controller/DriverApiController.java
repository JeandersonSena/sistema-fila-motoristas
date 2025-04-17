package com.suaempresa.driverqueue.controller;

import com.suaempresa.driverqueue.model.Driver;
import com.suaempresa.driverqueue.service.DriverService; // Verifique se este é o pacote correto para DriverService
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/drivers") // Base path para todos os endpoints da API de motoristas
public class DriverApiController {

    private static final Logger log = LoggerFactory.getLogger(DriverApiController.class);

    @Autowired
    private DriverService driverService; // Injeção da camada de serviço

    /**
     * Endpoint: GET /api/drivers/waiting
     * Retorna a lista de motoristas atualmente com status 'WAITING'.
     * Usado pelo frontend para popular a tabela de espera.
     * @return ResponseEntity com a lista de motoristas ou erro 500.
     */
    @GetMapping("/waiting")
    public ResponseEntity<List<Driver>> getWaitingDriversApi() {
        log.debug("API Request: GET /api/drivers/waiting");
        try {
            List<Driver> waitingDrivers = driverService.getWaitingDrivers();
            return ResponseEntity.ok(waitingDrivers); // Retorna 200 OK com a lista no corpo
        } catch (Exception e) {
            log.error("API Error: Falha ao buscar motoristas em espera.", e);
            // Retorna 500 Internal Server Error sem corpo ou com uma mensagem genérica
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint: POST /api/drivers/{id}/call
     * Tenta chamar um motorista específico pelo seu ID.
     * Usado pelos botões "Chamar" individuais na tabela do admin.
     * @param id O ID do motorista a ser chamado (extraído da URL).
     * @return ResponseEntity 200 OK se sucesso, 404 Not Found se não encontrado/esperando ou falha no SMS, 500 em caso de erro interno.
     */
    @PostMapping("/{id}/call")
    public ResponseEntity<?> callSpecificDriverApi(@PathVariable Long id) {
        log.info("API Request: POST /api/drivers/{}/call", id);
        try {
            Optional<Driver> calledDriver = driverService.callDriver(id); // Chama o método do serviço

            if (calledDriver.isPresent()) {
                log.info("API Response: Motorista ID {} chamado com sucesso.", id);
                return ResponseEntity.ok().build(); // 200 OK, sem corpo é suficiente
            } else {
                // O service logou o motivo (não encontrado, não esperando, falha SMS)
                log.warn("API Response: Motorista ID {} não pôde ser chamado (não encontrado, status inválido ou falha no SMS).", id);
                // Retornar 404 é apropriado para "recurso não encontrado na condição esperada"
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Motorista não encontrado, não está esperando ou falha ao enviar SMS.");
            }
        } catch (IllegalArgumentException e) {
            log.error("API Error: Argumento inválido ao chamar motorista {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage()); // 400 Bad Request
        }
        catch (Exception e) {
            log.error("API Error: Erro inesperado ao chamar motorista {}.", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno no servidor.");
        }
    }

    /**
     * Endpoint: POST /api/drivers/clear
     * Limpa a fila, marcando todos os motoristas como 'CLEARED'.
     * Usado pelo botão "Limpar Fila de Espera".
     * @return ResponseEntity 200 OK com mensagem de sucesso ou 500 em caso de erro.
     */
    @PostMapping("/clear")
    public ResponseEntity<String> clearWaitingListApi() { // Especificando <String> para o corpo
        log.warn("API Request: POST /api/drivers/clear");
        try {
            int clearedCount = driverService.clearQueue();
            String message = "Fila limpa com sucesso. " + clearedCount + " motoristas marcados como CLEARED.";
            log.info("API Response: {}", message);
            return ResponseEntity.ok(message); // 200 OK com mensagem no corpo
        } catch (Exception e) {
            log.error("API Error: Falha ao limpar a fila.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno ao limpar a fila.");
        }
    }

    /**
     * Endpoint: POST /api/drivers/call-next
     * Tenta chamar o próximo motorista disponível na fila (menor sequência, status WAITING).
     * Usado pelo botão "Chamar Próximo Motorista".
     * @return ResponseEntity 200 OK com o motorista chamado no corpo, 404 se fila vazia/falha SMS, 500 em caso de erro.
     */
    @PostMapping("/call-next")
    public ResponseEntity<?> callNextDriverApi() {
        log.info("API Request: POST /api/drivers/call-next");
        try {
            Optional<Driver> calledDriver = driverService.callNextDriver();
            if (calledDriver.isPresent()) {
                log.info("API Response: Próximo motorista chamado com sucesso (ID: {}).", calledDriver.get().getId());
                // Retorna 200 OK com os dados do motorista chamado no corpo
                return ResponseEntity.ok(calledDriver.get());
            } else {
                log.info("API Response: Não há próximo motorista para chamar ou falha no SMS.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Nenhum motorista esperando na fila ou falha ao enviar SMS."); // 404 Not Found
            }
        } catch (Exception e) {
            log.error("API Error: Erro inesperado ao chamar o próximo motorista.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno no servidor.");
        }
    }

    /**
     * Endpoint: POST /api/drivers/recall-last
     * Tenta reenviar o SMS para o último motorista chamado com sucesso.
     * Usado pelo botão "Chamar Novamente (Último)".
     * (Mapeamento corrigido de /call-again para /recall-last para corresponder ao JS anterior)
     * @return ResponseEntity 200 OK com mensagem se sucesso, 400 Bad Request se falha/sem último motorista, 500 em caso de erro.
     */
    @PostMapping("/recall-last") // <<-- CORRIGIDO para corresponder ao JS
    public ResponseEntity<String> recallLastCalledDriverApi() {
        log.info("API Request: POST /api/drivers/recall-last");
        try {
            boolean recalled = driverService.recallLastCalledDriver();
            if (recalled) {
                String message = "SMS reenviado com sucesso para o último motorista chamado.";
                log.info("API Response: {}", message);
                return ResponseEntity.ok(message); // 200 OK com mensagem
            } else {
                String message = "Não foi possível reenviar SMS (sem último motorista chamado ou falha no envio).";
                log.warn("API Response: {}", message);
                // 400 Bad Request indica que a requisição não pôde ser processada no estado atual
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
            }
        } catch (Exception e) {
            log.error("API Error: Erro inesperado ao tentar reenviar SMS (recall).", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno no servidor.");
        }
    }
}