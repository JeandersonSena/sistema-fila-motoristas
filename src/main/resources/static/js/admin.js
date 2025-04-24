/**
 * admin.js - Lógica para a página de administração da fila de motoristas.
 */
document.addEventListener('DOMContentLoaded', function() {

    // Elementos da UI
    const queueTableBody = document.getElementById('driver-queue-body');
    const callNextButton = document.getElementById('call-next-btn');
    const clearQueueButton = document.getElementById('clear-queue-btn');
    const adminFeedbackDiv = document.getElementById('admin-feedback');
    const noDriversMessageDiv = document.getElementById('no-drivers-message');
    const queueTable = document.getElementById('driver-queue-table'); // Referência à tabela inteira

    // Estado
    let pollingIntervalId = null;
    const POLLING_INTERVAL_MS = 7000; // Atualizar a cada 7 segundos

    // --- Funções Principais ---

    /**
     * Busca os dados da fila na API e atualiza a tabela na interface.
     */
    async function fetchAndUpdateQueue() {
        console.debug('Polling: Buscando dados da fila...'); // Usar debug para logs frequentes
        try {
            const response = await fetch('/admin/queue'); // Endpoint da API

            if (!response.ok) {
                // Tratar erro da API (ex: servidor fora do ar, erro 500)
                throw new Error(`Erro ${response.status}: ${response.statusText || 'Falha ao buscar dados da fila'}`);
            }

            const drivers = await response.json(); // Converte a resposta para um array de objetos
            updateQueueTable(drivers); // Chama a função para atualizar a UI

        } catch (error) {
            console.error("Erro durante fetchAndUpdateQueue:", error);
            displayAdminFeedback(`Erro ao atualizar fila: ${error.message}`, 'error');
            // Em caso de erro, pode ser útil desabilitar botões ou mostrar estado de erro na tabela
            queueTableBody.innerHTML = '<tr><td colspan="5" style="color: red; text-align: center;">Falha ao carregar dados. Tentando novamente...</td></tr>';
             noDriversMessageDiv.style.display = 'none';
             queueTable.style.display = ''; // Garante que a tabela (com a msg de erro) esteja visível
             disableActionButtons(); // Desabilita ações se não puder carregar
        }
    }

    /**
     * Atualiza a tabela HTML com os dados dos motoristas recebidos.
     * @param {Array<Object>} drivers - Array de objetos motorista.
     */
    function updateQueueTable(drivers) {
        queueTableBody.innerHTML = ''; // Limpa conteúdo atual

        if (drivers && drivers.length > 0) {
            queueTable.style.display = ''; // Mostra a tabela
            noDriversMessageDiv.style.display = 'none'; // Esconde mensagem de fila vazia

            drivers.forEach((driver, index) => {
                const row = queueTableBody.insertRow();
                row.insertCell(0).textContent = index + 1; // Posição na fila
                row.insertCell(1).textContent = driver.name || 'N/A';
                row.insertCell(2).textContent = driver.plate || 'N/A';
                row.insertCell(3).textContent = driver.phoneNumber || 'N/A'; // Exibe o telefone

                // Formata a data/hora de entrada
                let entryTimeFormatted = 'N/A';
                if (driver.entryTime) {
                    try {
                        entryTimeFormatted = new Date(driver.entryTime).toLocaleString('pt-BR', {
                            day: '2-digit', month: '2-digit', year: 'numeric',
                            hour: '2-digit', minute: '2-digit', second: '2-digit'
                        });
                    } catch (e) { console.warn("Erro ao formatar data:", driver.entryTime, e); }
                }
                row.insertCell(4).textContent = entryTimeFormatted;

                // Coluna de ação removida, usamos botões gerais
                // row.insertCell(5).innerHTML = `<button class="btn btn-sm btn-primary" disabled>Chamar</button>`;
            });

            // Habilita os botões de ação, pois há motoristas
            enableActionButtons();

        } else {
            // Fila vazia
            queueTable.style.display = 'none'; // Esconde a tabela
            noDriversMessageDiv.style.display = 'block'; // Mostra mensagem de fila vazia
            disableActionButtons(); // Desabilita os botões
        }
         console.debug(`Tabela atualizada com ${drivers ? drivers.length : 0} motoristas.`);
    }

    /**
     * Envia requisição para chamar o próximo motorista.
     */
    async function callNextDriverAction() {
        console.log("Botão 'Chamar Próximo' clicado.");
        disableActionButtons(); // Desabilita botões durante a ação
        displayAdminFeedback('Chamando próximo motorista...', 'info');

        try {
            const response = await fetch('/admin/call-next', { method: 'POST' });

            if (response.ok) {
                const calledDriver = await response.json();
                displayAdminFeedback(`Motorista ${calledDriver.name} (${calledDriver.plate}) chamado com sucesso!`, 'success');
                console.info("Chamada bem-sucedida:", calledDriver);
                fetchAndUpdateQueue(); // Atualiza a tabela imediatamente
            } else if (response.status === 404) {
                const message = await response.text();
                displayAdminFeedback(message || 'Nenhum motorista para chamar.', 'info');
                console.info("Tentativa de chamada, mas fila vazia.");
                // Os botões serão reavaliados pelo fetchAndUpdateQueue
            } else {
                // Outro erro do servidor
                const errorText = await response.text();
                throw new Error(`Erro ${response.status}: ${errorText || response.statusText}`);
            }
        } catch (error) {
            console.error("Erro durante callNextDriverAction:", error);
            displayAdminFeedback(`Erro ao chamar: ${error.message}`, 'error');
             enableActionButtons(); // Reabilita botões em caso de erro para nova tentativa
        }
        // Nota: Não precisamos reabilitar os botões aqui explicitamente se o fetchAndUpdateQueue for chamado,
        // pois ele avaliará o estado da fila e habilitará/desabilitará conforme necessário.
        // Mas podemos reabilitar em caso de erro para permitir nova tentativa.
    }

    /**
     * Envia requisição para limpar a lista de espera.
     */
    async function clearQueueAction() {
        console.log("Botão 'Limpar Lista' clicado.");

        // Confirmação crucial
        if (!confirm('ATENÇÃO!\n\nTem certeza que deseja remover TODOS os motoristas da lista de espera?\n\nEsta ação não pode ser desfeita.')) {
            console.log("Ação de limpar fila cancelada pelo usuário.");
            return; // Interrompe se o usuário cancelar
        }

        disableActionButtons();
        displayAdminFeedback('Limpando a lista de espera...', 'info');

        try {
            const response = await fetch('/admin/clear-queue', { method: 'POST' });
            const responseText = await response.text(); // Pega a resposta como texto

            if (response.ok) {
                displayAdminFeedback(responseText || 'Lista de espera limpa com sucesso!', 'success');
                console.warn("Lista de espera limpa via API.");
                fetchAndUpdateQueue(); // Atualiza a tabela
            } else {
                throw new Error(`Erro ${response.status}: ${responseText || response.statusText}`);
            }
        } catch (error) {
            console.error("Erro durante clearQueueAction:", error);
            displayAdminFeedback(`Erro ao limpar fila: ${error.message}`, 'error');
             enableActionButtons(); // Reabilita em caso de erro
        }
    }

    /**
     * Exibe mensagens de feedback na área designada.
     * @param {string} message - A mensagem a ser exibida.
     * @param {'info'|'success'|'error'} type - O tipo de mensagem para estilização.
     */
    function displayAdminFeedback(message, type = 'info') {
        adminFeedbackDiv.textContent = message;
        adminFeedbackDiv.className = type; // Aplica a classe CSS ('info', 'success', 'error')
        adminFeedbackDiv.style.display = 'block'; // Garante que esteja visível

        // Limpa a mensagem após um tempo
        setTimeout(() => {
            adminFeedbackDiv.textContent = '';
            adminFeedbackDiv.style.display = 'none';
             adminFeedbackDiv.className = '';
        }, 6000); // Mensagem some após 6 segundos
    }

    /** Habilita os botões de ação */
    function enableActionButtons() {
        if (callNextButton) callNextButton.disabled = false;
        if (clearQueueButton) clearQueueButton.disabled = false;
    }

    /** Desabilita os botões de ação */
    function disableActionButtons() {
        if (callNextButton) callNextButton.disabled = true;
        if (clearQueueButton) clearQueueButton.disabled = true;
    }


    // --- Inicialização e Event Listeners ---

    /** Inicia o polling para buscar dados da fila periodicamente */
    function startPolling() {
        console.log(`Iniciando polling a cada ${POLLING_INTERVAL_MS}ms`);
        stopPolling(); // Garante que não haja múltiplos intervalos rodando
        fetchAndUpdateQueue(); // Busca inicial imediata
        pollingIntervalId = setInterval(fetchAndUpdateQueue, POLLING_INTERVAL_MS);
    }

    /** Para o polling */
    function stopPolling() {
        if (pollingIntervalId) {
            console.log("Parando polling.");
            clearInterval(pollingIntervalId);
            pollingIntervalId = null;
        }
    }

    // Adiciona listeners aos botões
    if (callNextButton) {
        callNextButton.addEventListener('click', callNextDriverAction);
    } else {
         console.error("Botão 'call-next-btn' não encontrado!");
    }

    if (clearQueueButton) {
        clearQueueButton.addEventListener('click', clearQueueAction);
    } else {
         console.error("Botão 'clear-queue-btn' não encontrado!");
    }

    // Inicia o processo
    startPolling();

    // Opcional: Pausar polling quando a aba está inativa (economiza recursos)
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            stopPolling();
        } else {
            startPolling();
        }
    });

});