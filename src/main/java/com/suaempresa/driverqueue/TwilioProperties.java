package com.suaempresa.driverqueue.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Classe que mapeia e valida as propriedades de configuração do Twilio
 * definidas no arquivo {@code application.properties} (com prefixo "twilio").
 * Os valores são lidos preferencialmente de variáveis de ambiente.
 * <p>
 * Para usar esta classe, a aplicação principal deve ser anotada com {@code @ConfigurationPropertiesScan}.
 *
 * @see com.suaempresa.driverqueue.service.TwilioService onde estas propriedades são injetadas.
 */
@ConfigurationProperties(prefix = "twilio")
@Validated // Habilita a validação dos campos anotados (@NotBlank)
public class TwilioProperties {

    /**
     * Seu Account SID exclusivo do Twilio (obtido do painel Twilio).
     * Essencial para autenticar com a API Twilio.
     * Deve ser fornecido via variável de ambiente {@code TWILIO_ACCOUNT_SID}.
     */
    @NotBlank(message = "Twilio Account SID (twilio.account-sid) não pode ser vazio")
    private String accountSid;

    /**
     * Seu Auth Token secreto do Twilio (obtido do painel Twilio).
     * Usado junto com o Account SID para autenticação. Trate como senha.
     * Deve ser fornecido via variável de ambiente {@code TWILIO_AUTH_TOKEN}.
     */
    @NotBlank(message = "Twilio Auth Token (twilio.auth-token) não pode ser vazio")
    private String authToken;

    /**
     * O número de telefone Twilio ativo (comprado ou verificado na sua conta Twilio)
     * que será usado como remetente das mensagens SMS.
     * Deve estar no formato E.164 (ex: +15551234567).
     * Deve ser fornecido via variável de ambiente {@code TWILIO_PHONE_NUMBER}.
     */
    @NotBlank(message = "Twilio Phone Number (twilio.phone-number) não pode ser vazio")
    @Pattern(regexp = "^\\+[1-9]\\d{10,14}$", message = "Formato inválido para twilio.phone-number (Ex: +1...).")
    private String phoneNumber;

    // --- Getters e Setters ---
    // Necessários para que o Spring Boot possa injetar os valores das propriedades.

    public String getAccountSid() { return accountSid; }
    public void setAccountSid(String accountSid) { this.accountSid = accountSid; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}