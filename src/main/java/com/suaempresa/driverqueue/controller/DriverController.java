package com.suaempresa.driverqueue.controller;

import com.suaempresa.driverqueue.dto.DriverInputDto;
import com.suaempresa.driverqueue.service.DriverService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller responsável pelas interações do usuário motorista com a aplicação.
 * Lida com a exibição do formulário de entrada e o processamento do registro na fila.
 */
@Controller
public class DriverController {

    private static final Logger log = LoggerFactory.getLogger(DriverController.class);
    private final DriverService driverService;

    /**
     * Construtor para injeção de dependência do DriverService.
     * @param driverService O serviço que contém a lógica de negócio dos motoristas.
     */
    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    /**
     * Exibe o formulário principal para o motorista se registrar na fila.
     * Mapeado para a raiz da aplicação (GET /).
     *
     * @param model O objeto Model do Spring MVC para adicionar atributos à view.
     * @return O nome da view Thymeleaf a ser renderizada ("index").
     */
    @GetMapping("/")
    public String showDriverForm(Model model) {
        // Adiciona um DTO vazio para o binding do formulário, se não vier de um redirect com erro
        if (!model.containsAttribute("driverInputDto")) {
            model.addAttribute("driverInputDto", new DriverInputDto());
        }
        log.info("GET / : Exibindo formulário de entrada.");
        return "index";
    }

    /**
     * Processa a submissão do formulário de registro de motorista.
     * Mapeado para POST /drivers/add.
     * Valida os dados recebidos usando as anotações no {@link DriverInputDto}.
     * Se a validação falhar, redireciona de volta ao formulário com mensagens de erro.
     * Se a validação passar, chama o {@link DriverService} para adicionar o motorista.
     * Em caso de sucesso ou erro de negócio, redireciona de volta ao formulário com mensagens apropriadas.
     * Erros inesperados são capturados pelo {@link com.suaempresa.driverqueue.exception.GlobalExceptionHandler}.
     *
     * @param driverInputDto Objeto DTO preenchido com os dados do formulário (via {@code @ModelAttribute}).
     * @param bindingResult Contém o resultado da validação acionada por {@code @Valid}.
     * @param redirectAttributes Usado para passar mensagens (sucesso/erro) para a view após o redirecionamento.
     * @return Uma string de redirecionamento ("redirect:/").
     */
    @PostMapping("/drivers/add")
    public String addDriver(@Valid @ModelAttribute DriverInputDto driverInputDto,
                            BindingResult bindingResult, // Necessário para @Valid
                            RedirectAttributes redirectAttributes) {

        log.info("POST /drivers/add : Tentativa de adicionar motorista: {}", driverInputDto);

        // A validação @Valid é tratada primeiro. Se falhar, GlobalExceptionHandler redireciona.
        // Se chegar aqui, a validação básica (formato, tamanho, etc.) passou.

        try {
            driverService.addDriver(driverInputDto.getPlate(), driverInputDto.getName(), driverInputDto.getPhoneNumber());
            redirectAttributes.addFlashAttribute("successMessage", "Motorista '" + driverInputDto.getName() + "' adicionado à fila com sucesso!");
            log.info("POST /drivers/add : Motorista '{}' adicionado com sucesso via serviço.", driverInputDto.getName());

        } catch (IllegalArgumentException e) {
            // Captura erros de LÓGICA DE NEGÓCIO do serviço (ex: placa duplicada, regra específica)
            log.warn("POST /drivers/add : Falha ao adicionar motorista '{}'. Causa: {}", driverInputDto.getName(), e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Erro ao adicionar: " + e.getMessage());
            redirectAttributes.addFlashAttribute("driverInputDto", driverInputDto); // Devolve dados ao form
        }
        // Erros inesperados (NPE, DataAccessException, etc.) são pegos pelo GlobalExceptionHandler.

        return "redirect:/";
    }
}