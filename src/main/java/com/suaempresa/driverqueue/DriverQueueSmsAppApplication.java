package com.suaempresa.driverqueue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Classe principal da aplicação Spring Boot.
 * Ponto de entrada para iniciar a aplicação web.
 * A anotação {@code @SpringBootApplication} combina {@code @Configuration},
 * {@code @EnableAutoConfiguration} e {@code @ComponentScan}.
 * A anotação {@code @ConfigurationPropertiesScan} habilita a detecção de classes
 * anotadas com {@code @ConfigurationProperties} (como {@link com.suaempresa.driverqueue.config.TwilioProperties}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan // Habilita o scan por @ConfigurationProperties
public class DriverQueueSmsAppApplication {

    /**
     * Método main padrão para aplicações Spring Boot.
     * Delega a inicialização e execução para {@link SpringApplication#run(Class, String[])}.
     * @param args Argumentos de linha de comando (não utilizados atualmente).
     */
    public static void main(String[] args) {
        SpringApplication.run(DriverQueueSmsAppApplication.class, args);
    }

}