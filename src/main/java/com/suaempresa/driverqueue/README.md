# Sistema de Fila de Motoristas com Notificação SMS



Aplicação web Java Spring Boot para gerenciar uma fila de entrada de motoristas, permitindo o registro via formulário web e a notificação do próximo motorista da fila via SMS (utilizando Twilio). Inclui uma visão administrativa para gerenciar a fila.

## Funcionalidades Principais

*   **Motorista:**
    *   Registrar-se na fila informando Placa, Nome e Telefone.
    *   Receber notificação via SMS quando for chamado.
*   **Administrador:**
    *   Visualizar a fila de motoristas aguardando (status "WAITING") em tempo real (via polling).
    *   Chamar o próximo motorista da fila (FIFO).
    *   Limpar todos os motoristas da fila de espera.

## Tecnologias Utilizadas

*   **Backend:**
    *   Java 17+ 
    *   Spring Boot 3.2.4+
    *   Spring Web (MVC)
    *   Spring Data JPA (com Hibernate)
    *   Spring Boot Validation
    *   Thymeleaf (Template Engine para HTML)
    *   Maven (Gerenciador de dependências e build)
*   **Banco de Dados:**
    *   PostgreSQL (para produção e recomendado para desenvolvimento local)
    *   Flyway (Gerenciamento de migrações de schema)
*   **Notificação:**
    *   Twilio SMS API
*   **Frontend (Básico):**
    *   HTML5
    *   CSS3 (Puro)
    *   JavaScript (Vanilla JS para polling na tela de admin)
*   **Testes:**
    *   JUnit 5 (Jupiter)
    *   Mockito
    *   AssertJ

## Pré-requisitos (para rodar localmente)

*   **JDK 17 ou superior:** Verifique com `java -version`.
*   **Maven 3.6+:** Verifique com `mvn -version`.
*   **PostgreSQL:** Servidor PostgreSQL instalado e rodando localmente.
*   **Conta Twilio:** Uma conta Twilio ativa com:
    *   Account SID
    *   Auth Token
    *   Um número de telefone Twilio ativo (com capacidade de enviar SMS para a região desejada).
    *   *Opcional (Conta Trial):* Números de telefone de destino verificados na conta Twilio.
*   **IDE:** IntelliJ IDEA (recomendado) ou outra IDE Java compatível com Maven.
*   **Git:** Para clonar o repositório.

## Configuração Local

1.  **Banco de Dados:**
    *   Crie um banco de dados PostgreSQL (ex: `fila_motoristas_db`).
    *   Crie um usuário/role para a aplicação (ex: `fila_user`) com uma **senha segura**.
    *   Conceda privilégios ao usuário no banco de dados: `GRANT ALL PRIVILEGES ON DATABASE fila_motoristas_db TO fila_user;`
    *   Conceda privilégios no schema public: Conecte-se ao banco (`\c fila_motoristas_db`) e execute `GRANT ALL ON SCHEMA public TO fila_user;`
2.  **Variáveis de Ambiente (IntelliJ):**
    *   Vá em `Run -> Edit Configurations...`.
    *   Selecione sua aplicação Spring Boot.
    *   Na seção "Environment variables", adicione as seguintes variáveis, **substituindo os valores de exemplo pelos seus dados reais**:
        *   `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fila_motoristas_db` *(Ajuste host, porta, nome do banco se necessário)*
        *   `SPRING_DATASOURCE_USERNAME=fila_user` *(Seu usuário do banco)*
        *   `SPRING_DATASOURCE_PASSWORD=sua_senha_segura_aqui` *(Sua senha do banco)*
        *   `SERVER_PORT=8080`
        *   `TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxx` *(Seu SID real)*
        *   `TWILIO_AUTH_TOKEN=yyyyyyyyyyyyyyy` *(Seu Token real)*
        *   `TWILIO_PHONE_NUMBER=+15551234567` *(Seu número Twilio real)*

## Como Rodar Localmente

1.  **Clone o Repositório:**
    ```bash
    git clone https://github.com/JeandersonSena/sistema-fila-motoristas.git
    cd sistema-fila-motoristas
    ```
2.  **Compile e Empacote (Opcional, se for rodar via IDE):**
    ```bash
    ./mvnw clean package -DskipTests
    ```
3.  **Execute via IntelliJ IDEA:**
    *   Importe o projeto como um projeto Maven no IntelliJ.
    *   Configure as variáveis de ambiente conforme a seção "Configuração Local".
    *   Encontre a classe `DriverQueueSmsAppApplication.java`.
    *   Clique com o botão direito -> Run 'DriverQueueSmsAppApplication'.
4.  **Execute via Linha de Comando (Alternativa):**
    *   Certifique-se de que as variáveis de ambiente (`SPRING_DATASOURCE_*`, `TWILIO_*`, `SERVER_PORT`) estão **exportadas** no seu terminal antes de rodar. (Exemplo no Bash: `export SPRING_DATASOURCE_PASSWORD='sua_senha_segura_aqui'`).
    *   Execute o JAR criado no passo 2:
        ```bash
        java -jar target/driver-queue-sms-app-*.jar 
        # O nome exato do JAR pode variar ligeiramente com a versão
        ```
5.  **Acesse a Aplicação:** Abra o navegador em `http://localhost:8080/`.

## Como Rodar os Testes

Execute o seguinte comando na raiz do projeto:

```bash
./mvnw test