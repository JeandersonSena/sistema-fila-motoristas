package com.suaempresa.driverqueue.service;

import com.suaempresa.driverqueue.config.TwilioProperties; // Necessário se usado no serviço
import com.suaempresa.driverqueue.model.Driver;
import com.suaempresa.driverqueue.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach; // Para setup antes de cada teste
import org.junit.jupiter.api.DisplayName; // Nome descritivo para o teste
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor; // Para capturar argumentos passados aos mocks
import org.mockito.InjectMocks; // Para injetar mocks na classe sob teste
import org.mockito.Mock; // Para criar mocks das dependências
import org.mockito.junit.jupiter.MockitoExtension; // Para habilitar Mockito com JUnit 5

import java.time.LocalDateTime;
import java.util.Collections; // Para listas vazias
import java.util.List;
import java.util.Optional;

// Import estático para asserções (AssertJ é incluído pelo Spring Boot Test)
import static org.assertj.core.api.Assertions.*;
// Import estático para métodos do Mockito
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Habilita Mockito
class DriverServiceTest {

    // Cria um mock (objeto simulado) do DriverRepository
    @Mock
    private DriverRepository driverRepository;

    // Cria um mock do TwilioService
    @Mock
    private TwilioService twilioService;

    // Cria uma instância REAL do DriverService e INJETA os mocks acima (@Mock)
    // nos campos correspondentes (via construtor)
    @InjectMocks
    private DriverService driverService;

    // --- Dados de Teste Reutilizáveis (Opcional) ---
    private Driver validDriver;
    private Driver waitingDriver1;
    private Driver waitingDriver2;

    @BeforeEach // Método que roda ANTES de cada @Test
    void setUp() {
        // Podemos inicializar objetos de teste comuns aqui
        validDriver = new Driver();
        validDriver.setId(1L);
        validDriver.setName("Teste Unitario");
        validDriver.setPlate("ABC-9999");
        validDriver.setPhoneNumber("+5511999990000");
        validDriver.setStatus(Driver.DriverStatus.WAITING);
        validDriver.setEntryTime(LocalDateTime.now());

        waitingDriver1 = new Driver();
        waitingDriver1.setId(10L);
        waitingDriver1.setName("Motorista Um");
        waitingDriver1.setPlate("AAA-1111");
        waitingDriver1.setPhoneNumber("+5511911111111");
        waitingDriver1.setStatus(Driver.DriverStatus.WAITING);
        waitingDriver1.setEntryTime(LocalDateTime.now().minusMinutes(5)); // Entrou há 5 min

        waitingDriver2 = new Driver();
        waitingDriver2.setId(20L);
        waitingDriver2.setName("Motorista Dois");
        waitingDriver2.setPlate("BBB-2222");
        waitingDriver2.setPhoneNumber("+5511922222222");
        waitingDriver2.setStatus(Driver.DriverStatus.WAITING);
        waitingDriver2.setEntryTime(LocalDateTime.now().minusMinutes(2)); // Entrou há 2 min
    }

    // --- Testes para o método addDriver ---

    @Test
    @DisplayName("Deve adicionar motorista com sucesso quando dados são válidos")
    void addDriver_whenValidInput_shouldSaveAndReturnDriver() {
        // Arrange (Organização)
        String plate = "NEW-1234";
        String name = "Novo Motorista";
        String phone = "+559912345678";

        // Configura o mock do repositório:
        // Quando o método save for chamado com QUALQUER objeto Driver...
        when(driverRepository.save(any(Driver.class)))
                // ...então retorne um objeto Driver com ID e os dados passados (simulando o save)
                .thenAnswer(invocation -> {
                    Driver driverToSave = invocation.getArgument(0); // Pega o driver passado para save()
                    driverToSave.setId(55L); // Simula o banco gerando um ID
                    // Os outros dados (plate, name, phone, status, entryTime) já foram definidos pelo addDriver
                    return driverToSave;
                });

        // Act (Ação)
        Driver result = driverService.addDriver(plate, name, phone);

        // Assert (Verificação)
        assertThat(result).isNotNull(); // Verifica se não retornou nulo
        assertThat(result.getId()).isEqualTo(55L); // Verifica se o ID foi definido
        assertThat(result.getPlate()).isEqualTo(plate); // Verifica dados
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getPhoneNumber()).isEqualTo(phone);
        assertThat(result.getStatus()).isEqualTo(Driver.DriverStatus.WAITING); // Verifica status inicial
        assertThat(result.getEntryTime()).isNotNull(); // Verifica se a data de entrada foi definida

