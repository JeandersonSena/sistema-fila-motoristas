package com.suaempresa.driverqueue.controller;

import com.suaempresa.driverqueue.model.Driver;
import com.suaempresa.driverqueue.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DriverWebController {

    private static final Logger log = LoggerFactory.getLogger(DriverWebController.class);

    @Autowired private DriverService driverService;

    @GetMapping("/")
    public String showDriverEntryForm(Model model) {
        log.debug("Acessando formulário de entrada do motorista");
        if (!model.containsAttribute("driver")) {
            model.addAttribute("driver", new Driver());
        }
        return "driver-entry";
    }

    @PostMapping("/drivers/add")
    public String handleDriverEntry(@ModelAttribute Driver driver, RedirectAttributes redirectAttributes) {
        // --- MANTIDO IGUAL ---
        // Ajuste os campos (getPhoneNumber, getSequenceNumber) conforme o seu Model 'Driver'
        log.info("Recebida submissão: Placa={}, Telefone={}", driver.getLicensePlate(), driver.getPhoneNumber());
        try {
            Driver savedDriver = driverService.addDriver(driver.getLicensePlate(), driver.getPhoneNumber());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Entrada registrada! Sua sequência é: " + savedDriver.getSequenceNumber());
            log.info("Motorista adicionado com sucesso, Seq: {}", savedDriver.getSequenceNumber());
        } catch (IllegalArgumentException e) {
            log.warn("Erro de validação ao adicionar motorista: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("driver", driver);
        } catch (Exception e) {
            log.error("Erro inesperado ao adicionar motorista", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Erro inesperado no servidor. Tente novamente.");
            redirectAttributes.addFlashAttribute("driver", driver);
        }
        return "redirect:/";
    }

    @GetMapping("/admin")
    public String showAdminView() {
        log.debug("Acessando painel de administração");
        // A página admin é carregada e seu conteúdo é preenchido pelo admin.js via API
        return "admin-view";
    }

    // REMOVIDO: Endpoint /admin/clear via POST por formulário.
    // A limpeza agora é feita pelo botão na página admin que chama a API via admin.js
    /*
    @PostMapping("/admin/clear")
    public String handleClearQueue(RedirectAttributes redirectAttributes) {
        log.warn("Recebida requisição para limpar a fila via POST /admin/clear");
        try {
            int clearedCount = driverService.clearQueue();
            redirectAttributes.addFlashAttribute("adminSuccessMessage", "Fila limpa com sucesso. " + clearedCount + " motoristas afetados.");
        } catch (Exception e) {
            log.error("Erro ao limpar a fila", e);
            redirectAttributes.addFlashAttribute("adminErrorMessage", "Erro ao limpar a fila.");
        }
        return "redirect:/admin";
    }
    */
}