package com.suaempresa.driverqueue.controller;

import com.suaempresa.driverqueue.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DriverWebController {

    private static final Logger log = LoggerFactory.getLogger(DriverWebController.class);
    private final DriverService driverService;

    // Injeção via construtor (fornecida pelo Spring)
    public DriverWebController(DriverService driverService) {
        this.driverService = driverService;
    }

    // Mapeamento para exibir o formulário inicial
    @GetMapping("/")
    public String showDriverForm(Model model) {
        // Apenas retorna o nome da view (index.html)
        // Mensagens são passadas via redirectAttributes se vierem do POST
        log.debug("Acessando GET / - Exibindo formulário de entrada.");
        return "index";
    }

    // Mapeamento para processar o envio do formulário
    @PostMapping("/drivers/add")
    public String addDriver(@RequestParam String plate,
                            @RequestParam String name,
                            @RequestParam String phoneNumber, // Recebe o telefone do formulário
                            RedirectAttributes redirectAttributes) { // Para passar mensagens após redirect

        log.info("Recebido POST /drivers/add: Placa='{}', Nome='{}', Telefone='{}'", plate, name, phoneNumber);
        try {
            // Tenta adicionar o motorista através do serviço
            driverService.addDriver(plate, name, phoneNumber);
            // Se sucesso, adiciona mensagem de sucesso para a próxima requisição (após redirect)
            redirectAttributes.addFlashAttribute("successMessage", "Motorista '" + name + "' adicionado à fila com sucesso!");
            log.info("Motorista '{}' adicionado com sucesso.", name);

        } catch (IllegalArgumentException e) {
            // Captura erros de validação específicos lançados pelo DriverService
            redirectAttributes.addFlashAttribute("errorMessage", "Erro ao adicionar: " + e.getMessage());
            log.warn("Falha na validação ao adicionar motorista '{}': {}", name, e.getMessage());

        } catch (Exception e) {
            // Captura qualquer outro erro inesperado durante o processamento
            redirectAttributes.addFlashAttribute("errorMessage", "Ocorreu um erro inesperado. Por favor, tente novamente ou contate o suporte.");
            log.error("Erro inesperado no POST /drivers/add para Placa='{}', Nome='{}': {}", plate, name, e.getMessage(), e);
        }

        // Redireciona de volta para a página inicial (GET /)
        // Isso segue o padrão Post-Redirect-Get para evitar reenvios de formulário
        return "redirect:/";
    }
}