        // Verifica se o método save do repositório foi chamado exatamente 1 vez
        verify(driverRepository, times(1)).save(any(Driver.class));
    }

    @Test
    @DisplayName("Deve lançar IllegalArgumentException ao adicionar com placa inválida")
    void addDriver_whenInvalidPlate_shouldThrowException() {
        // Arrange
        String invalidPlate = " "; // Placa em branco
        String name = "Nome Valido";
        String phone = "+5511999998888";

        // Act & Assert
        // Verifica se a chamada ao método lança a exceção esperada
        assertThatThrownBy(() -> driverService.addDriver(invalidPlate, name, phone))
                .isInstanceOf(IllegalArgumentException.class) // Verifica o tipo da exceção
                .hasMessageContaining("Placa"); // Verifica parte da mensagem de erro

        // Garante que o método save NUNCA foi chamado
        verify(driverRepository, never()).save(any(Driver.class));
    }

    // TODO: Adicionar testes para nome inválido, telefone inválido, telefone com formato errado...
    // (Segue a mesma lógica do teste de placa inválida)

    // --- Testes para o método getAdminQueueView ---

    @Test
    @DisplayName("Deve retornar lista de motoristas esperando")
    void getAdminQueueView_whenDriversWaiting_shouldReturnList() {
        // Arrange
        List<Driver> waitingList = List.of(waitingDriver1, waitingDriver2);
        // Configura o mock: quando buscar por WAITING, retorna a lista criada
        when(driverRepository.findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING))
                .thenReturn(waitingList);

        // Act
        List<Driver> result = driverService.getAdminQueueView();

        // Assert
        assertThat(result)
                .isNotNull()
                .hasSize(2) // Verifica o tamanho da lista
                .containsExactly(waitingDriver1, waitingDriver2); // Verifica o conteúdo e a ordem

        verify(driverRepository, times(1)).findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando nenhum motorista esperando")
    void getAdminQueueView_whenNoDriversWaiting_shouldReturnEmptyList() {
        // Arrange
        // Configura o mock: retorna uma lista vazia
        when(driverRepository.findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING))
                .thenReturn(Collections.emptyList());

        // Act
        List<Driver> result = driverService.getAdminQueueView();

        // Assert
        assertThat(result).isNotNull().isEmpty(); // Verifica se a lista está vazia

        verify(driverRepository, times(1)).findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);
    }


    // --- Testes para o método callNextDriver ---

    @Test
    @DisplayName("Deve chamar próximo motorista e enviar SMS quando há motorista com telefone")
    void callNextDriver_whenDriverExistsWithPhone_shouldUpdateStatusAndSendSms() {
        // Arrange
        // Configura mock para encontrar o waitingDriver1 como próximo
        when(driverRepository.findFirstByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING))
                .thenReturn(Optional.of(waitingDriver1));
        // Configura mock do Twilio para não fazer nada quando sendSms for chamado
        doNothing().when(twilioService).sendSms(anyString(), anyString());

        // Act
        Optional<Driver> resultOpt = driverService.callNextDriver();

        // Assert
        assertThat(resultOpt).isPresent(); // Verifica se encontrou um motorista
        Driver resultDriver = resultOpt.get();
        assertThat(resultDriver.getId()).isEqualTo(waitingDriver1.getId());

        // Captura o objeto Driver que foi passado para o método save()
        ArgumentCaptor<Driver> driverCaptor = ArgumentCaptor.forClass(Driver.class);
        verify(driverRepository, times(1)).save(driverCaptor.capture());
        Driver savedDriver = driverCaptor.getValue();

        assertThat(savedDriver.getStatus()).isEqualTo(Driver.DriverStatus.CALLED); // Verifica se o status foi mudado
        assertThat(savedDriver.getCalledTime()).isNotNull(); // Verifica se o horário da chamada foi definido

        // Verifica se o TwilioService foi chamado 1 vez com os parâmetros corretos (ou parte deles)
        ArgumentCaptor<String> phoneCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(twilioService, times(1)).sendSms(phoneCaptor.capture(), messageCaptor.capture());

        assertThat(phoneCaptor.getValue()).isEqualTo(waitingDriver1.getPhoneNumber());
        assertThat(messageCaptor.getValue()).contains(waitingDriver1.getName()); // Verifica se a mensagem contém o nome

        verify(driverRepository, times(1)).findFirstByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);

    }


    @Test
    @DisplayName("Deve chamar próximo motorista mas NÃO enviar SMS quando telefone ausente")
    void callNextDriver_whenDriverExistsWithoutPhone_shouldUpdateStatusOnly() {
        // Arrange
        waitingDriver1.setPhoneNumber(null); // Simula motorista sem telefone
        when(driverRepository.findFirstByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING))
                .thenReturn(Optional.of(waitingDriver1));

        // Act
        Optional<Driver> resultOpt = driverService.callNextDriver();

        // Assert
        assertThat(resultOpt).isPresent();

        ArgumentCaptor<Driver> driverCaptor = ArgumentCaptor.forClass(Driver.class);
        verify(driverRepository, times(1)).save(driverCaptor.capture());
        Driver savedDriver = driverCaptor.getValue();

        assertThat(savedDriver.getStatus()).isEqualTo(Driver.DriverStatus.CALLED);
        assertThat(savedDriver.getCalledTime()).isNotNull();

        // VERIFICA QUE O SMS NÃO FOI ENVIADO!
        verify(twilioService, never()).sendSms(anyString(), anyString());
    }

    @Test
    @DisplayName("Deve retornar Optional vazio quando não há motorista na fila")
    void callNextDriver_whenQueueIsEmpty_shouldReturnEmptyOptional() {
        // Arrange
        when(driverRepository.findFirstByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING))
                .thenReturn(Optional.empty()); // Simula fila vazia

        // Act
        Optional<Driver> resultOpt = driverService.callNextDriver();

        // Assert
        assertThat(resultOpt).isEmpty(); // Verifica que o Optional está vazio

        // Garante que save e sendSms não foram chamados
        verify(driverRepository, never()).save(any(Driver.class));
        verify(twilioService, never()).sendSms(anyString(), anyString());
    }

    // TODO: Adicionar teste para quando twilioService.sendSms lança uma exceção
    // (deve logar o erro mas ainda retornar o Optional com o motorista chamado)

    // --- Testes para o método clearWaitingList ---

    @Test
    @DisplayName("Deve limpar a fila e retornar contagem quando há motoristas esperando")
    void clearWaitingList_whenDriversWaiting_shouldUpdateStatusAndReturnCount() {
        // Arrange
        List<Driver> waitingList = List.of(waitingDriver1, waitingDriver2);
        when(driverRepository.findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING))
                .thenReturn(waitingList);

        // Act
        int clearedCount = driverService.clearWaitingList();

        // Assert
        assertThat(clearedCount).isEqualTo(2); // Verifica a contagem retornada

        // Captura a lista de motoristas passada para saveAll
        ArgumentCaptor<List<Driver>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(driverRepository, times(1)).saveAll(listCaptor.capture());
        List<Driver> savedList = listCaptor.getValue();

        // Verifica se todos na lista capturada têm status CLEARED
        assertThat(savedList).hasSize(2);
        assertThat(savedList).allMatch(driver -> driver.getStatus() == Driver.DriverStatus.CLEARED);

        verify(driverRepository, times(1)).findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING);
    }

    @Test
    @DisplayName("Deve retornar 0 quando não há motoristas para limpar")
    void clearWaitingList_whenNoDriversWaiting_shouldReturnZero() {
        // Arrange
        when(driverRepository.findByStatusOrderByEntryTimeAsc(Driver.DriverStatus.WAITING))
                .thenReturn(Collections.emptyList()); // Fila vazia

        // Act
        int clearedCount = driverService.clearWaitingList();

        // Assert
        assertThat(clearedCount).isEqualTo(0); // Contagem deve ser 0

        // Garante que saveAll não foi chamado
        verify(driverRepository, never()).saveAll(anyList());
    }
}