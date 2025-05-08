package com.suaempresa.driverqueue.controller;

import com.suaempresa.driverqueue.model.Driver;
import com.suaempresa.driverqueue.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*; // Import geral para anotações
import org.springframework.web.bind.annotation.PathVariable; // <-- IMPORT NECESSÁRIO

import java.util.Collections;
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
        return "admin-view";
    }

    /**
     * Endpoint da API REST para obter a lista atual de motoristas aguardando (status WAITING).
     * Mapeado para GET /admin/queue.
     *
     * @return ResponseEntity contendo a lista de motoristas (JSON) e status 200 OK,
     *         ou status 500 Internal Server Error em caso de falha.
     */
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

    /**
     * Endpoint da API REST para obter a lista atual de motoristas JÁ CHAMADOS (status CALLED).
     * Mapeado para GET /admin/called-drivers.
     *
     * @return ResponseEntity contendo a lista de motoristas chamados (JSON) e status 200 OK,
     *         ou status 500 Internal Server Error em caso de falha.
     */
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

    /**
     * Endpoint da API REST para chamar o próximo motorista da fila.
     * Mapeado para POST /admin/call-next.
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
     *
     * @return ResponseEntity contendo uma mensagem de sucesso e status 200 OK,
     *         ou status apropriado em caso de erro (ex: 500).
     */
    @PostMapping("/clear-queue")
    @ResponseBody
    public ResponseEntity<String> clearQueue() {
        log.warn("API POST /admin/clear-queue : Requisição para limpar fila.");
        if (!confirmActionSafety()) {
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

    // --- NOVOS ENDPOINTS PARA CONFIRMAÇÃO DE COMPARECIMENTO ---

    /**
     * Endpoint da API REST para marcar um motorista como compareceu (ATTENDED).
     * Mapeado para POST /admin/driver/{id}/attended
     * O ID do motorista é passado como parte da URL (PathVariable).
     *
     * @param id O ID do motorista a ser marcado.
     * @return ResponseEntity com status 200 OK e mensagem de sucesso,
     *         ou status 400 Bad Request se o motorista não puder ser marcado (não encontrado/status errado),
     *         ou status 500 Internal Server Error para outros erros.
     */
    @PostMapping("/driver/{id}/attended") // <<< NOVO MÉTODO ADICIONADO AQUI
    @ResponseBody
    public ResponseEntity<String> markAttended(@PathVariable Long id) {
        log.info("API POST /admin/driver/{}/attended : Marcando como compareceu.", id);
        try {
            driverService.markDriverAsAttended(id);
            return ResponseEntity.ok("Motorista ID " + id + " marcado como ATTENDED.");
        } catch (IllegalArgumentException e) {
            // Erro esperado se ID não existe ou status errado
            log.warn("API POST /admin/driver/{}/attended : Falha - {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("API POST /admin/driver/{}/attended : Erro inesperado!", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno ao marcar comparecimento.");
        }
    }

    /**
     * Endpoint da API REST para marcar um motorista como não compareceu (NO_SHOW).
     * Mapeado para POST /admin/driver/{id}/no-show
     * O ID do motorista é passado como parte da URL (PathVariable).
     *
     * @param id O ID do motorista a ser marcado.
     * @return ResponseEntity com status 200 OK e mensagem de sucesso,
     *         ou status 400 Bad Request se o motorista não puder ser marcado (não encontrado/status errado),
     *         ou status 500 Internal Server Error para outros erros.
     */
    @PostMapping("/driver/{id}/no-show") // <<< NOVO MÉTODO ADICIONADO AQUI
    @ResponseBody
    public ResponseEntity<String> markNoShow(@PathVariable Long id) {
        log.warn("API POST /admin/driver/{}/no-show : Marcando como NÃO compareceu.", id);
        try {
            driverService.markDriverAsNoShow(id);
            return ResponseEntity.ok("Motorista ID " + id + " marcado como NO_SHOW.");
        } catch (IllegalArgumentException e) {
            log.warn("API POST /admin/driver/{}/no-show : Falha - {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("API POST /admin/driver/{}/no-show : Erro inesperado!", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro interno ao marcar não comparecimento.");
        }
    }

    // Método auxiliar apenas para exemplo, remova ou implemente lógica real se necessário.
    private boolean confirmActionSafety() {
        return true;
    }

} // <<< FIM DA CLASSE AdminController