package com.suaempresa.driverqueue.service;

import com.suaempresa.driverqueue.config.TwilioProperties;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Serviço responsável pela interação com a API do Twilio para envio de SMS.
 * Utiliza as propriedades configuradas em {@link TwilioProperties}.
 */
@Service
public class TwilioService {

    private static final Logger log = LoggerFactory.getLogger(TwilioService.class);

    private final TwilioProperties twilioProperties;
    private boolean isInitialized = false;

    /**
     * Construtor que injeta as propriedades de configuração do Twilio.
     * @param twilioProperties Objeto contendo as credenciais (SID, Token) e número de telefone do Twilio.
     */
    public TwilioService(TwilioProperties twilioProperties) {
        this.twilioProperties = twilioProperties;
    }

    /**
     * Inicializa o SDK do Twilio com as credenciais fornecidas após a injeção das propriedades.
     * Marca o serviço como inicializado ou loga um erro fatal se as credenciais forem inválidas.
     */
    @PostConstruct
    public void initTwilio() {
        String sid = twilioProperties.getAccountSid();
        String token = twilioProperties.getAuthToken();

        if (sid == null || token == null) {
            log.error("### ERRO FATAL: Falha ao obter propriedades Twilio injetadas (SID ou Token nulos). Verifique as variáveis de ambiente e a configuração. ###");
            isInitialized = false;
            // Considerar lançar uma exceção aqui para impedir a inicialização da aplicação
            // throw new IllegalStateException("Credenciais Twilio ausentes.");
            return;
        }

        try {
            Twilio.init(sid, token);
            isInitialized = true;
            log.info("Twilio Service inicializado com sucesso. Account SID: {}..., Número Twilio: {}",
                    sid.substring(0, 6), twilioProperties.getPhoneNumber());
        } catch (Exception e) {
            log.error("### Falha ao inicializar o Twilio SDK: {} ###", e.getMessage(), e);
            isInitialized = false;
            // Considerar relançar a exceção
            // throw new RuntimeException("Falha na inicialização do Twilio SDK", e);
        }
    }

    /**
     * Envia uma mensagem SMS para o número de telefone de destino especificado.
     * Utiliza o número de telefone remetente configurado nas propriedades.
     * Loga erros se o serviço não estiver inicializado ou se a API do Twilio retornar um erro.
     *
     * @param toPhoneNumber O número de telefone do destinatário (formato E.164, ex: +55...).
     * @param messageBody O conteúdo da mensagem SMS a ser enviada.
     */
    public void sendSms(String toPhoneNumber, String messageBody) {
        if (!isInitialized) {
            log.error("sendSms: Tentativa de enviar SMS falhou: Twilio Service não está inicializado corretamente.");
            // Não enviar se não inicializado
            return;
        }

        if (toPhoneNumber == null || !toPhoneNumber.startsWith("+")) {
            log.error("sendSms: Formato inválido para o número de destino do SMS: '{}'.", toPhoneNumber);
            return;
        }
        String fromNumber = twilioProperties.getPhoneNumber();
        if (fromNumber == null || !fromNumber.startsWith("+")) {
            log.error("sendSms: Número de telefone Twilio (remetente) inválido nas propriedades: '{}'.", fromNumber);
            return;
        }

        try {
            PhoneNumber to = new PhoneNumber(toPhoneNumber);
            PhoneNumber from = new PhoneNumber(fromNumber);

            // Cria e envia a mensagem via API Twilio
            Message message = Message.creator(to, from, messageBody).create();

            log.info("sendSms: SMS para {} solicitado com sucesso. Message SID: {}", toPhoneNumber, message.getSid());

        } catch (Exception e) { // Captura exceções da API Twilio
            // Log detalhado do erro, mas não impede o fluxo principal da aplicação que chamou este método.
            log.error("sendSms: Falha ao enviar SMS via Twilio para {}: {}", toPhoneNumber, e.getMessage());
        }
    }
}