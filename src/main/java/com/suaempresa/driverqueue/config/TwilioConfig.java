package com.suaempresa.driverqueue.config;

import com.twilio.Twilio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct; // Para executar após a inicialização

@Configuration // Indica que esta classe contém configurações para o Spring
public class TwilioConfig {

    private static final Logger log = LoggerFactory.getLogger(TwilioConfig.class);

    // Injeta os valores das propriedades do application.properties
    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    // Método que executa automaticamente após o Spring criar esta classe
    @PostConstruct
    public void initTwilio() {
        try {
            log.info("Inicializando Twilio com Account SID: {}", accountSid);
            // Inicializa a biblioteca Twilio globalmente com suas credenciais
            Twilio.init(accountSid, authToken);
            log.info("Twilio inicializado com sucesso.");
        } catch (Exception e) {
            log.error("Falha ao inicializar Twilio! Verifique as credenciais.", e);
            // Você pode querer lançar uma exceção aqui para impedir o boot se o Twilio for essencial
        }
    }

    // Poderia adicionar getters para as credenciais se outros serviços precisassem delas
    // public String getAccountSid() { return accountSid; }
}