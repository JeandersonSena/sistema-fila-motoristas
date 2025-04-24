package com.suaempresa.driverqueue.controller;

import com.suaempresa.driverqueue.model.Driver;
import com.suaempresa.driverqueue.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus; // Importa HttpStatus
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin") // Todas as URLs aqui começarão com /admin
public class DriverApiController {

    private static final Logger log = LoggerFactory.getLogger(DriverApiController.class);
    private final DriverService driverService;

    // Injeção via construtor
    public DriverApiController(DriverService driverService) {
        this.driverService = driverService;
    }

    // Exibe a página HTML de administração
    @GetMapping
    public String showAdminPage(Model model) {
        log.debug("Acessando GET /admin - Exibindo página admin-view.html");
        // A fila será carregada dinamicamente via JavaScript/API
        return "admin-view";
    }

    // --- Endpoints da API REST para o JavaScript ---

    // Retorna a lista atual de motoristas na fila (status WAITING)
    @GetMapping("/queue")
    @ResponseBody // Indica que o retorno é o corpo da resposta (JSON)
    public ResponseEntity<List<Driver>> getQueueData() {
        log.trace("API GET /admin/queue chamada."); // Trace para chamadas frequentes
        try {
            List<Driver> queue = driverService.getAdminQueueView();
            return ResponseEntity.ok(queue); // Retorna 200 OK com a lista
        } catch (Exception e) {
            log.error("Erro ao buscar dados para API GET /admin/queue", e);
            // Retorna 500 Internal Server Error (sem corpo ou com mensagem genérica)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Chama o próximo motorista na fila
    @PostMapping("/call-next")
    @ResponseBody
    public ResponseEntity<?> callNextDriver() { // Retorno genérico com ResponseEntity
        log.info("Recebido POST /admin/call-next");
        try {
            Optional<Driver> calledDriverOpt = driverService.callNextDriver();
            if (calledDriverOpt.isPresent()) {
                log.info("Motorista {} chamado com sucesso via API.", calledDriverOpt.get().getName());
                // Retorna 200 OK com os dados do motorista chamado
                return ResponseEntity.ok(calledDriverOpt.get());
            } else {
                log.info("Nenhum motorista na fila para chamar via API.");
                // Retorna 404 Not Found com uma mensagem no corpo
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Nenhum motorista aguardando na fila.");
            }
        } catch (Exception e) {
            log.error("Erro inesperado no POST /admin/call-next", e);
            // Retorna 500 Internal Server Error com uma mensagem
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno ao processar a chamada.");
        }
    }

    // Limpa todos os motoristas com status WAITING
    @PostMapping("/clear-queue")
    @ResponseBody
    public ResponseEntity<String> clearQueue() { // Retorna uma string de confirmação
        log.warn("Recebido POST /admin/clear-queue");
        if (!confirmActionSafety()) { // Medida de segurança extra (se aplicável)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ação não permitida no momento.");
        }
        try {
            int clearedCount = driverService.clearWaitingList();
            String message = "Lista de espera limpa. " + clearedCount + " motorista(s) atualizado(s) para CLEARED.";
            log.warn(message);
            // Retorna 200 OK com a mensagem de confirmação
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("Erro inesperado no POST /admin/clear-queue", e);
            // Retorna 500 Internal Server Error com uma mensagem de erro
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno ao limpar a fila.");
        }
    }

    // Método auxiliar de segurança (exemplo, pode ser removido ou adaptado)
    private boolean confirmActionSafety() {
        // Implementar lógica se necessário (ex: verificar se há chamadas em andamento, etc.)
        return true; // Permitir por padrão
    }
}