/**
 * admin.js - Lógica para a página de administração da fila de motoristas.
 */
document.addEventListener('DOMContentLoaded', function() {

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

    // === Estado ===
    let pollingIntervalId = null;
    const POLLING_INTERVAL_MS = 7000; // Atualizar a cada 7 segundos

    // === Funções de Atualização da UI ===

    /** Busca e atualiza AMBAS as listas (espera e chamados). */
    async function fetchAndRefreshAllQueues() {
        console.debug('Polling: Buscando dados de TODAS as filas...');
        // Executa em paralelo para um pouco mais de velocidade
        await Promise.all([
            fetchAndUpdateWaitingQueue(),
            fetchAndUpdateCalledDrivers()
        ]);
    }

    /** Busca dados da fila de ESPERA e atualiza a tabela. */
    async function fetchAndUpdateWaitingQueue() {
        try {
            const response = await fetch('/admin/queue');
            if (!response.ok) throw new Error(`Erro ${response.status}: ${response.statusText || 'Falha ao buscar fila de espera'}`);
            const drivers = await response.json();
            updateWaitingQueueTable(drivers);
        } catch (error) {
            console.error("Erro durante fetchAndUpdateWaitingQueue:", error);
            displayAdminFeedback(`Erro ao atualizar fila de espera: ${error.message}`, 'error');
            if(queueTableBody) queueTableBody.innerHTML = '<tr><td colspan="5" class="error-message">Falha ao carregar.</td></tr>';
            if(noDriversMessageDiv) noDriversMessageDiv.style.display = 'none';
            if(queueTable) queueTable.style.display = '';
            disableActionButtons();
        }
    }

    /** Atualiza a tabela HTML da FILA DE ESPERA. */
    function updateWaitingQueueTable(drivers) {
        if (!queueTableBody || !queueTable || !noDriversMessageDiv) return;
        queueTableBody.innerHTML = '';
        if (drivers && drivers.length > 0) {
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
            queueTable.style.display = 'none';
            noDriversMessageDiv.style.display = 'block';
            disableActionButtons();
        }
    }

    /** Busca dados dos MOTORISTAS CHAMADOS e atualiza a tabela. */
    async function fetchAndUpdateCalledDrivers() {
        try {
            const response = await fetch('/admin/called-drivers');
            if (!response.ok) throw new Error(`Erro ${response.status}: ${response.statusText || 'Falha ao buscar motoristas chamados'}`);
            const drivers = await response.json();
            updateCalledDriversTable(drivers);
        } catch (error) {
            console.error("Erro durante fetchAndUpdateCalledDrivers:", error);
            if(calledDriversTableBody) calledDriversTableBody.innerHTML = '<tr><td colspan="6" class="error-message">Falha ao carregar.</td></tr>'; // Colspan 6
            if(noCalledDriversMessageDiv) noCalledDriversMessageDiv.style.display = 'none';
            if(calledDriversTable) calledDriversTable.style.display = '';
        }
    }

    /** Atualiza a tabela HTML dos MOTORISTAS CHAMADOS. */
    function updateCalledDriversTable(drivers) {
        if (!calledDriversTableBody || !calledDriversTable || !noCalledDriversMessageDiv) return;
        calledDriversTableBody.innerHTML = ''; // Limpa tabela
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

                // --- Adiciona Botões de Ação ---
                const actionCell = row.insertCell(5);
                actionCell.appendChild(createActionButton('Compareceu', 'btn-attended', driver.id));
                actionCell.appendChild(createActionButton('Não Compareceu', 'btn-no-show', driver.id));
            });
        } else {
            calledDriversTable.style.display = 'none';
            noCalledDriversMessageDiv.style.display = 'block';
        }
    }

    // === Funções de Ação ===

    /** Chama o próximo motorista na fila de espera. */
    async function callNextDriverAction() {
        console.log("Botão 'Chamar Próximo' clicado.");
        disableActionButtons();
        displayAdminFeedback('Chamando próximo motorista...', 'info');
        try {
            const response = await fetch('/admin/call-next', { method: 'POST' });
            if (response.ok) {
                const calledDriver = await response.json();
                displayAdminFeedback(`Motorista ${calledDriver.name} (${calledDriver.plate}) chamado com sucesso!`, 'success');
                fetchAndRefreshAllQueues(); // Atualiza ambas as filas
            } else if (response.status === 404) {
                const message = await response.text();
                displayAdminFeedback(message || 'Nenhum motorista para chamar.', 'info');
                // Mantém botões desabilitados pois a fila de espera está vazia
            } else {
                const errorText = await response.text();
                throw new Error(`Erro ${response.status}: ${errorText || response.statusText}`);
            }
        } catch (error) {
            console.error("Erro durante callNextDriverAction:", error);
            displayAdminFeedback(`Erro ao chamar: ${error.message}`, 'error');
            enableActionButtons(); // Reabilita em caso de erro
        }
    }

    /** Limpa todos os motoristas da fila de espera (status WAITING). */
    async function clearQueueAction() {
        if (!confirm('ATENÇÃO!\n\nTem certeza que deseja remover TODOS os motoristas da lista de espera?\nStatus será mudado para CLEARED.')) return;
        disableActionButtons();
        displayAdminFeedback('Limpando a lista de espera...', 'info');
        try {
            const response = await fetch('/admin/clear-queue', { method: 'POST' });
            const responseText = await response.text();
            if (response.ok) {
                displayAdminFeedback(responseText || 'Lista de espera limpa com sucesso!', 'success');
                fetchAndRefreshAllQueues(); // Atualiza ambas as filas
            } else {
                throw new Error(`Erro ${response.status}: ${responseText || response.statusText}`);
            }
        } catch (error) {
            console.error("Erro durante clearQueueAction:", error);
            displayAdminFeedback(`Erro ao limpar fila: ${error.message}`, 'error');
            enableActionButtons();
        }
    }

    /** Marca um motorista como compareceu (ATTENDED). */
    async function markAttendedAction(driverId) {
        console.log(`Botão 'Compareceu' clicado para ID: ${driverId}`);
        displayAdminFeedback(`Marcando motorista ${driverId} como compareceu...`, 'info');
         // Desabilitar botões temporariamente pode ser adicionado aqui se necessário
        try {
            const response = await fetch(`/admin/driver/${driverId}/attended`, { method: 'POST' });
            const responseText = await response.text();
             if (response.ok) {
                displayAdminFeedback(responseText || `Motorista ${driverId} marcado como compareceu.`, 'success');
                fetchAndRefreshAllQueues(); // Atualiza ambas as filas
            } else {
                throw new Error(`Erro ${response.status}: ${responseText || response.statusText}`);
            }
        } catch (error) {
            console.error(`Erro ao marcar motorista ${driverId} como compareceu:`, error);
            displayAdminFeedback(`Erro: ${error.message}`, 'error');
        }
    }

     /** Marca um motorista como não compareceu (NO_SHOW). */
    async function markNoShowAction(driverId) {
        console.log(`Botão 'Não Compareceu' clicado para ID: ${driverId}`);
        if (!confirm(`Tem certeza que deseja marcar o motorista ID ${driverId} como NÃO COMPARECEU?`)) return;
        displayAdminFeedback(`Marcando motorista ${driverId} como não compareceu...`, 'info');
        try {
            const response = await fetch(`/admin/driver/${driverId}/no-show`, { method: 'POST' });
            const responseText = await response.text();
             if (response.ok) {
                displayAdminFeedback(responseText || `Motorista ${driverId} marcado como não compareceu.`, 'success');
                fetchAndRefreshAllQueues(); // Atualiza ambas as filas
            } else {
                throw new Error(`Erro ${response.status}: ${responseText || response.statusText}`);
            }
        } catch (error) {
            console.error(`Erro ao marcar motorista ${driverId} como não compareceu:`, error);
            displayAdminFeedback(`Erro: ${error.message}`, 'error');
        }
    }


    // === Funções Auxiliares ===

    /** Formata data/hora (ISO string) para o formato local pt-BR. */
    function formatDateTime(dateTimeString) {
        if (!dateTimeString) return 'N/A';
        try {
            return new Date(dateTimeString).toLocaleString('pt-BR', {
                day: '2-digit', month: '2-digit', year: 'numeric',
                hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
            });
        } catch (e) {
            console.warn("Erro ao formatar data:", dateTimeString, e);
            return dateTimeString; // Retorna string original em caso de erro
        }
    }

     /** Cria um botão de ação para as tabelas. */
    function createActionButton(text, cssClass, driverId) {
        const button = document.createElement('button');
        button.textContent = text;
        // Adiciona classes CSS básicas e a classe específica da ação
        button.classList.add('btn', 'btn-sm', cssClass); // Assume que você tem btn e btn-sm no seu CSS
        button.dataset.driverId = driverId; // Armazena o ID no botão usando data attribute
        return button;
    }

    /** Exibe mensagens de feedback na área designada. */
    function displayAdminFeedback(message, type = 'info') {
        if (!adminFeedbackDiv) return;
        adminFeedbackDiv.textContent = message;
        adminFeedbackDiv.className = ''; // Limpa classes antigas
        adminFeedbackDiv.classList.add(type); // Adiciona a classe de tipo (success, error, info)
        adminFeedbackDiv.style.display = 'block';
        setTimeout(() => {
            adminFeedbackDiv.textContent = '';
            adminFeedbackDiv.style.display = 'none';
            adminFeedbackDiv.className = '';
        }, 6000);
    }

    /** Habilita os botões de ação principais. */
    function enableActionButtons() {
        if (callNextButton) callNextButton.disabled = false;
        if (clearQueueButton) clearQueueButton.disabled = false;
    }

    /** Desabilita os botões de ação principais. */
    function disableActionButtons() {
        if (callNextButton) callNextButton.disabled = true;
        if (clearQueueButton) clearQueueButton.disabled = true;
    }

    // === Delegação de Eventos para os Botões de Ação nas Tabelas ===
    // Em vez de adicionar um listener para cada botão, adicionamos um no corpo da tabela
    // e verificamos qual botão foi clicado. É mais eficiente.

    if (calledDriversTableBody) {
        calledDriversTableBody.addEventListener('click', function(event) {
            const target = event.target; // O elemento que foi clicado
            if (target.tagName === 'BUTTON' && target.dataset.driverId) {
                const driverId = target.dataset.driverId;
                if (target.classList.contains('btn-attended')) {
                    markAttendedAction(driverId);
                } else if (target.classList.contains('btn-no-show')) {
                    markNoShowAction(driverId);
                }
            }
        });
    }


    // --- Inicialização e Polling ---
    function startPolling(interval = POLLING_INTERVAL_MS) {
        console.log(`Iniciando polling a cada ${interval}ms para todas as filas.`);
        stopPolling();
        fetchAndRefreshAllQueues(); // Busca inicial
        pollingIntervalId = setInterval(fetchAndRefreshAllQueues, interval);
    }

    function stopPolling() {
        if (pollingIntervalId) {
            console.log("Parando polling.");
            clearInterval(pollingIntervalId);
            pollingIntervalId = null;
        }
    }

    startPolling(); // Inicia o polling
    document.addEventListener('visibilitychange', () => { // Pausa/retoma polling com visibilidade da aba
        if (document.hidden) stopPolling();
        else startPolling();
    });

}); // Fim do DOMContentLoaded