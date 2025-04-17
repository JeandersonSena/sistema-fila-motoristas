// Executa o código quando o HTML estiver completamente carregado
document.addEventListener('DOMContentLoaded', () => {
    console.log("Admin.js loaded and running!"); // Confirma que o script carregou

    // === Seletores de Elementos ===
    const tableBody = document.querySelector('#drivers-table tbody');
    const noDriversRow = document.getElementById('no-drivers-row');
    const errorLoadingRow = document.getElementById('error-loading-row');
    const loadingIndicator = document.getElementById('loading-indicator');
    const adminFeedbackDiv = document.getElementById('admin-feedback-js');

    const callNextButton = document.getElementById('call-next-button');
    const recallButton = document.getElementById('recall-button');
    const clearListButton = document.getElementById('clear-list-button');

    // === Estado ===
    let lastCalledDriverId = null; // Guarda o ID do último motorista chamado com sucesso
    const POLLING_INTERVAL_MS = 5000; // Atualizar a cada 5 segundos

    // === Funções Auxiliares ===

    /**
     * Exibe uma mensagem de feedback para o administrador.
     * @param {string} message A mensagem a ser exibida.
     * @param {boolean} isError Define se a mensagem é de erro (true) ou sucesso (false).
     */
    const showAdminFeedback = (message, isError = false) => {
        if (!adminFeedbackDiv) return; // Segurança se o elemento não existir

        adminFeedbackDiv.textContent = message;
        adminFeedbackDiv.className = 'message'; // Reseta classes
        if (isError) {
            adminFeedbackDiv.classList.add('error');
        } else {
            adminFeedbackDiv.classList.add('success');
        }
        adminFeedbackDiv.style.display = 'block';

        // Opcional: Esconder a mensagem após alguns segundos
        setTimeout(() => {
            adminFeedbackDiv.style.display = 'none';
            adminFeedbackDiv.textContent = '';
            adminFeedbackDiv.className = 'message';
        }, 5000); // Esconde após 5 segundos
    };

    /**
     * Formata um timestamp ISO (vindo do Java LocalDateTime) para Hora:Minuto:Segundo.
     * Retorna '-' se o timestamp for inválido.
     * @param {string | null} isoTimestamp String do timestamp ou null.
     * @returns {string} Hora formatada ou '-'.
     */
    const formatTime = (isoTimestamp) => {
        if (!isoTimestamp) return '-';
        try {
            return new Date(isoTimestamp).toLocaleTimeString([], {
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        } catch (e) {
            console.error("Error formatting time:", isoTimestamp, e);
            return '-';
        }
    };

    // === Lógica Principal de Atualização ===

    /**
     * Busca os dados da API e atualiza a tabela de motoristas na interface.
     */
    const fetchAndUpdateDrivers = async () => {
        console.log("Fetching drivers...");
        if(loadingIndicator) loadingIndicator.style.display = 'block'; // Mostra carregando
        if(errorLoadingRow) errorLoadingRow.style.display = 'none'; // Esconde erro prévio
        if(noDriversRow) noDriversRow.style.display = 'none'; // Esconde msg de vazio prévia

        try {
            const response = await fetch('/api/drivers/waiting');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const drivers = await response.json();

            // --- ADICIONE ESTA LINHA ---
            console.log("Dados recebidos da API:", JSON.stringify(drivers)); // Mostra exatamente o que foi recebido

            // Limpa o corpo da tabela atual
            tableBody.innerHTML = '';

            if (!drivers || drivers.length === 0) {
                console.log("JS interpretou: Fila vazia."); // Confirma que entrou neste bloco
                if(noDriversRow) noDriversRow.style.display = '';
                tableBody.appendChild(noDriversRow);
            } else {
                console.log(`JS interpretou: ${drivers.length} motoristas encontrados.`); // Confirma que entrou neste bloco
                if(noDriversRow) noDriversRow.style.display = 'none'; // Esconde a linha de vazio

                drivers.forEach((driver, index) => {
                    const row = tableBody.insertRow();

                    // Aplicar classe CSS com base no status
                    row.classList.add(`status-${driver.status ? driver.status.toLowerCase() : 'unknown'}`);

                    // Colunas (ajuste a ordem e o conteúdo conforme o HTML)
                    row.insertCell(0).textContent = formatTime(driver.entryTimestamp);
                    row.insertCell(1).textContent = driver.licensePlate || '-';
                    row.insertCell(2).textContent = driver.driverName || '-';
                    row.insertCell(3).textContent = driver.status || 'DESCONHECIDO';

                    // Coluna de Ações
                    const actionCell = row.insertCell(4);
                    if (driver.status === 'WAITING') {
                        const callButton = document.createElement('button');
                        callButton.textContent = 'Chamar';
                        callButton.classList.add('call-individual-button'); // Classe para identificar
                        callButton.dataset.driverId = driver.id; // Guarda o ID no botão
                        actionCell.appendChild(callButton);
                    } else {
                        actionCell.textContent = '-'; // Sem ações para não-esperando
                    }
                });
            }

        } catch (error) {
            console.error('Error fetching or updating drivers:', error);
            if(errorLoadingRow) errorLoadingRow.style.display = ''; // Mostra linha de erro
            tableBody.innerHTML = ''; // Limpa qualquer conteúdo anterior
            tableBody.appendChild(errorLoadingRow); // Adiciona a linha de erro
            showAdminFeedback('Erro ao buscar dados da fila. Tente novamente.', true);
        } finally {
            if(loadingIndicator) loadingIndicator.style.display = 'none'; // Esconde carregando
        }
    };

    // === Funções de Manipulação de Eventos (Handlers) ===

    /**
     * Chama um motorista específico pela API.
     * @param {string | number} driverId O ID do motorista a ser chamado.
     */
    const handleCallIndividual = async (driverId) => {
        if (!driverId) {
            console.warn("Tentativa de chamar motorista sem ID.");
            return;
        }
        console.log(`Calling driver ${driverId}`);
        showAdminFeedback(`Chamando motorista ID ${driverId}...`); // Feedback inicial

        try {
            const response = await fetch(`/api/drivers/${driverId}/call`, {
                method: 'POST',
                headers: {
                    // Incluir headers CSRF se o Spring Security estiver ativo
                }
            });

            if (response.ok) {
                console.log(`Successfully called driver ${driverId}`);
                lastCalledDriverId = driverId; // Guarda o ID para o botão "Recall"
                if (recallButton) recallButton.disabled = false; // Habilita o recall
                showAdminFeedback(`Motorista ID ${driverId} chamado com sucesso.`);
                fetchAndUpdateDrivers(); // Atualiza a lista imediatamente
            } else {
                console.error(`Failed to call driver ${driverId}: ${response.status} ${response.statusText}`);
                const errorText = await response.text(); // Tenta pegar mais detalhes do erro
                showAdminFeedback(`Falha ao chamar motorista ID ${driverId}. ${response.statusText} ${errorText ? `(${errorText})` : ''}`, true);
            }
        } catch (error) {
            console.error('Error calling driver:', error);
            showAdminFeedback(`Erro de rede ao tentar chamar motorista ID ${driverId}.`, true);
        }
    };

    /**
     * Encontra o primeiro motorista na fila (na tabela) e o chama.
     */
    const handleCallNext = () => {
        console.log("Call Next button clicked");
        // Encontra o primeiro botão "Chamar" individual visível na tabela
        const firstCallButton = tableBody.querySelector('.call-individual-button');

        if (firstCallButton && firstCallButton.dataset.driverId) {
            const driverIdToCall = firstCallButton.dataset.driverId;
            handleCallIndividual(driverIdToCall);
        } else {
            console.log("No waiting drivers found to call next.");
            showAdminFeedback("Nenhum motorista na fila para chamar.");
        }
    };

    /**
     * Chama novamente o último motorista que foi chamado com sucesso.
     */
    const handleRecall = () => {
        console.log("Recall button clicked");
        if (lastCalledDriverId) {
            console.log(`Recalling driver ${lastCalledDriverId}`);
            showAdminFeedback(`Re-chamando motorista ID ${lastCalledDriverId}...`);
            handleCallIndividual(lastCalledDriverId); // Reutiliza a função de chamar
        } else {
            console.log("No driver has been called yet to recall.");
            showAdminFeedback("Nenhum motorista chamado recentemente para chamar novamente.");
            if(recallButton) recallButton.disabled = true; // Garante que está desabilitado
        }
    };

    /**
     * Limpa a lista de espera via API após confirmação.
     */
    const handleClearList = async () => {
        console.log("Clear List button clicked");
        // Mensagem de confirmação que estava no HTML
        if (!confirm('ATENÇÃO! Isso marcará todos os motoristas em espera como "CLEARED". Deseja continuar?')) {
            console.log("Clear list cancelled by user.");
            return; // Para se o usuário clicar em Cancelar
        }

        console.log('Clearing waiting list via API...');
        showAdminFeedback('Limpando fila de espera...');

        try {
            const response = await fetch('/api/drivers/clear', {
                method: 'POST',
                 headers: {
                    // Incluir headers CSRF se necessário
                 }
            });

            if (response.ok) {
                const message = await response.text(); // Pega a msg de sucesso da API
                console.log(`Successfully cleared list: ${message}`);
                showAdminFeedback(message || 'Fila de espera limpa com sucesso.');
                lastCalledDriverId = null; // Reseta o último chamado, pois a fila foi limpa
                if (recallButton) recallButton.disabled = true; // Desabilita o recall
                fetchAndUpdateDrivers(); // Atualiza a lista
            } else {
                console.error(`Failed to clear list: ${response.status} ${response.statusText}`);
                 const errorText = await response.text();
                 showAdminFeedback(`Falha ao limpar a fila. ${response.statusText} ${errorText ? `(${errorText})` : ''}`, true);
            }
        } catch (error) {
            console.error('Error clearing list:', error);
             showAdminFeedback('Erro de rede ao tentar limpar a fila.', true);
        }
    };

    // === Anexar Event Listeners ===

    // Botão Chamar Próximo
    if (callNextButton) {
        callNextButton.addEventListener('click', handleCallNext);
    } else {
        console.warn("Button with id 'call-next-button' not found.");
    }

    // Botão Chamar Novamente (Recall)
    if (recallButton) {
        recallButton.addEventListener('click', handleRecall);
    } else {
        console.warn("Button with id 'recall-button' not found.");
    }

    // Botão Limpar Fila
    if (clearListButton) {
        clearListButton.addEventListener('click', handleClearList);
    } else {
        console.warn("Button with id 'clear-list-button' not found.");
    }

    // Delegação de Eventos para botões "Chamar" individuais na tabela
    // Isso é mais eficiente do que adicionar um listener para cada botão
    if (tableBody) {
        tableBody.addEventListener('click', (event) => {
            // Verifica se o clique foi em um elemento com a classe 'call-individual-button'
            if (event.target && event.target.classList.contains('call-individual-button')) {
                const driverId = event.target.dataset.driverId;
                if (driverId) {
                    handleCallIndividual(driverId);
                } else {
                    console.warn("Call button clicked but driverId dataset is missing.");
                }
            }
        });
    } else {
         console.error("Table body not found for event delegation.");
    }


    // === Inicialização ===
    fetchAndUpdateDrivers(); // Carrega os dados iniciais ao carregar a página

    // Configura o polling para atualizar a lista periodicamente
    setInterval(fetchAndUpdateDrivers, POLLING_INTERVAL_MS);
    console.log(`Polling setup every ${POLLING_INTERVAL_MS / 1000} seconds.`);

}); // Fim do DOMContentLoaded