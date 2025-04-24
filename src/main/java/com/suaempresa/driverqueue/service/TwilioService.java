package com.suaempresa.driverqueue.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct; // Use jakarta se Spring Boot 3+
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TwilioService {

    private static final Logger log = LoggerFactory.getLogger(TwilioService.class);

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.phone-number}")
    private String twilioPhoneNumber;

    private boolean isInitialized = false;

    @PostConstruct
    public void initTwilio() {
        // Verifica se as credenciais foram carregadas corretamente das variáveis de ambiente
        if (accountSid == null || accountSid.isEmpty() || accountSid.startsWith("${") || accountSid.length() < 10) {
            log.error("### ERRO FATAL: Account SID do Twilio não configurado ou inválido nas variáveis de ambiente! ###");
            isInitialized = false;
            return;
        }
        if (authToken == null || authToken.isEmpty() || authToken.startsWith("${") || authToken.length() < 10) {
            log.error("### ERRO FATAL: Auth Token do Twilio não configurado ou inválido nas variáveis de ambiente! ###");
            isInitialized = false;
            return;
        }
        if (twilioPhoneNumber == null || twilioPhoneNumber.isEmpty() || !twilioPhoneNumber.startsWith("+") || twilioPhoneNumber.startsWith("${")) {
            log.error("### ERRO FATAL: Número de telefone Twilio não configurado ou inválido nas variáveis de ambiente! Use formato E.164 (+...) ###");
            isInitialized = false;
            return;
        }

        try {
            Twilio.init(accountSid, authToken);
            isInitialized = true;
            // NÃO logue o authToken! Logue apenas parte do SID para confirmação.
            log.info("Twilio Service inicializado com sucesso. Account SID: {}..., Número Twilio: {}",
                    accountSid.substring(0, 6), twilioPhoneNumber);
        } catch (Exception e) {
            log.error("### Falha ao inicializar o Twilio SDK: {} ###", e.getMessage(), e);
            isInitialized = false;
        }
    }

    public void sendSms(String toPhoneNumber, String messageBody) {
        if (!isInitialized) {
            log.error("Tentativa de enviar SMS falhou: Twilio Service não está inicializado corretamente. Verifique as credenciais.");
            // Considerar lançar uma exceção aqui para que a camada de serviço possa tratar
            // throw new IllegalStateException("Twilio Service não inicializado.");
            return; // Não tenta enviar
        }

        // Validação básica do número de destino
        if (toPhoneNumber == null || !toPhoneNumber.startsWith("+")) {
            log.error("Formato inválido para o número de destino do SMS: '{}'. Deve iniciar com '+'.", toPhoneNumber);
            return;
        }
        // Validação básica do número de origem
        if (twilioPhoneNumber == null || !twilioPhoneNumber.startsWith("+")) {
            log.error("Número de telefone Twilio (remetente) inválido: '{}'. Verifique application.properties/variáveis de ambiente.", twilioPhoneNumber);
            return;
        }


        try {
            PhoneNumber to = new PhoneNumber(toPhoneNumber);
            PhoneNumber from = new PhoneNumber(twilioPhoneNumber);

            Message message = Message.creator(to, from, messageBody).create();

            log.info("SMS para {} solicitado com sucesso. Message SID: {}", toPhoneNumber, message.getSid());

        } catch (Exception e) { // Captura exceções da API Twilio (ex: número inválido, saldo insuficiente)
            log.error("Falha ao enviar SMS via Twilio para {}: {}", toPhoneNumber, e.getMessage());
            // Não relançar a exceção por padrão, apenas logar, para não impedir outras operações.
            // Se a falha do SMS for crítica, pode-se lançar uma exceção personalizada.
            // throw new RuntimeException("Falha no envio de SMS para " + toPhoneNumber, e);
        }
    }
}