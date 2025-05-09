package com.suaempresa.driverqueue.controller;

import com.suaempresa.driverqueue.dto.DriverInputDto;
import com.suaempresa.driverqueue.model.Driver;
import com.suaempresa.driverqueue.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
    @GetMapping("/")
    public String showDriverForm(Model model) {
        // Adiciona um DTO vazio ao modelo se não vier de um redirect com erros
        if (!model.containsAttribute("driverInputDto")) {
            model.addAttribute("driverInputDto", new DriverInputDto());
        }
        log.info("GET / : Exibindo formulário de entrada.");
        return "index";
    }

    @GetMapping("/queue")
    @ResponseBody
    public ResponseEntity<List<Driver>> getQueueData() {
        // ... (sem mudanças) ...
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
        // ... (sem mudanças) ...
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
        // ... (sem mudanças) ...
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
        // ... (sem mudanças) ...
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

    /**
     * Endpoint da API REST para marcar um motorista como compareceu (ATTENDED).
     * Mapeado para POST /admin/driver/{id}/attended
     */
    @PostMapping("/driver/{id}/attended")
    @ResponseBody
    public ResponseEntity<String> markAttended(@PathVariable Long id) {
        // ... (sem mudanças) ...
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

    /**
     * Endpoint da API REST para tentar chamar novamente um motorista que já foi chamado (status CALLED).
     * Se o limite de tentativas for atingido, o status do motorista mudará para NO_SHOW.
     * Mapeado para POST /admin/driver/{id}/recall
     *
     * @param id O ID do motorista a ser chamado novamente.
     * @return ResponseEntity com os dados do motorista (status CALLED ou NO_SHOW) e 200 OK,
     *         ou status apropriado em caso de erro (400 Bad Request, 500 Internal Server Error).
     */
    @PostMapping("/driver/{id}/recall") // <<< ENDPOINT ALTERADO/RENOMEADO
    @ResponseBody
    public ResponseEntity<?> recallDriverEndpoint(@PathVariable Long id) { // <<< MÉTODO RENOMEADO
        log.info("API POST /admin/driver/{}/recall : Tentando chamar motorista ID {} novamente.", id, id);
        try {
            Optional<Driver> recalledDriverOpt = driverService.recallDriver(id); // Chama o novo método do serviço

            // O método recallDriver agora retorna um Optional<Driver> e lida com a transição para NO_SHOW
            // ou lança exceção se não puder re-chamar.
            if (recalledDriverOpt.isPresent()) {
                Driver driver = recalledDriverOpt.get();
                if (driver.getStatus() == Driver.DriverStatus.NO_SHOW) {
                    log.info("API POST /admin/driver/{}/recall : Motorista ID {} ({}) atingiu limite e foi para NO_SHOW.", id, driver.getName());
                    return ResponseEntity.ok().body("Motorista ID " + id + " (" + driver.getName() + ") atingiu o limite de re-chamadas e foi marcado como NÃO COMPARECEU.");
                } else {
                    log.info("API POST /admin/driver/{}/recall : Motorista ID {} ({}) chamado novamente. Status: {}. Tentativas: {}.",
                            id, driver.getName(), driver.getStatus(), driver.getCallAttempts());
                    return ResponseEntity.ok(driver); // Retorna o motorista (ainda CALLED)
                }
            } else {
                // Este caso não deveria acontecer se o recallDriver lança exceção em falha de busca.
                // Mas é bom ter um fallback.
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

    // O endpoint para marcar manualmente como NO_SHOW ainda pode ser mantido se você quiser
    // uma forma administrativa de fazer isso, independente do fluxo de "Chamar Novamente".
    // Se não, pode ser removido. Por ora, vamos mantê-lo comentado para referência.
    /*
    @PostMapping("/driver/{id}/no-show")
    @ResponseBody
    public ResponseEntity<String> markNoShow(@PathVariable Long id) {
        log.warn("API POST /admin/driver/{}/no-show : Marcando como NÃO compareceu (ação manual).", id);
        try {
            driverService.markDriverAsNoShow(id);
            return ResponseEntity.ok("Motorista ID " + id + " marcado manualmente como NO_SHOW.");
        } catch (IllegalArgumentException e) {
            log.warn("API POST /admin/driver/{}/no-show : Falha - {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("API POST /admin/driver/{}/no-show : Erro inesperado!", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno ao marcar não comparecimento.");
        }
    }
    */

    private boolean confirmActionSafety() {
        return true;
    }
}