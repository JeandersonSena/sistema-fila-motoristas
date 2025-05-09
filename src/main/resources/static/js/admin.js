/**
 * admin.js - Lógica para a página de administração da fila de motoristas.
 */
document.addEventListener('DOMContentLoaded', function() {
    console.log('[DEBUG] DOMContentLoaded - Script admin.js iniciado.');

    // === Elementos da UI ===
    const queueTableBody = document.getElementById('driver-queue-body');
    const callNextButton = document.getElementById('call-next-btn');
    const clearQueueButton = document.getElementById('clear-queue-btn');
    const adminFeedbackDiv = document.getElementById('admin-feedback');
    const noDriversMessageDiv = document.getElementById('no-drivers-message');
    const queueTable = document.getElementById('driver-queue-table');

    const calledDriversTableBody = document.getElementById('called-drivers-body');
    const noCalledDriversMessageDiv = document.getElementById('no-called-drivers-message');
    const calledDriversTable = document.getElementById('called-drivers-table');

    console.log('[DEBUG] Elementos UI Espera:', { queueTableBody, callNextButton, clearQueueButton, noDriversMessageDiv, queueTable });
    console.log('[DEBUG] Elementos UI Chamados:', { calledDriversTableBody, noCalledDriversMessageDiv, calledDriversTable });

    // === Estado ===
    let pollingIntervalId = null;
    const POLLING_INTERVAL_MS = 7000;

    // === Funções de Atualização da UI ===

    /** Busca e atualiza AMBAS as listas (espera e chamados). */
    async function fetchAndRefreshAllQueues() {
        console.log('[DEBUG] fetchAndRefreshAllQueues - Iniciando busca de ambas as filas.');
        try {
            // Executa em paralelo para um pouco mais de velocidade
            await Promise.all([
                fetchAndUpdateWaitingQueue(),
                fetchAndUpdateCalledDrivers()
            ]);
             console.log('[DEBUG] fetchAndRefreshAllQueues - Busca de ambas as filas concluída.');
        } catch (error) {
             console.error("[DEBUG] Erro GERAL em fetchAndRefreshAllQueues:", error);
             displayAdminFeedback("Erro ao conectar com o servidor. Verifique os logs e tente recarregar.", "error");
             disableActionButtons();
             stopPolling();
        }
    }

    /** Busca dados da fila de ESPERA e atualiza a tabela. */
    async function fetchAndUpdateWaitingQueue() {
        console.log('[DEBUG] fetchAndUpdateWaitingQueue - Buscando /admin/queue...');
        try {
            const response = await fetch('/admin/queue');
            console.log('[DEBUG] fetchAndUpdateWaitingQueue - Resposta status:', response.status);
            if (!response.ok) throw new Error(`Erro ${response.status}: ${response.statusText || 'Falha ao buscar fila de espera'}`);
            const drivers = await response.json();
            console.log('[DEBUG] fetchAndUpdateWaitingQueue - Dados recebidos:', drivers);
            updateWaitingQueueTable(drivers);
        } catch (error) {
             console.error("[DEBUG] Erro em fetchAndUpdateWaitingQueue:", error);
             displayAdminFeedback(`Erro ao buscar fila de espera: ${error.message}`, 'error');
             if(queueTableBody) queueTableBody.innerHTML = '<tr><td colspan="5" class="error-message">Falha ao carregar.</td></tr>';
             if(noDriversMessageDiv) noDriversMessageDiv.style.display = 'none';
             if(queueTable) queueTable.style.display = '';
             disableActionButtons();
        }
    }

    /** Atualiza a tabela HTML da FILA DE ESPERA. */
    function updateWaitingQueueTable(drivers) {
        console.log('[DEBUG] updateWaitingQueueTable - Atualizando tabela de espera com', drivers ? drivers.length : 0, 'motoristas.');
        if (!queueTableBody || !queueTable || !noDriversMessageDiv) {
            console.error('[DEBUG] updateWaitingQueueTable - Elementos da tabela de espera não encontrados!');
            return;
        }
        queueTableBody.innerHTML = '';
        if (drivers && drivers.length > 0) {
            console.log('[DEBUG] updateWaitingQueueTable - Fila de espera não vazia. Habilitando botões.');
            queueTable.style.display = '';
            noDriversMessageDiv.style.display = 'none';
            drivers.forEach((driver, index) => {
                const row = queueTableBody.insertRow();
                row.insertCell(0).textContent = index + 1;
                row.insertCell(1).textContent = driver.name || 'N/A';
                row.insertCell(2).textContent = driver.plate || 'N/A';
                row.insertCell(3).textContent = driver.phoneNumber || 'N/A';
                row.insertCell(4).textContent = driver.entryTime ? formatDateTime(driver.entryTime) : 'N/A';
            });
            enableActionButtons();
        } else {
             console.log('[DEBUG] updateWaitingQueueTable - Fila de espera vazia. Desabilitando botões.');
            queueTable.style.display = 'none';
            noDriversMessageDiv.style.display = 'block';
            disableActionButtons();
        }
    }

    /** Busca dados dos MOTORISTAS CHAMADOS e atualiza a tabela. */
    async function fetchAndUpdateCalledDrivers() {
         console.log('[DEBUG] fetchAndUpdateCalledDrivers - Buscando /admin/called-drivers...');
         try {
            const response = await fetch('/admin/called-drivers');
             console.log('[DEBUG] fetchAndUpdateCalledDrivers - Resposta status:', response.status);
            if (!response.ok) throw new Error(`Erro ${response.status}: ${response.statusText || 'Falha ao buscar motoristas chamados'}`);
            const drivers = await response.json();
            console.log('[DEBUG] fetchAndUpdateCalledDrivers - Dados recebidos:', drivers);
            updateCalledDriversTable(drivers);
        } catch (error) {
            console.error("[DEBUG] Erro em fetchAndUpdateCalledDrivers:", error);
            if(calledDriversTableBody) calledDriversTableBody.innerHTML = '<tr><td colspan="6" class="error-message">Falha ao carregar.</td></tr>';
            if(noCalledDriversMessageDiv) noCalledDriversMessageDiv.style.display = 'none';
            if(calledDriversTable) calledDriversTable.style.display = '';
        }
    }

    /** Atualiza a tabela HTML dos MOTORISTAS CHAMADOS. */
    function updateCalledDriversTable(drivers) {
        console.log('[DEBUG] updateCalledDriversTable - Atualizando tabela de chamados com', drivers ? drivers.length : 0, 'motoristas.');
        if (!calledDriversTableBody || !calledDriversTable || !noCalledDriversMessageDiv) {
             console.error('[DEBUG] updateCalledDriversTable - Elementos da tabela de chamados não encontrados!');
             return;
        }
        calledDriversTableBody.innerHTML = '';
        if (drivers && drivers.length > 0) {
            calledDriversTable.style.display = '';
            noCalledDriversMessageDiv.style.display = 'none';
            drivers.forEach((driver) => {
                const row = calledDriversTableBody.insertRow();
                row.insertCell(0).textContent = driver.name || 'N/A';
                row.insertCell(1).textContent = driver.plate || 'N/A';
                row.insertCell(2).textContent = driver.phoneNumber || 'N/A';
                row.insertCell(3).textContent = driver.entryTime ? formatDateTime(driver.entryTime) : 'N/A';
                row.insertCell(4).textContent = driver.calledTime ? formatDateTime(driver.calledTime) : 'N/A';
                const actionCell = row.insertCell(5);
                actionCell.appendChild(createActionButton('Compareceu', ['btn', 'btn-success', 'btn-sm', 'btn-attended'], driver.id));
                actionCell.appendChild(createActionButton('Chamar Novamente', ['btn', 'btn-info', 'btn-sm', 'btn-recall'], driver.id));
            });
        } else {
            calledDriversTable.style.display = 'none';
            noCalledDriversMessageDiv.style.display = 'block';
        }
    }

    // === Funções de Ação ===

    /** Chama o próximo motorista na fila de espera. */
    async function callNextDriverAction() {
        console.log("[DEBUG] callNextDriverAction - Função chamada.");
        disableActionButtons();
        displayAdminFeedback('Chamando próximo motorista...', 'info');
        try {
            const response = await fetch('/admin/call-next', { method: 'POST' });
            if (response.ok) {
                const calledDriver = await response.json();
                displayAdminFeedback(`Motorista ${calledDriver.name} (${calledDriver.plate}) chamado com sucesso!`, 'success');
                 await fetchAndRefreshAllQueues();
            } else if (response.status === 404) {
                const message = await response.text();
                displayAdminFeedback(message || 'Nenhum motorista para chamar.', 'info');
                 // A fila vazia será tratada por updateWaitingQueueTable que chamará disableActionButtons
            } else {
                const errorText = await response.text();
                throw new Error(`Erro ${response.status}: ${errorText || 'Falha ao chamar próximo'}`);
            }
        } catch (error) {
            console.error("Erro durante callNextDriverAction:", error);
            displayAdminFeedback(`Erro ao chamar: ${error.message}`, 'error');
             enableActionButtons(); // Reabilita em caso de erro na chamada API
        }
    }

    /** Limpa todos os motoristas da fila de espera (status WAITING). */
    async function clearQueueAction() {
        console.log("[DEBUG] clearQueueAction - Função chamada.");
        if (!confirm('ATENÇÃO!\n\nTem certeza que deseja remover TODOS os motoristas da lista de espera?\nStatus será mudado para CLEARED.')) return;
        disableActionButtons();
        displayAdminFeedback('Limpando a lista de espera...', 'info');
        try {
            const response = await fetch('/admin/clear-queue', { method: 'POST' });
            const responseText = await response.text();
            if (response.ok) {
                displayAdminFeedback(responseText || 'Lista de espera limpa com sucesso!', 'success');
                 await fetchAndRefreshAllQueues();
            } else {
                throw new Error(`Erro ${response.status}: ${responseText || 'Falha ao limpar fila'}`);
            }
        } catch (error) {
            console.error("Erro durante clearQueueAction:", error);
            displayAdminFeedback(`Erro ao limpar fila: ${error.message}`, 'error');
             enableActionButtons();
        }
    }

     /** Marca um motorista como compareceu (ATTENDED). */
    async function markAttendedAction(driverId) {
        console.log(`[DEBUG] markAttendedAction - Função chamada para ID: ${driverId}`);
        displayAdminFeedback(`Marcando motorista ${driverId} como compareceu...`, 'info');
        try {
            const response = await fetch(`/admin/driver/${driverId}/attended`, { method: 'POST' });
            const responseText = await response.text();
             if (response.ok) {
                displayAdminFeedback(responseText || `Motorista ${driverId} marcado como compareceu.`, 'success');
                 await fetchAndRefreshAllQueues();
            } else {
                 const message = response.status === 400 ? responseText : (await response.text() || `Erro ${response.status}`);
                throw new Error(message);
            }
        } catch (error) {
            console.error(`Erro ao marcar motorista ${driverId} como compareceu:`, error);
            displayAdminFeedback(`Erro: ${error.message || 'Falha ao marcar comparecimento'}`, 'error');
        }
    }

     /** Tenta chamar novamente um motorista que já foi chamado. */
    async function recallDriverAction(driverId) {
        console.log(`[DEBUG] recallDriverAction - Função chamada para ID: ${driverId}`);
        if (!confirm(`Tem certeza que deseja chamar novamente o motorista ID ${driverId}?`)) return;
        displayAdminFeedback(`Tentando chamar novamente motorista ${driverId}...`, 'info');
        try {
            const response = await fetch(`/admin/driver/${driverId}/recall`, { method: 'POST' });
            // A resposta para /recall pode ser um objeto Driver (se ainda CALLED) ou uma string (se virou NO_SHOW)
            if (response.ok) {
                try {
                    const responseData = await response.json(); // Tenta parsear como JSON (objeto Driver)
                    if (responseData && responseData.name) {
                        displayAdminFeedback(`Motorista ${responseData.name} chamado novamente (tentativa ${responseData.callAttempts}).`, 'success');
                    } else { // Se não for um objeto Driver, pode ser a string de NO_SHOW
                         displayAdminFeedback(await response.text(), 'warning'); // Mostra a string de aviso do backend
                    }
                } catch (e) { // Se não for JSON, é provavelmente a string de NO_SHOW
                    displayAdminFeedback(await response.text(), 'warning');
                }
                await fetchAndRefreshAllQueues();
            } else {
                const errorText = await response.text();
                throw new Error(`Erro ${response.status}: ${errorText || 'Falha ao chamar novamente'}`);
            }
        } catch (error) {
            console.error(`Erro ao tentar chamar novamente motorista ${driverId}:`, error);
            displayAdminFeedback(`Erro: ${error.message || 'Falha ao chamar novamente.'}`, 'error');
        }
    }

    // === Funções Auxiliares ===
    function formatDateTime(dateTimeString) {
        if (!dateTimeString) return 'N/A';
        try {
            return new Date(dateTimeString).toLocaleString('pt-BR', {
                day: '2-digit', month: '2-digit', year: 'numeric',
                hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
            });
        } catch (e) {
            console.warn("[DEBUG] Erro ao formatar data:", dateTimeString, e);
            return dateTimeString;
        }
    }
    function createActionButton(text, cssClasses = [], driverId) {
        const button = document.createElement('button');
        button.textContent = text;
        if (Array.isArray(cssClasses)) {
             button.classList.add(...cssClasses);
        } else if (typeof cssClasses === 'string') {
             button.classList.add(cssClasses);
        }
        button.dataset.driverId = driverId;
        return button;
    }
    function displayAdminFeedback(message, type = 'info') {
        if (!adminFeedbackDiv) return;
        adminFeedbackDiv.textContent = message;
        adminFeedbackDiv.className = '';
        adminFeedbackDiv.classList.add(type);
        adminFeedbackDiv.style.display = 'block';
        setTimeout(() => {
            adminFeedbackDiv.textContent = '';
            adminFeedbackDiv.style.display = 'none';
            adminFeedbackDiv.className = '';
        }, 6000);
    }
    function enableActionButtons() {
        console.log('[DEBUG] enableActionButtons - Habilitando botões principais.');
        if (callNextButton) callNextButton.disabled = false;
        if (clearQueueButton) clearQueueButton.disabled = false;
    }
    function disableActionButtons() {
         console.log('[DEBUG] disableActionButtons - Desabilitando botões principais.');
        if (callNextButton) callNextButton.disabled = true;
        if (clearQueueButton) clearQueueButton.disabled = true;
    }

    // === Delegação de Eventos para os Botões de Ação nas Tabelas ===
    if (calledDriversTableBody) {
        calledDriversTableBody.addEventListener('click', function(event) {
             console.log('[DEBUG] Click detectado na tabela de chamados.');
            const target = event.target;
            if (target.tagName === 'BUTTON' && target.dataset.driverId) {
                 console.log('[DEBUG] Click foi em um botão de ação com ID:', target.dataset.driverId, 'Classes:', target.classList);
                const driverId = target.dataset.driverId;
                if (target.classList.contains('btn-attended')) {
                    markAttendedAction(driverId);
                } else if (target.classList.contains('btn-recall')) {
                    recallDriverAction(driverId);
                }
            }
        });
    } else {
        console.error("[DEBUG] Elemento 'called-drivers-body' não encontrado para listener de clique.");
    }

    // --- Adiciona Listeners aos Botões Principais ---
    console.log('[DEBUG] Adicionando listener ao botão Chamar Próximo:', callNextButton);
     if (callNextButton) {
        callNextButton.addEventListener('click', callNextDriverAction);
         console.log('[DEBUG] Listener ADICIONADO para Chamar Próximo.');
    } else {
         console.error("[DEBUG] FALHA ao adicionar listener: Botão 'call-next-btn' não encontrado!");
    }

     console.log('[DEBUG] Adicionando listener ao botão Limpar Lista:', clearQueueButton);
    if (clearQueueButton) {
        clearQueueButton.addEventListener('click', clearQueueAction);
         console.log('[DEBUG] Listener ADICIONADO para Limpar Lista.');
    } else {
         console.error("[DEBUG] FALHA ao adicionar listener: Botão 'clear-queue-btn' não encontrado!");
    }

    // --- Inicialização e Polling ---
    function startPolling(interval = POLLING_INTERVAL_MS) {
        console.log(`[DEBUG] Iniciando polling a cada ${interval}ms para todas as filas.`);
        stopPolling();
        fetchAndRefreshAllQueues().catch(err => console.error("[DEBUG] Erro na busca inicial do polling:", err));
        pollingIntervalId = setInterval(() => {
             fetchAndRefreshAllQueues().catch(err => {
                 console.error("[DEBUG] Erro durante execução do polling:", err);
                 displayAdminFeedback("Erro periódico ao buscar dados. Verifique a conexão/servidor.", "error");
             });
        }, interval);
    }

    function stopPolling() {
        if (pollingIntervalId) {
            console.log("[DEBUG] Parando polling.");
            clearInterval(pollingIntervalId);
            pollingIntervalId = null;
        }
    }

    // --- Inicia ---
    console.log('[DEBUG] Iniciando polling...');
    startPolling();
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) stopPolling();
        else startPolling();
    });

    console.log('[DEBUG] Script admin.js carregado e inicializado.');

}); // Fim do DOMContentLoaded