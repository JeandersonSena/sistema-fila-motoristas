package com.suaempresa.driverqueue.controller;

import com.suaempresa.driverqueue.model.Driver;
import com.suaempresa.driverqueue.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model; // Model ainda é usado por showAdminPage
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Controller responsável pelas funcionalidades administrativas da fila de motoristas.
 * Fornece a página de visualização e endpoints da API REST para gerenciamento da fila
 * (usados pelo JavaScript da página de admin), incluindo re-chamadas.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private final DriverService driverService;

    public AdminController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping
    public String showAdminPage(Model model) {
        log.info("GET /admin : Exibindo página de administração.");
        return "admin-view";
    }

    // MÉTODO showDriverForm REMOVIDO DESTA CLASSE

    @GetMapping("/queue")
    @ResponseBody
    public ResponseEntity<List<Driver>> getQueueData() {
        log.debug("API GET /admin/queue : Buscando dados da fila de espera.");
        try {
            List<Driver> queue = driverService.getAdminQueueView();
            return ResponseEntity.ok(queue);
        } catch (Exception e) {
            log.error("API GET /admin/queue : Erro ao buscar dados da fila de espera!", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    @GetMapping("/called-drivers")
    @ResponseBody
    public ResponseEntity<List<Driver>> getCalledDriversData() {
        log.debug("API GET /admin/called-drivers : Buscando dados de motoristas chamados.");
        try {
            List<Driver> calledDrivers = driverService.getCalledDriversView();
            return ResponseEntity.ok(calledDrivers);
        } catch (Exception e) {
            log.error("API GET /admin/called-drivers : Erro ao buscar dados de motoristas chamados!", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    @PostMapping("/call-next")
    @ResponseBody
    public ResponseEntity<?> callNextDriver() {
        // ... (código existente) ...
        log.info("API POST /admin/call-next : Requisição para chamar próximo motorista.");
        try {
            Optional<Driver> calledDriverOpt = driverService.callNextDriver();
            if (calledDriverOpt.isPresent()) {
                log.info("API POST /admin/call-next : Motorista {} chamado com sucesso.", calledDriverOpt.get().getName());
                return ResponseEntity.ok(calledDriverOpt.get());
            } else {
                log.info("API POST /admin/call-next : Nenhum motorista na fila para chamar.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Nenhum motorista aguardando na fila.");
            }
        } catch (Exception e) {
            log.error("API POST /admin/call-next : Erro inesperado!", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno ao processar a chamada.");
        }
    }

    @PostMapping("/clear-queue")
    @ResponseBody
    public ResponseEntity<String> clearQueue() {
        // ... (código existente) ...
        log.warn("API POST /admin/clear-queue : Requisição para limpar fila.");
        if (!confirmActionSafety()) { /* ... */ }
        try {
            int clearedCount = driverService.clearWaitingList();
            String message = "Lista de espera limpa. " + clearedCount + " motorista(s) atualizado(s) para CLEARED.";
            log.warn(message);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("API POST /admin/clear-queue : Erro inesperado!", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno ao limpar a fila.");
        }
    }

    @PostMapping("/driver/{id}/attended")
    @ResponseBody
    public ResponseEntity<String> markAttended(@PathVariable Long id) {
        // ... (código existente) ...
        log.info("API POST /admin/driver/{}/attended : Marcando como compareceu.", id);
        try {
            driverService.markDriverAsAttended(id);
            return ResponseEntity.ok("Motorista ID " + id + " marcado como ATTENDED.");
        } catch (IllegalArgumentException e) {
            log.warn("API POST /admin/driver/{}/attended : Falha - {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("API POST /admin/driver/{}/attended : Erro inesperado!", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno ao marcar comparecimento.");
        }
    }

    @PostMapping("/driver/{id}/recall")
    @ResponseBody
    public ResponseEntity<?> recallDriverEndpoint(@PathVariable Long id) {
        // ... (código existente) ...
        log.info("API POST /admin/driver/{}/recall : Tentando chamar motorista ID {} novamente.", id, id); // Ajustei o log
        try {
            Optional<Driver> recalledDriverOpt = driverService.recallDriver(id);
            if (recalledDriverOpt.isPresent()) {
                Driver driver = recalledDriverOpt.get();
                if (driver.getStatus() == Driver.DriverStatus.NO_SHOW) {
                    log.info("API POST /admin/driver/{}/recall : Motorista ID {} ({}) atingiu limite e foi para NO_SHOW.", id, driver.getName());
                    return ResponseEntity.ok().body("Motorista ID " + id + " (" + driver.getName() + ") atingiu o limite de re-chamadas e foi marcado como NÃO COMPARECEU.");
                } else {
                    log.info("API POST /admin/driver/{}/recall : Motorista ID {} ({}) chamado novamente. Status: {}. Tentativas: {}.",
                            id, driver.getName(), driver.getStatus(), driver.getCallAttempts());
                    return ResponseEntity.ok(driver);
                }
            } else {
                // Este caso não deveria ser alcançado com a lógica atual do service que lança exceção
                log.error("API POST /admin/driver/{}/recall : Optional vazio retornado pelo serviço, inesperado.", id);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro inesperado ao tentar chamar novamente o motorista ID " + id);
            }
        } catch (IllegalArgumentException e) {
            log.warn("API POST /admin/driver/{}/recall : Falha - {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("API POST /admin/driver/{}/recall : Erro inesperado!", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno ao tentar chamar novamente o motorista ID " + id + ".");
        }
    }

    /*
    // Opcional: manter para ação administrativa direta
    @PostMapping("/driver/{id}/no-show")
    @ResponseBody
    public ResponseEntity<String> markNoShow(@PathVariable Long id) { ... }
    */

    private boolean confirmActionSafety() {
        return true;
    }
}