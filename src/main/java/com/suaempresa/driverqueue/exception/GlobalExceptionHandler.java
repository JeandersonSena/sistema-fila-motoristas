package com.suaempresa.driverqueue.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.stream.Collectors;

/**
 * Handler de Exceções Global para toda a aplicação web.
 * Captura exceções lançadas pelos Controllers e fornece respostas padronizadas
 * ou redirecionamentos com mensagens de erro apropriadas.
 * Usa a anotação {@code @ControllerAdvice} para ser detectado pelo Spring MVC.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Manipula exceções de validação de beans ({@code @Valid} falhou).
     * Captura {@link MethodArgumentNotValidException} (geralmente de {@code @RequestBody})
     * e {@link BindException} (geralmente de {@code @ModelAttribute}).
     * Extrai as mensagens de erro de validação, loga um aviso, e redireciona
     * o usuário de volta para a página de origem (presumidamente o formulário)
     * com as mensagens de erro e os dados preenchidos anteriormente.
     *
     * @param ex A exceção de validação capturada.
     * @param redirectAttributes Para adicionar atributos flash (mensagens, dados) para o redirect.
     * @return A string de redirecionamento (ex: "redirect:/").
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrityViolationException(DataIntegrityViolationException ex, RedirectAttributes redirectAttributes) {
        String userMessage = "Erro de integridade de dados ao salvar."; // Mensagem padrão
        String rootCauseMessage = ex.getMostSpecificCause().getMessage(); // Pega a mensagem da causa raiz (ex: PSQLException)

        log.warn("Erro de DataIntegrityViolation capturado: {}", rootCauseMessage); // Loga a causa real

        // Tenta identificar a constraint específica (isso pode variar um pouco entre bancos)
        if (rootCauseMessage != null) {
            if (rootCauseMessage.contains("driver_plate_key")) { // Nome da constraint UNIQUE da placa
                userMessage = "Erro: A placa informada já está cadastrada no sistema.";
            } else if (rootCauseMessage.contains("violates unique constraint")) {
                // Outra constraint única genérica
                userMessage = "Erro: Já existe um registro com um dos valores informados (possivelmente placa ou outro campo único).";
            } else if (rootCauseMessage.contains("violates foreign key constraint")) {
                // Exemplo se tivéssemos chaves estrangeiras
                userMessage = "Erro: Tentativa de usar um valor relacionado que não existe.";
            }
            // Adicionar mais 'else if' para outras constraints conhecidas
        }

        redirectAttributes.addFlashAttribute("errorMessage", userMessage);
        // Não estamos devolvendo o DTO aqui, pois a falha foi no banco, não na validação inicial.
        // Poderíamos tentar extrair os dados da exceção se necessário, mas complica.
        return "redirect:/";
    }

    /**
     * Manipulador genérico para qualquer outra exceção não tratada pelos handlers específicos.
     * Isso evita que o usuário final veja stack traces ou páginas de erro padrão do servidor.
     * Loga o erro completo (nível ERROR) para diagnóstico e exibe uma página de erro amigável (error/500.html).
     *
     * @param ex A exceção genérica capturada.
     * @return Um objeto {@link ModelAndView} configurado para renderizar a view "error/500"
     *         com mensagens de erro apropriadas.
     */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleGenericException(Exception ex) {
        log.error("Erro inesperado capturado pelo GlobalExceptionHandler!", ex);

        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setStatus(HttpStatus.INTERNAL_SERVER_ERROR); // Define o status HTTP
        modelAndView.addObject("errorMessage", "Ocorreu um erro inesperado no servidor. Por favor, tente novamente mais tarde ou contate o suporte.");
        // CUIDADO ao expor ex.getMessage() diretamente, pode vazar detalhes internos.
        // modelAndView.addObject("exceptionDetails", ex.getMessage()); // Para debug apenas
        modelAndView.setViewName("error/500"); // Define o template HTML a ser usado

        return modelAndView;
    }
}