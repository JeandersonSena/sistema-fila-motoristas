package com.suaempresa.driverqueue; // Certifique-se que o pacote está correto

import com.zaxxer.hikari.HikariDataSource; // Pool de conexões eficiente
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary; // Para garantir que este DataSource seja o principal

import javax.sql.DataSource;

@Configuration // Marca esta classe para configuração de Beans do Spring
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    // Injeta os valores das variáveis de ambiente fornecidas pelo Railway
    // Se alguma variável não for encontrada, a aplicação falhará ao iniciar (o que é bom neste caso)
    @Value("${PGHOST}")
    private String dbHost;

    @Value("${PGPORT}")
    private String dbPort; // Pode ser String ou int, String é mais seguro aqui

    @Value("${PGDATABASE}")
    private String dbName;

    @Value("${PGUSER}")
    private String dbUsername;

    @Value("${PGPASSWORD}")
    private String dbPassword;

    @Bean // Define um método que cria um Bean gerenciado pelo Spring
    @Primary // Marca este DataSource como o principal, caso haja outros
    public DataSource dataSource() {
        // Log para verificar se as variáveis foram injetadas (NÃO LOGAR SENHA EM PRODUÇÃO REAL)
        log.info("--- Configurando DataSource Programaticamente ---");
        log.info("Host Injetado: {}", dbHost);
        log.info("Porta Injetada: {}", dbPort);
        log.info("Banco Injetado: {}", dbName);
        log.info("Usuário Injetado: {}", dbUsername);
        // log.info("Senha Injetada: [NÃO MOSTRAR]"); // Comentado por segurança

        // Verifica se as variáveis foram injetadas (básico)
        if (dbHost == null || dbHost.isEmpty() || dbPort == null || dbPort.isEmpty() ||
                dbName == null || dbName.isEmpty() || dbUsername == null || dbUsername.isEmpty() ||
                dbPassword == null || dbPassword.isEmpty()) {
            log.error("!!! Variáveis de ambiente do banco de dados (PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD) não foram injetadas corretamente! Verifique a configuração do Railway. !!!");
            // Pode lançar uma exceção para impedir a inicialização
            throw new IllegalStateException("Variáveis de ambiente do banco de dados não configuradas.");
        }


        // Constrói a URL JDBC manualmente
        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", dbHost, dbPort, dbName);
        log.info("Construindo DataSource com JDBC URL: {}", jdbcUrl);

        // Configura o HikariDataSource (pool de conexões)
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(dbUsername);
        dataSource.setPassword(dbPassword);
        dataSource.setDriverClassName("org.postgresql.Driver"); // Define explicitamente

        // Configurações opcionais do Pool HikariCP
        dataSource.setMaximumPoolSize(10); // Ajuste conforme necessidade
        dataSource.setMinimumIdle(2);
        dataSource.setPoolName("DriverQueueHikariPool");

        log.info("--- DataSource HikariCP configurado com sucesso. ---");
        return dataSource;
    }
}