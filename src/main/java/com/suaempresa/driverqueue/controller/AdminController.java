package com.suaempresa.driverqueue.controller;

import com.suaempresa.driverqueue.model.Driver;
import com.suaempresa.driverqueue.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Controller responsável pelas funcionalidades administrativas da fila de motoristas.
 * Fornece a página de visualização e endpoints da API REST para gerenciamento da fila
 * (usados pelo JavaScript da página de admin).
 */
@Controller
@RequestMapping("/admin") // Todas as rotas neste controller começam com /admin
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private final DriverService driverService;

    /**
     * Construtor para injeção de dependência do DriverService.
     * @param driverService O serviço que contém a lógica de negócio dos motoristas.
     */
    public AdminController(DriverService driverService) {
        this.driverService = driverService;
    }

    /**
     * Exibe a página HTML principal de administração da fila.
     * Mapeado para GET /admin.
     *
     * @param model Objeto Model do Spring MVC.
     * @return O nome da view Thymeleaf ("admin-view").
     */
    @GetMapping
    public String showAdminPage(Model model) {
        log.info("GET /admin : Exibindo página de administração.");
        // A fila é carregada dinamicamente via JavaScript chamando a API /admin/queue
        return "admin-view";
    }

    /**
     * Endpoint da API REST para obter a lista atual de motoristas aguardando (status WAITING).
     * Usado pelo JavaScript da página de administração para atualização (polling).
     * Mapeado para GET /admin/queue.
     *
     * @return ResponseEntity contendo a lista de motoristas (JSON) e status 200 OK,
     *         ou status 500 Internal Server Error em caso de falha.
     */
    @GetMapping("/queue")
    @ResponseBody // Retorna o corpo da resposta diretamente (serializado para JSON)
    public ResponseEntity<List<Driver>> getQueueData() {
        log.debug("API GET /admin/queue : Buscando dados da fila.");
        try {
            List<Driver> queue = driverService.getAdminQueueView();
            return ResponseEntity.ok(queue);
        } catch (Exception e) {
            log.error("API GET /admin/queue : Erro ao buscar dados da fila!", e);
            // Não retornar a exceção diretamente para o cliente por segurança
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Endpoint da API REST para chamar o próximo motorista da fila.
     * Mapeado para POST /admin/call-next.
     * Chama o {@link DriverService#callNextDriver()}.
     *
     * @return ResponseEntity contendo os dados do motorista chamado (JSON) e status 200 OK,
     *         ou status 404 Not Found se a fila estiver vazia,
     *         ou status 500 Internal Server Error em caso de falha inesperada.
     */
    @PostMapping("/call-next")
    @ResponseBody
    public ResponseEntity<?> callNextDriver() {
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

    /**
     * Endpoint da API REST para limpar a lista de espera (mudar status WAITING para CLEARED).
     * Mapeado para POST /admin/clear-queue.
     * Chama o {@link DriverService#clearWaitingList()}.
     *
     * @return ResponseEntity contendo uma mensagem de sucesso e status 200 OK,
     *         ou status apropriado em caso de erro (ex: 500).
     */
    @PostMapping("/clear-queue")
    @ResponseBody
    public ResponseEntity<String> clearQueue() {
        log.warn("API POST /admin/clear-queue : Requisição para limpar fila.");
        if (!confirmActionSafety()) { // Simulação de verificação de segurança
            log.warn("API POST /admin/clear-queue : Ação de limpar fila bloqueada por regras internas.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ação não permitida no momento.");
        }
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

    // Método auxiliar apenas para exemplo, remova ou implemente lógica real se necessário.
    private boolean confirmActionSafety() {
        return true;
    }
}