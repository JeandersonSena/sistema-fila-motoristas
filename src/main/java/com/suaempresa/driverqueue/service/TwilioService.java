package com.suaempresa.driverqueue.service; // Mantendo o pacote que você indicou

import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber; // Import correto
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TwilioService {

    private static final Logger log = LoggerFactory.getLogger(TwilioService.class);

    // As credenciais são usadas na inicialização do Twilio, geralmente em uma classe @Configuration
    // @Value("${twilio.account_sid}")
    // private String accountSid;
    // @Value("${twilio.auth_token}")
    // private String authToken;

    @Value("${twilio.phone_number}") // SEU número Twilio configurado no application.properties
    private String twilioPhoneNumber;

    // Mensagem padrão se nenhuma for fornecida
    private static final String DEFAULT_MESSAGE = "Seu veículo está sendo chamado. Por favor, dirija-se à área designada.";

    /**
     * Envia um SMS usando Twilio, formatando o número brasileiro para E.164.
     * @param brazilianNumber O número de telefone do destinatário (esperado com 11 dígitos, ex: 71997788123).
     * @param messageBody O corpo da mensagem. Se null ou vazio, usa a mensagem padrão.
     * @return true se o envio foi iniciado com sucesso pela API Twilio, false caso contrário.
     */
    public boolean sendSms(String brazilianNumber, String messageBody) {
        // Validação defensiva do número recebido do DriverService
        if (brazilianNumber == null || brazilianNumber.length() != 11 || !brazilianNumber.matches("\\d{11}")) {
            log.error("TwilioService: Recebido número brasileiro inválido ou com comprimento/formato incorreto: '{}'. Não será enviado.", brazilianNumber);
            // Log do erro que vimos antes, para referência
            log.error("Tentativa de enviar SMS com número ou mensagem inválidos.");
            return false;
        }

        // Define a mensagem final
        String finalMessageBody = (messageBody == null || messageBody.isBlank()) ? DEFAULT_MESSAGE : messageBody.trim();

        // --- FORMATAÇÃO PARA E.164 (Adiciona +55) ---
        String e164Number = "+55" + brazilianNumber;
        // ---------------------------------------------

        // Validação do número DE (Twilio) - deve ser configurado corretamente
        if (twilioPhoneNumber == null || twilioPhoneNumber.isBlank()) {
            log.error("Número de telefone Twilio remetente (twilio.phone_number) não configurado no application.properties.");
            return false;
        }

        log.debug("Tentando enviar SMS via Twilio. De: {}, Para: {}, Mensagem: '{}'", twilioPhoneNumber, e164Number, finalMessageBody);

        try {
            // Cria e envia a mensagem
            Message message = Message.creator(
                    new com.twilio.type.PhoneNumber(e164Number),        // Destinatário formatado
                    new com.twilio.type.PhoneNumber(twilioPhoneNumber), // Remetente (Seu número Twilio)
                    finalMessageBody
            ).create(); // Envia a requisição para a API Twilio

            // Log de sucesso (não garante entrega, apenas envio à API)
            log.info("SMS enviado via Twilio com sucesso. SID: {}, Status API: {}, Para: {}", message.getSid(), message.getStatus(), e164Number);
            return true;

        } catch (ApiException e) {
            // Tratamento específico para erros da API Twilio
            log.error("Erro da API Twilio ao enviar SMS para {}: Código={}, Mensagem='{}', Mais Info='{}'",
                    e164Number, e.getCode(), e.getMessage(), e.getMoreInfo(), e);
            // --- CORREÇÃO: Linhas inválidas removidas ---
            /*
            // Log para debug (ver o corpo do erro se disponível)
            if (e.getResponse() != null) {
                 log.error("Twilio API Response Body: {}", e.getResponse().getContent());
            }
            */
            return false;
        } catch (Exception e) {
            // Tratamento para outras exceções inesperadas
            log.error("Erro inesperado ('{}') ao tentar enviar SMS via Twilio para {}", e.getClass().getSimpleName(), e164Number, e);
            return false;
        }
    }
}